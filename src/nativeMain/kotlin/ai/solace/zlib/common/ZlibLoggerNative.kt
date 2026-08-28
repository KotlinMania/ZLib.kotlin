@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

import kotlinx.cinterop.toKString
import platform.posix.getenv

actual fun getEnv(name: String): String? {
    val v = getenv(name)
    return v?.toKString()
}
