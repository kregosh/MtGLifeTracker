// The browser version of the app. A build of its own, so the Android build stays as it is;
// it compiles core and the Firebase data layout (SessionSchema) from their folders.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "MtGLifeTrackerWeb"
