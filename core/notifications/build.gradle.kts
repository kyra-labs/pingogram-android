// Notifications from TDLib's notification engine (docs/ARCHITECTURE.md, Notifications): channels, conversation
// notifications, shortcuts and the actions' receiver and worker.
plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.hilt)
}

android {
    // The tests post real notifications and read string resources on Robolectric.
    testOptions.unitTests.isIncludeAndroidResources = true
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    // The chat list's message previews and names (text/), the avatar placeholder colors and initials, the font.
    implementation(projects.core.ui)
    implementation(libs.androidx.core)
    // NotificationActionWorker and its Hilt factory; :app hands HiltWorkerFactory to WorkManager.
    api(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // The renderer and the worker are tested against the real repositories on FakeTdlibClient, from TDLib's updates to
    // the notifications posted.
    testImplementation(projects.core.data)
    testImplementation(projects.core.tdlib)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.work.testing)
    // Robolectric: notifications, shortcuts and pending intents (JUnit 4 on the JUnit Platform).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
