plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace  = "com.kregosh.mtglifetracker.firebase"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(kotlin("test-junit"))  // AGP's built-in Kotlin doesn't pick the JUnit flavour itself
}
