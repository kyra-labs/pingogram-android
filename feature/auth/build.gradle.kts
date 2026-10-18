plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.collections.immutable)
    // Draws the QR code for QR login (FR-A6) on the device; nothing leaves it.
    implementation(libs.zxing.core)

    // AuthViewModel is tested through the real TDLib repositories on FakeTdlibClient (docs/TESTING.md).
    testImplementation(projects.core.data)
    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
