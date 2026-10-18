plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    // Plays the viewer's videos and GIFs from TDLib's local files, on a Compose surface; see the catalog entry.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)

    // MediaViewerViewModel is tested against fakes of the domain repositories and of the player.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
