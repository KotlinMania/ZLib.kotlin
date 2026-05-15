package ai.solace.zlib.zip

import ai.solace.zlib.inflate.InflateStream
import okio.Buffer
import okio.BufferedSink
import okio.FileSystem
import okio.Path
import okio.Source
import okio.buffer

/** Minimal ZIP reader supporting STORE (0) and DEFLATE (8, raw). */
object ZipReader {
    private const val SIG_EOCD = 0x06054b50
    private const val SIG_CEN = 0x02014b50
    private const val SIG_LOC = 0x04034b50

    data class Entry(
        val name: String,
        val compression: Int,
        val compressedSize: Long,
        val uncompressedSize: Long,
        val localHeaderOffset: Long,
        val generalPurposeFlag: Int,
    )

    private data class Eocd(
        val cdOffset: Long,
        val cdSize: Long,
        val totalEntries: Int,
    )

    fun list(fs: FileSystem, path: Path): List<Entry> {
        // Read whole file into memory to scan from the end
        val fileBuf = Buffer()
        fs.source(path).use { s ->
            val bs = s.buffer()
            fileBuf.writeAll(bs)
        }
        val size = fileBuf.size
        val eocd = findEocd(fileBuf.clone(), size) ?: return emptyList()
        // Central directory parsing
        val cdBuf = fileBuf.clone()
        cdBuf.skip(eocd.cdOffset)
        val entries = mutableListOf<Entry>()
        var read = 0L
        while (read < eocd.cdSize) {
            val sig = cdBuf.readIntLe()
            if (sig != SIG_CEN) break
            cdBuf.skip(2) // ver made
            cdBuf.skip(2) // ver req
            val gpbf = cdBuf.readShortLe().toInt() and 0xFFFF
            val method = cdBuf.readShortLe().toInt() and 0xFFFF
            cdBuf.skip(4) // time/date
            cdBuf.skip(4) // crc32
            val compSize = cdBuf.readIntLe().toLong() and 0xFFFFFFFFL
            val uncompSize = cdBuf.readIntLe().toLong() and 0xFFFFFFFFL
            val nameLen = cdBuf.readShortLe().toInt() and 0xFFFF
            val extraLen = cdBuf.readShortLe().toInt() and 0xFFFF
            val commentLen = cdBuf.readShortLe().toInt() and 0xFFFF
            cdBuf.skip(2) // disk start
            cdBuf.skip(2) // int attr
            cdBuf.skip(4) // ext attr
            val lho = cdBuf.readIntLe().toLong() and 0xFFFFFFFFL
            val name = cdBuf.readUtf8(nameLen.toLong())
            if (extraLen > 0) cdBuf.skip(extraLen.toLong())
            if (commentLen > 0) cdBuf.skip(commentLen.toLong())
            entries.add(Entry(name, method, compSize, uncompSize, lho, gpbf))
            read += 46 + nameLen + extraLen + commentLen
        }
        return entries
    }

    fun extract(fs: FileSystem, path: Path, entry: Entry, out: BufferedSink): Long {
        fs.source(path).use { s ->
            val src = s.buffer()
            // Seek to local header offset by skipping forward
            var remaining = entry.localHeaderOffset
            while (remaining > 0) {
                val skipped = src.skip(remaining)
                if (skipped <= 0) break
                remaining -= skipped
            }
            val sig = src.readIntLe()
            require(sig == SIG_LOC) { "Bad local header" }
            src.skip(2) // ver
            src.skip(2) // gpbf
            val method = src.readShortLe().toInt() and 0xFFFF
            src.skip(4) // time/date
            src.skip(4) // crc
            val compSize = src.readIntLe().toLong() and 0xFFFFFFFFL
            src.skip(4) // uncompSize
            val nameLen = src.readShortLe().toInt() and 0xFFFF
            val extraLen = src.readShortLe().toInt() and 0xFFFF
            src.skip(nameLen.toLong())
            if (extraLen > 0) src.skip(extraLen.toLong())
            return when (method) {
                0 -> { // STORE
                    var left = compSize
                    val buf = Buffer()
                    var written = 0L
                    while (left > 0) {
                        val n = minOf(left, 8192L)
                        src.readFully(buf, n)
                        out.write(buf, n)
                        written += n
                        left -= n
                    }
                    written
                }
                8 -> { // DEFLATE raw
                    val payload = Buffer()
                    src.readFully(payload, compSize)
                    val (rc, outBytes) = InflateStream.inflateRaw(payload, out)
                    outBytes
                }
                else -> error("Unsupported compression: $method")
            }
        }
    }

    private fun findEocd(src: Buffer, size: Long): Eocd? {
        val maxComment = 0xFFFF
        val search = minOf(size, (22 + maxComment).toLong())
        src.skip(size - search)
        val window = src.readByteArray(search)
        for (i in window.size - 22 downTo 0) {
            if (window[i].toInt() and 0xFF == 0x50 &&
                window.getOrNull(i + 1)?.toInt()?.and(0xFF) == 0x4B &&
                window.getOrNull(i + 2)?.toInt()?.and(0xFF) == 0x05 &&
                window.getOrNull(i + 3)?.toInt()?.and(0xFF) == 0x06
            ) {
                val buf = Buffer().write(window, i, window.size - i)
                buf.skip(4) // sig
                buf.skip(2) // disk
                buf.skip(2) // cd start disk
                buf.skip(2) // entries this disk
                buf.skip(2) // total entries
                val cdSize = buf.readIntLe().toLong() and 0xFFFFFFFFL
                val cdOffset = buf.readIntLe().toLong() and 0xFFFFFFFFL
                val commentLen = buf.readShortLe().toInt() and 0xFFFF
                return Eocd(cdOffset, cdSize, 0)
            }
        }
        return null
    }
}

