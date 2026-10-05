// The browser version of the app. A build of its own, so the Android build stays as it is;
// it compiles core and the Firebase data layout (SessionSchema) from their folders.
pluginManagement {
    repositories {
        // Compose Multiplatform pulls AndroidX's multiplatform libraries from Google's repository.
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MtGLifeTrackerWeb"
