plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}


dependencies {
    api(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
}
