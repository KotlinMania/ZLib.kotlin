package ai.solace.zlib.cli

import ai.solace.zlib.common.Z_STREAM_END
import ai.solace.zlib.common.ZlibLogger
import ai.solace.zlib.deflate.DeflateStream
import ai.solace.zlib.inflate.InflateStream
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem

private fun printHelp() {
    println("ZLib.kotlin - Pure arithmetic zlib implementation")
    println("Commands:")
    println("  compress|deflate <input.txt> <output.zz> [level]  - Compress a file (zlib). Level: 1..9 (default 6)")
    println("  decompress|inflate <input.zz> <output.txt>        - Decompress a zlib file")
    println("  log-on                                            - Enable logging (DEBUG off by default)")
    println("  log-off                                           - Disable logging")
    println("  help                                              - Show this help")
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        printHelp()
        return
    }

    when (args[0]) {
        "help", "--help", "-h" -> {
            printHelp()
            return
        }

        "compress", "deflate" -> {
            if (args.size < 3) {
                println("Usage: ${args[0]} <input.txt> <output.zz> [level]")
                return
            }
            val fileSystem: FileSystem = SystemFileSystem
            val inPath = Path(args[1])
            val outPath = Path(args[2])
            val level = args.getOrNull(3)?.toIntOrNull() ?: 6
            fileSystem.source(inPath).buffered().useResource { src ->
                fileSystem.sink(outPath).buffered().useResource { snk ->
                    val bytesIn = DeflateStream.compressZlib(src, snk, level)
                    val outSize = fileSystem.metadataOrNull(outPath)?.size ?: -1L
                    println("Compressed $bytesIn bytes to $outSize bytes (level=$level)")
                }
            }
        }

        "decompress", "inflate" -> {
            if (args.size < 3) {
                println("Usage: ${args[0]} <input.zz> <output.txt>")
                return
            }
            val fileSystem: FileSystem = SystemFileSystem
            val inPath = Path(args[1])
            val outPath = Path(args[2])
            fileSystem.source(inPath).buffered().useResource { src ->
                fileSystem.sink(outPath).buffered().useResource { snk ->
                    val (result, bytesOut) = InflateStream.inflateZlib(src, snk)
                    snk.flush()
                    if (result == Z_STREAM_END) {
                        val inSize = fileSystem.metadataOrNull(inPath)?.size ?: -1L
                        println("Decompressed $inSize bytes to $bytesOut bytes")
                    } else {
                        println("Decompression failed: $result")
                    }
                }
            }
        }

        // Toggle logging flags for diagnostics
        "log-on" -> {
            ZlibLogger.setEnabled(true)
            ZlibLogger.setDebug(false)
            ZlibLogger.setBitwiseVerbose(false)
            println("Logging enabled (DEBUG=off, BITWISE=off). Use environment vars ZLIB_LOG_DEBUG=1 and ZLIB_LOG_BITWISE=1 for more detail.")
        }
        "log-off" -> {
            ZlibLogger.setEnabled(false)
            println("Logging disabled")
        }

        else -> {
            println("Unknown command: ${args[0]}")
            printHelp()
        }
    }
}

private inline fun <T : AutoCloseable, R> T.useResource(block: (T) -> R): R {
    try {
        return block(this)
    } finally {
        this.close()
    }
}
