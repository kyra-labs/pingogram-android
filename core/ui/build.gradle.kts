plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.compose)
}

dependencies {
    api(libs.androidx.compose.material3)
    implementation(projects.core.domain)
}
