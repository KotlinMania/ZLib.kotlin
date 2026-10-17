package io.github.kotlinmania.zlib.inflate

/**
 * Signals invalid or corrupt DEFLATE/zlib data encountered during decoding.
 */
class DataFormatException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
