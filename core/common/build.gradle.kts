plugins {
    alias(libs.plugins.pingogram.jvm.library)
    // Kotlin warnings fail the build here (docs/audit/v1-readiness.md).
    alias(libs.plugins.pingogram.kotlin.warnings)
}

dependencies {
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
