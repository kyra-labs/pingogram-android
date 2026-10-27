plugins {
    alias(libs.plugins.pingogram.android.feature)
    // Kotlin warnings fail the build here (docs/audit/v1-readiness.md).
    alias(libs.plugins.pingogram.kotlin.warnings)
}

dependencies {
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    // Plays the viewer's videos and GIFs from TDLib's local files, on a Compose surface; see the catalog entry.
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui.compose)

    // MediaViewerViewModel is tested against fakes of the domain repositories and of the player; the stories viewer
    // against the stories repository's fake there, shared with the chat list's rings.
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
