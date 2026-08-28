package ai.solace.zlib.cli

import ai.solace.zlib.common.Z_STREAM_END
import ai.solace.zlib.common.ZlibLogger
import ai.solace.zlib.deflate.DeflateStream
import ai.solace.zlib.inflate.InflateStream
import io.github.kotlinmania.io.buffered
import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem

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
            val inPath = Path(args[1])
            val outPath = Path(args[2])
            val level = args.getOrNull(3)?.toIntOrNull() ?: 6
            val src = SystemFileSystem.source(inPath).buffered()
            val snk = SystemFileSystem.sink(outPath).buffered()
            try {
                val bytesIn = DeflateStream.compressZlib(src, snk, level)
                val outSize = SystemFileSystem.metadataOrNull(outPath)?.size ?: -1L
                println("Compressed $bytesIn bytes to $outSize bytes (level=$level)")
            } finally {
                try {
                    src.close()
                } catch (_: Throwable) {
                }
                try {
                    snk.close()
                } catch (_: Throwable) {
                }
            }
        }

        "decompress", "inflate" -> {
            if (args.size < 3) {
                println("Usage: ${args[0]} <input.zz> <output.txt>")
                return
            }
            val inPath = Path(args[1])
            val outPath = Path(args[2])
            val src = SystemFileSystem.source(inPath).buffered()
            val snk = SystemFileSystem.sink(outPath).buffered()
            try {
                val (result, bytesOut) = InflateStream.inflateZlib(src, snk)
                snk.flush()
                if (result == Z_STREAM_END) {
                    val inSize = SystemFileSystem.metadataOrNull(inPath)?.size ?: -1L
                    println("Decompressed $inSize bytes to $bytesOut bytes")
                } else {
                    println("Decompression failed: $result")
                }
            } finally {
                try {
                    src.close()
                } catch (_: Throwable) {
                }
                try {
                    snk.close()
                } catch (_: Throwable) {
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
