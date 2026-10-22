plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    // ReportDrawnWhen: the chat list reports the app fully drawn for the startup benchmark (docs/PERFORMANCE.md §1).
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // ChatListViewModel is tested against fakes of the domain repositories and a fake clock.
    // The picked photo of a new group or channel comes from its fake there, shared with the other features.
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
