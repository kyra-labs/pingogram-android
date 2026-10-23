plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    // The attach menu's pickers and camera (rememberLauncherForActivityResult); already in the app, as for
    // feature:auth.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    // Plays voice messages and music from TDLib's local files (ui/player); see the catalog entry.
    implementation(libs.media3.exoplayer)

    // ChatViewModel is tested against fakes of the domain repositories and a fake clock; core:testing's fakes of the
    // app's language and the device's language detection among them, and assertAccessible for the export page.
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
