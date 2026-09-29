// Root build file — only declare plugins needed for non-Android subprojects here.
// Android-specific plugins are declared in :app/build.gradle.kts directly.
plugins {
    alias(libs.plugins.kotlin.jvm)           apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
