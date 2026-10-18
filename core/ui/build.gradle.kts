plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.compose)
    alias(libs.plugins.pingogram.android.roborazzi)
}

dependencies {
    api(libs.androidx.compose.material3)
    // PingogramTheme takes ThemeMode and Contrast from the domain model.
    api(projects.core.domain)
}
