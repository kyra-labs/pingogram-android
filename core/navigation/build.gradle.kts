// @Serializable route objects shared by features (features never depend on each other).
plugins {
    alias(libs.plugins.pingogram.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.core)
}
