// The demo for the app stores' reviewers (docs/adr/0034-demo-mode-for-store-review.md): a TdlibClient that answers
// from sample data stored on the phone. It never creates a TDLib instance or touches the network: it depends on
// core:tdlib only for the interface and TdApi's plain Java classes.
plugins {
    alias(libs.plugins.pingogram.android.library)
    // Kotlin warnings fail the build here (docs/audit/v1-readiness.md).
    alias(libs.plugins.pingogram.kotlin.warnings)
    alias(libs.plugins.pingogram.android.hilt)
}

dependencies {
    api(projects.core.tdlib)
    implementation(projects.core.common)
    // createBitmap for the pictures the demo draws (DemoFiles).
    implementation(libs.androidx.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
