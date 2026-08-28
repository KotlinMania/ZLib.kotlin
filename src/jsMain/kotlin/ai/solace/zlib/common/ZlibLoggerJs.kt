@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

actual fun getEnv(name: String): String? =
    try {
        if (js("typeof process !== 'undefined'")) {
            val env = js("process.env").asDynamic()
            env[name] as? String
        } else {
            null
        }
    } catch (_: Throwable) {
        null
    }
