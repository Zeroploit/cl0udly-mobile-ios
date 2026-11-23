import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    // AGP 9 forbids the Kotlin Multiplatform plugin in a `com.android.application`
    // module, so the Android target of this library comes from Google's plugin.
    alias(libs.plugins.android.kotlin.multiplatform.library)
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
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
