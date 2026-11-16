// @Serializable route objects shared by features (features never depend on each other).
plugins {
    alias(libs.plugins.pingogram.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    // Kotlin warnings fail the build here (docs/audit/v1-readiness.md).
    alias(libs.plugins.pingogram.kotlin.warnings)
}

dependencies {
    api(libs.kotlinx.serialization.core)
}
