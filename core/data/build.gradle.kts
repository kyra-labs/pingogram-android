plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.hilt)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(projects.core.tdlib)
}
