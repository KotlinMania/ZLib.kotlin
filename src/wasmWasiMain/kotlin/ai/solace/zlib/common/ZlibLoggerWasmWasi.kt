@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

actual var LOG_FILE_PATH: String? = null

actual fun logToFile(line: String) {
    println(line.trimEnd())
}

actual fun getEnv(name: String): String? = null

actual fun currentTimestamp(): String = "timestamp"
