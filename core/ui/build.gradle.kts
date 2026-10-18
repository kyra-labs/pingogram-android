plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.compose)
    alias(libs.plugins.pingogram.android.roborazzi)
}

dependencies {
    api(libs.androidx.compose.material3)
    // TdlibFileImage's fetcher and keyer are Coil components, which :app registers in its ImageLoader, and features
    // show other TDLib images (message photos, stickers) with AsyncImage. No coil-network-*: TDLib's files only.
    api(libs.coil.compose)
    // PingogramTheme takes ThemeMode and Contrast from the domain model.
    api(projects.core.domain)
    // FileProvider, for the content URIs of TDLib files handed to other apps (file/TdlibFiles.kt). Already in the app
    // through Compose; the catalog pins the version it resolves to.
    implementation(libs.androidx.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
