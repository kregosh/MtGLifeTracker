plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace  = "com.kregosh.mtglifetracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.kregosh.mtglifetracker"
        minSdk        = 26
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions { jvmTarget = "11" }
}

// ── Debug APK rotation: keep last 3 builds ───────────────────────────────────
val buildTimestamp = System.currentTimeMillis()

android.applicationVariants.configureEach {
    if (buildType.name == "debug") {
        outputs.configureEach {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "app-debug-$buildTimestamp.apk"
        }
    }
}

tasks.register("trimOldDebugApks") {
    mustRunAfter("packageDebug")
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/debug").get().asFile
        if (!dir.exists()) return@doLast
        dir.listFiles { f -> f.extension == "apk" }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(3)
            ?.forEach {
                logger.lifecycle("Removing old debug APK: ${it.name}")
                it.delete()
            }
    }
}

tasks.matching { it.name == "assembleDebug" }.configureEach {
    finalizedBy("trimOldDebugApks")
}

dependencies {
    // ── Modules ──────────────────────────────────────────────────────
    implementation(project(":core"))
    implementation(project(":firebase"))

    // ── Coroutines ───────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Jetpack ──────────────────────────────────────────────────────
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)

    // ── Compose ──────────────────────────────────────────────────────
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
}
