@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

actual var LOG_FILE_PATH: String? = null

actual fun logToFile(line: String) {
    // In JS/WASM environment, we'll just log to console
    // TODO: Could be enhanced to write to a virtual file system if needed
    console.log(line.trimEnd())
}

actual fun getEnv(name: String): String? {
    return try {
        // In Node.js environment, we can access process.env
        js("typeof process !== 'undefined' && process.env[name]") as? String
    } catch (_: Throwable) {
        null
    }
}

actual fun currentTimestamp(): String {
    val now = Clock.System.now()
    val localDateTime = now.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${localDateTime.year}-" +
        "${localDateTime.monthNumber.toString().padStart(2, '0')}-" +
        "${localDateTime.dayOfMonth.toString().padStart(2, '0')} " +
        "${localDateTime.hour.toString().padStart(2, '0')}:" +
        "${localDateTime.minute.toString().padStart(2, '0')}:" +
        "${localDateTime.second.toString().padStart(2, '0')}"
}