// Root build file — declare every plugin used by any subproject here with
// `apply false` so Gradle registers the version on the build classpath once,
// before subprojects apply them.  This prevents the
// "plugin already on the classpath with an unknown version" error that occurs
// when AGP bundles a Kotlin runtime and a subproject then tries to add it again.
plugins {
    alias(libs.plugins.android.application)  apply false
    alias(libs.plugins.android.library)      apply false
    alias(libs.plugins.kotlin.android)       apply false
    alias(libs.plugins.kotlin.jvm)           apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose)       apply false
}
