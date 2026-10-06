import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.4.20"
    kotlin("plugin.compose") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
}

// Built-in images come from the Android app's drawables: images/ (light) and images/night/.
val copyImages by tasks.registering(Sync::class) {
    val res = rootDir.resolve("../app/src/main/res")
    from(res.resolve("drawable")) { include("bg_*.webp"); into("images") }
    from(res.resolve("drawable-night")) { include("bg_*.webp"); into("images/night") }
    into(layout.buildDirectory.dir("generated/images"))
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("mtglifetracker")
        browser {
            commonWebpackConfig { outputFileName = "mtglifetracker.js" }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            // Shared with Android: game rules, view model, data model and the Firebase layout.
            kotlin.srcDir("../core/src/main/kotlin")
            kotlin.srcDir("../firebase/src/main/kotlin/com/kregosh/mtglifetracker/network/schema")
            // The screens, shared with the Android app.
            kotlin.srcDir("../ui/src/main/kotlin")
            // The background and card images, from the Android app's resources.
            resources.srcDir(copyImages)
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.11.0")
                implementation(npm("firebase", "10.14.1"))
                implementation(npm("qrcode-generator", "1.4.4"))
            }
        }
    }
}
