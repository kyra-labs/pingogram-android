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
    // Records round video messages (ui/videonote, docs/adr/0032-round-video-messages.md): CameraX's VideoCapture and
    // Recorder, its preview in camera-compose's CameraXViewfinder. The same CameraX as feature:scanner's.
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.video) {
        // Guava's compile-time annotations, which camera-video's Guava would bring and media3 leaves out of its own:
        // nothing reads them at run time, and they stay out of the app as before.
        exclude(group = "com.google.code.findbugs", module = "jsr305")
        exclude(group = "com.google.errorprone", module = "error_prone_annotations")
        exclude(group = "com.google.j2objc", module = "j2objc-annotations")
        exclude(group = "org.checkerframework", module = "checker-qual")
    }

    // ChatViewModel is tested against fakes of the domain repositories and a fake clock; core:testing's fakes of the
    // app's language and the device's language detection among them, and assertAccessible for the export page.
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
