@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

actual var LOG_FILE_PATH: String? = null

actual fun logToFile(line: String) {
    // In JS environment, we'll just log to console
    // TODO: Could be enhanced to write to a virtual file system if needed
    console.log(line.trimEnd())
}

actual fun getEnv(name: String): String? =
    try {
        // In Node.js environment, we can access process.env
        js("typeof process !== 'undefined' && process.env[name]") as? String
    } catch (_: Throwable) {
        null
    }

actual fun currentTimestamp(): String {
    // Use JavaScript Date for timestamp - need to be careful with dynamic types
    val date = js("new Date()")
    val year = js("date.getFullYear()") as Int
    val month = (js("date.getMonth() + 1") as Int).toString().padStart(2, '0')
    val day = (js("date.getDate()") as Int).toString().padStart(2, '0')
    val hour = (js("date.getHours()") as Int).toString().padStart(2, '0')
    val minute = (js("date.getMinutes()") as Int).toString().padStart(2, '0')
    val second = (js("date.getSeconds()") as Int).toString().padStart(2, '0')
    return "$year-$month-$day $hour:$minute:$second"
}
