@file:Suppress("ktlint:standard:property-naming")

package io.github.kotlinmania.zlib.common

actual fun getEnv(name: String): String? = System.getenv(name)
