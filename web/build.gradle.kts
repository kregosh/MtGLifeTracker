import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.0.21"
    kotlin("plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.3"
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        moduleName = "mtglifetracker"
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
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
                implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel:2.8.4")
                implementation(npm("firebase", "10.14.1"))
            }
        }
    }
}
