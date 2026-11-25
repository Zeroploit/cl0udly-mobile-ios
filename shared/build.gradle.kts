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
            // Fonts ship with the app: the Android res/font files are copied into
            // composeResources so iOS and the desktop preview use the same faces.
            implementation(compose.components.resources)
            // Hardware/gesture back: the sheets and the drawer close on it.
            implementation("org.jetbrains.compose.ui:ui-backhandler:1.12.2")
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            // AsyncImage for the message media: the same Coil API the Android app uses.
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }
        jvmMain.dependencies {
            // The same engine as Android, so the canary on Windows exercises the
            // engine the phone uses.
            implementation(libs.ktor.client.okhttp)
            // Desktop is not a shipping target: it exists so the shared UI can be
            // run and looked at on Windows, which is where the app is developed.
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
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

// `./gradlew :shared:run` opens the shared UI in a phone-sized window on the
// desktop. Nothing is packaged or distributed from here; it is a way to look at
// the Compose code that ships on iOS and Android without either device.
compose.desktop {
    application {
        mainClass = "app.ocmobile.preview.MainKt"
    }
}

compose.resources {
    // A stable package for the generated `Res`: shared code references it by name.
    publicResClass = true
    packageOfResClass = "app.ocmobile.resources"
}
