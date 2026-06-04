package ai.solace.zlib.zip

import ai.solace.zlib.inflate.InflateStream
import kotlinx.io.Buffer
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.buffered
import kotlinx.io.readByteArray
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path

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
        val raw = fs.source(path)
        val src = raw.buffered()
        return src.useResource { list(it) }
    }

    fun list(src: Source): List<Entry> {
        val bytes = src.readByteArray()
        val eocdOffset = findEocd(bytes) ?: return emptyList()
        val eocd = parseEocd(bytes, eocdOffset)
        val entries = mutableListOf<Entry>()
        var offset = eocd.cdOffset.toInt()
        val cdEnd = offset + eocd.cdSize.toInt()
        while (offset + 46 <= bytes.size && offset < cdEnd) {
            val sig = readIntLe(bytes, offset)
            if (sig != SIG_CEN) break
            val gpbf = readShortLe(bytes, offset + 8)
            val method = readShortLe(bytes, offset + 10)
            val compSize = readUIntLe(bytes, offset + 20)
            val uncompSize = readUIntLe(bytes, offset + 24)
            val nameLen = readShortLe(bytes, offset + 28)
            val extraLen = readShortLe(bytes, offset + 30)
            val commentLen = readShortLe(bytes, offset + 32)
            val lho = readUIntLe(bytes, offset + 42)
            val nameStart = offset + 46
            val nameEnd = nameStart + nameLen
            if (nameEnd > bytes.size) break
            val name = bytes.copyOfRange(nameStart, nameEnd).decodeToString()
            val recordEnd = nameEnd + extraLen + commentLen
            if (recordEnd > bytes.size) break
            val entry = Entry(
                name = name,
                compression = method,
                compressedSize = compSize,
                uncompressedSize = uncompSize,
                localHeaderOffset = lho,
                generalPurposeFlag = gpbf,
            )
            entries.add(entry)
            offset = recordEnd
        }
        return entries
    }

    fun extract(
        fs: FileSystem,
        path: Path,
        entry: Entry,
        sink: Sink,
    ): Long {
        val raw = fs.source(path)
        val src = raw.buffered()
        return src.useResource { extract(it, entry, sink) }
    }

    fun extract(
        src: Source,
        entry: Entry,
        sink: Sink,
    ): Long {
        val bytes = src.readByteArray()
        return extractFromBytes(bytes, entry, sink)
    }

    private fun extractFromBytes(bytes: ByteArray, entry: Entry, sink: Sink): Long {
        val headerOffset = entry.localHeaderOffset.toInt()
        require(headerOffset + 30 <= bytes.size) { "Local header outside ZIP bounds" }
        val sig = readIntLe(bytes, headerOffset)
        require(sig == SIG_LOC) { "Bad local header sig" }
        val method = readShortLe(bytes, headerOffset + 8)
        val compSize = readUIntLe(bytes, headerOffset + 18)
        val nameLen = readShortLe(bytes, headerOffset + 26)
        val extraLen = readShortLe(bytes, headerOffset + 28)
        val dataStart = headerOffset + 30 + nameLen + extraLen
        require(compSize <= Int.MAX_VALUE) { "Compressed entry too large for in-memory extraction" }
        val compSizeInt = compSize.toInt()
        require(dataStart <= bytes.size) { "Local header truncated" }
        val dataEnd = dataStart + compSizeInt
        require(dataEnd <= bytes.size) { "Compressed data outside ZIP bounds" }

        return when (method) {
            0 -> { // STORE
                sink.write(bytes, dataStart, dataEnd)
                compSize
            }
            8 -> { // DEFLATE raw
                val limited = Buffer()
                limited.write(bytes, dataStart, dataEnd)
                val (_, out) = InflateStream.inflateRaw(limited, sink)
                out
            }
            else -> error("Unsupported compression method: $method")
        }
    }

    private fun findEocd(bytes: ByteArray): Int? {
        val minIndex = (bytes.size - 22).coerceAtLeast(0)
        val maxComment = 0xFFFF
        val lowerBound = (bytes.size - 22 - maxComment).coerceAtLeast(0)
        for (i in minIndex downTo lowerBound) {
            if (readIntLe(bytes, i) == SIG_EOCD) return i
        }
        return null
    }

    private fun parseEocd(bytes: ByteArray, offset: Int): Eocd {
        val cdSize = readUIntLe(bytes, offset + 12)
        val cdOffset = readUIntLe(bytes, offset + 16)
        val totalEntries = readShortLe(bytes, offset + 10)
        return Eocd(cdOffset, cdSize, totalEntries)
    }
}

private inline fun <T : AutoCloseable, R> T.useResource(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this.close()
    }
}

private fun readShortLe(bytes: ByteArray, offset: Int): Int {
    return ((bytes[offset].toInt() and 0xFF) or ((bytes[offset + 1].toInt() and 0xFF) shl 8))
}

private fun readIntLe(bytes: ByteArray, offset: Int): Int {
    return (bytes[offset].toInt() and 0xFF) or
        ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
        ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
        ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}

private fun readUIntLe(bytes: ByteArray, offset: Int): Long {
    return readIntLe(bytes, offset).toLong() and 0xFFFFFFFFL
}
