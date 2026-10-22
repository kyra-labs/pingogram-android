plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    // The camera permission request and the photo picker of "Scan from image".
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // The QR code scanner's camera (docs/adr/0012-camerax-and-zxing-qr-scanner.md): this module only.
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.compose)
    implementation(libs.camerax.core)
    implementation(libs.camerax.lifecycle)
    // Decodes QR codes from the camera's frames and from picked images, on the device; nothing leaves it.
    implementation(libs.zxing.core)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
