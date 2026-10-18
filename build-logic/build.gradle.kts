// Loads the Kotlin Gradle plugin and ktlint once for all build-logic projects, so `kotlin-dsl`
// (convention), `embedded-kotlin` (detekt-rules) and ktlint share them. ktlint here also checks this
// file and settings.gradle.kts.
plugins {
    `embedded-kotlin` apply false
    `kotlin-dsl` apply false
    alias(libs.plugins.ktlint)
}

ktlint {
    version = libs.versions.ktlint
}
