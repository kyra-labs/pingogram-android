plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.hilt)
    // TDLib AAR as an `api` dependency (fetched from the pinned GitHub release) and `./gradlew fetchTdlib`.
    alias(libs.plugins.pingogram.tdlib)
}

dependencies {
    api(projects.core.common)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
