plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.hilt)
}

dependencies {
    implementation(projects.core.domain)
}
