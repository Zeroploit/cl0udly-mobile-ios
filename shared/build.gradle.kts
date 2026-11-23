import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9 forbids the Kotlin Multiplatform plugin in a `com.android.application`
    // module, so the Android target of this library comes from Google's plugin.
    alias(libs.plugins.android.kotlin.multiplatform.library)
    // Compose Multiplatform: the same Compose code renders on Android and iOS.
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.compose)
    // The API models are @Serializable: the plugin generates their serializers
    // for every target (JVM here, Kotlin/Native on iOS).
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    // Desktop/JVM is not a shipping target. It exists so the shared code can be
    // compiled and tested on Windows, where neither a Mac nor the Android SDK is
    // available. If it compiles here, commonMain is almost certainly fine.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_21) }
    }

    android {
        namespace = "app.ocmobile.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest {}
    }

    // Intel simulators (iosX64) are not declared: Apple Silicon only.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            // The same engine as Android, so the canary on Windows exercises the
            // engine the phone uses.
            implementation(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
