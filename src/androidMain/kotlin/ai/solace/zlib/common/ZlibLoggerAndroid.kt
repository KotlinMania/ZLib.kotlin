@file:Suppress("ktlint:standard:property-naming")

package ai.solace.zlib.common

actual fun getEnv(name: String): String? = System.getenv(name)
