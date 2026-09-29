/**
 * Standalone settings for the backend — lets you build/run the server
 * without the Android SDK:
 *
 *   cd backend && gradle run
 *
 * The full multi-module build (including :app) lives at the repo root.
 */
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral() }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}

rootProject.name = "MtGLifeTrackerBackend"

// Shared protocol module lives one level up
include(":shared")
project(":shared").projectDir = file("../shared")
