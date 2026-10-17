// Loads the Kotlin Gradle plugin and ktlint once for all build-logic projects, so `kotlin-dsl`
// (convention) and ktlint share them. ktlint here also checks this file and settings.gradle.kts.
plugins {
    `kotlin-dsl` apply false
    alias(libs.plugins.ktlint)
}

ktlint {
    version = libs.versions.ktlint
}
