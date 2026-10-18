plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

android {
    namespace  = "com.kregosh.mtglifetracker"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.kregosh.mtglifetracker"
        minSdk        = 26
        targetSdk     = 36
        // Every published build needs a higher version code than the one before. CI passes its
        // run number; the Play release workflow its own counter (VERSION_CODE).
        versionCode   = providers.environmentVariable("VERSION_CODE")
            .orElse(providers.environmentVariable("GITHUB_RUN_NUMBER")).orElse("1").get().toInt()
        versionName   = providers.environmentVariable("VERSION_NAME").orElse("0.1.0").get()
    }

    // Android installs an update only when it is signed with the same key as the installed app.
    // CI points DEBUG_KEYSTORE at the stable debug key (from the DEBUG_KEYSTORE_BASE64 secret);
    // AGP 9 no longer picks it up from ~/.android on its own, so it is named here. Locally the
    // usual per-machine debug key is used.
    signingConfigs {
        getByName("debug") {
            providers.environmentVariable("DEBUG_KEYSTORE").orNull?.let { path ->
                storeFile     = file(path)
                storePassword = "android"
                keyAlias      = "androiddebugkey"
                keyPassword   = "android"
            }
        }
        // Play uploads are signed with the upload key; Google re-signs them for the store (Play
        // App Signing). The Play release workflow provides it from repository secrets.
        providers.environmentVariable("UPLOAD_KEYSTORE").orNull?.let { path ->
            create("upload") {
                storeFile     = file(path)
                storePassword = providers.environmentVariable("UPLOAD_KEYSTORE_PASSWORD").get()
                keyAlias      = providers.environmentVariable("UPLOAD_KEY_ALIAS").get()
                keyPassword   = providers.environmentVariable("UPLOAD_KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("upload")?.let { signingConfig = it }
        }
    }

    buildFeatures {
        compose = true
    }

    // The screens are shared with the browser app (web/), which compiles the same folder.
    sourceSets["main"].kotlin.srcDir("../ui/src/main/kotlin")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
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
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.zxing.core)

    // ── Test ─────────────────────────────────────────────────────────────
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(kotlin("test-junit"))  // AGP's built-in Kotlin doesn't pick the JUnit flavour itself
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
