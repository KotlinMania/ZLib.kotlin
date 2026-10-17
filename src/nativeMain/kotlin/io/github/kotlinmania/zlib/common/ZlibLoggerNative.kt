@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.github.kotlinmania.zlib.common

import kotlinx.cinterop.toKString
import platform.posix.getenv

actual fun getEnv(name: String): String? {
    val v = getenv(name)
    return v?.toKString()
}
