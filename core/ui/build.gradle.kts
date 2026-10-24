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
    // PredictiveBackHandler, for leaving selection mode with back (component/SelectionBack.kt). Already in the app and
    // in the features that show selection mode.
    implementation(libs.androidx.activity.compose)
    // collectAsStateWithLifecycle, for whether a call button offers video (component/CallButtonMenu.kt). Already in the
    // app through every feature.
    implementation(libs.androidx.lifecycle.runtime.compose)
    // The list-detail layouts' shared pane parts (ListDetailPanes.kt).
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)

    // Animated (TGS) stickers, drawn into bitmaps by LottieDrawable (sticker/LottieStickerDecoder.kt,
    // docs/adr/0008-lottie-for-animated-stickers.md). Not its AppCompat dependency: only LottieAnimationView, a View,
    // needs it, and Pingogram has no Views (CLAUDE.md rule 4).
    implementation(libs.lottie) { exclude(group = "androidx.appcompat", module = "appcompat") }

    // Encodes the QR codes Pingogram shows (qr/QrCode.kt: the login's, a chat's link, the user's own) on the device;
    // nothing leaves it. Pure Java; R8 keeps only what is used.
    implementation(libs.zxing.core)

    // TdlibDataSource, which plays TDLib files while they download (media/TdlibDataSource.kt,
    // docs/adr/0027-streaming-media.md). media3-exoplayer, in the features that play, depends on it already.
    implementation(libs.media3.datasource)

    testImplementation(libs.kotlinx.coroutines.test)
}
