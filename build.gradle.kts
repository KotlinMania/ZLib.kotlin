@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform") version "2.2.10"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

kotlin {
    // Standardize on Java 17 toolchain (used by Gradle tooling even without JVM target)
    jvmToolchain(17)

    // Modern Kotlin compiler options (K2)
    compilerOptions {
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2)
        progressiveMode.set(true)
        allWarningsAsErrors.set(true)
    }

    // No JVM target: project is Native/JS/WASM focused; do not add JVM here

    // JavaScript (Node.js) target
    js(IR) {
        nodejs {
            testTask {
                useMocha {
                    timeout = "10s"
                }
            }
        }
        binaries.executable()
    }

    // iOS targets
    iosArm64()
    iosX64()
    iosSimulatorArm64()

    // Additional Native targets
    linuxArm64()
    mingwX64()

    macosArm64 {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
    }
    linuxX64 {
        binaries {
            executable {
                entryPoint = "ai.solace.zlib.cli.main"
                baseName = "zlib-cli"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio:3.10.2")
                implementation("co.touchlab:kermit:2.0.8")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("com.squareup.okio:okio:3.10.2")
            }
        }

        // JS shared source set
        val jsMain by getting {
            dependsOn(commonMain)
        }
        val jsTest by getting {
            dependsOn(commonTest)
        }

        // Native shared source set (for expect/actual logger)
        val nativeMain by creating {
            dependsOn(commonMain)
        }
        val nativeTest by creating {
            dependsOn(commonTest)
        }

        // Apple shared source set
        val appleMain by creating {
            dependsOn(nativeMain)
        }
        val appleTest by creating {
            dependsOn(nativeTest)
        }

        // Linux shared source set
        val linuxMain by creating {
            dependsOn(nativeMain)
        }
        val linuxTest by creating {
            dependsOn(nativeTest)
        }

        // macOS targets
        val macosArm64Main by getting { dependsOn(appleMain) }
        val macosArm64Test by getting { dependsOn(appleTest) }

        // iOS targets
        val iosArm64Main by getting { dependsOn(appleMain) }
        val iosArm64Test by getting { dependsOn(appleTest) }
        val iosX64Main by getting { dependsOn(appleMain) }
        val iosX64Test by getting { dependsOn(appleTest) }
        val iosSimulatorArm64Main by getting { dependsOn(appleMain) }
        val iosSimulatorArm64Test by getting { dependsOn(appleTest) }

        // Linux targets
        val linuxX64Main by getting { dependsOn(linuxMain) }
        val linuxX64Test by getting { dependsOn(linuxTest) }
        val linuxArm64Main by getting { dependsOn(linuxMain) }
        val linuxArm64Test by getting { dependsOn(linuxTest) }

        // Windows targets
        val mingwX64Main by getting { dependsOn(nativeMain) }
        val mingwX64Test by getting { dependsOn(nativeTest) }
    }
}

dependencies {
    // Add any dependencies your project needs here.
}

// Ktlint configuration
ktlint {
    android.set(false)
    outputToConsole.set(true)
    ignoreFailures.set(false)
}

// Detekt configuration
detekt {
    toolVersion = "1.23.8"
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("detekt.yml"))
    source.setFrom(
        files(
            // Main
            "src/commonMain/kotlin",
            "src/nativeMain/kotlin",
            // Tests
            "src/commonTest/kotlin",
        ),
    )
    ignoreFailures = false // Fail the build on violations
}

tasks.named("check").configure {
    dependsOn("ktlintCheck", "detekt")
}

// Disable running tests during normal builds by default.
// To enable tests, build with: ./gradlew build -PwithTests=true
val withTests: Boolean =
    providers
        .gradleProperty("withTests")
        .map { it.equals("true", ignoreCase = true) }
        .getOrElse(false)

if (!withTests) {
    tasks.withType<Test>().configureEach {
        enabled = false
    }
}

// Root `test` task: Native only (host-appropriate)
tasks.register("test") {
    group = "verification"
    description =
        if (withTests) {
            "Runs native tests on the current host (macosArm64 or linuxX64)"
        } else {
            "Displays how to enable running native tests"
        }

    if (withTests) {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("mac") -> dependsOn("macosArm64Test")
            osName.contains("nux") || osName.contains("linux") -> dependsOn("linuxX64Test")
            else -> {
                doFirst {
                    println("[ZLib.kotlin] No native test task configured for host OS: $osName")
                }
            }
        }
    } else {
        doFirst {
            println("\n[ZLib.kotlin] Native tests are disabled by default.")
            println("To run macOS tests: ./gradlew -PwithTests=true macosArm64Test")
            println("To run Linux tests: ./gradlew -PwithTests=true linuxX64Test")
            println("To run all iOS tests: ./gradlew -PwithTests=true iosX64Test iosSimulatorArm64Test")
            println("To run Windows tests: ./gradlew -PwithTests=true mingwX64Test")
            println("To run JavaScript tests: ./gradlew -PwithTests=true jsTest")
            println("To run all available tests: ./gradlew -PwithTests=true allTests")
            println("Or use host-agnostic: ./gradlew -PwithTests=true test\n")
        }
    }
}
