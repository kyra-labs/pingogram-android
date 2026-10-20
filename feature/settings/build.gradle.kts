plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    // The photo picker for a new profile photo, and BackHandler for unsaved profile changes.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    // The notification channels whose system settings Notifications opens (PingogramChannel).
    implementation(projects.core.notifications)

    // SettingsViewModel is tested through the real TDLib repositories on FakeTdlibClient (docs/TESTING.md).
    testImplementation(projects.core.data)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
