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
        // CI run number keeps every published build installable over the previous one
        versionCode   = providers.environmentVariable("GITHUB_RUN_NUMBER").orElse("1").get().toInt()
        versionName   = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions { jvmTarget = "11" }
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

    implementation(libs.zxing.core)

    // ── Test ─────────────────────────────────────────────────────────────
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(kotlin("test"))
}
