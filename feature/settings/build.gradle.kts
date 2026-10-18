plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // SettingsViewModel is tested through the real TDLib repositories on FakeTdlibClient (docs/TESTING.md).
    testImplementation(projects.core.data)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
