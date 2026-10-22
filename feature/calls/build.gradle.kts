plugins {
    alias(libs.plugins.pingogram.android.feature)
    // The call engine, NTgCalls, built by .github/workflows/ntgcalls.yml (docs/adr/0015-call-media-engine.md): this
    // module only, and only behind its CallEngine interface.
    alias(libs.plugins.pingogram.ntgcalls)
}

dependencies {
    implementation(projects.core.common)
    // The call's notifications (CallNotificationBuilder) and channels.
    implementation(projects.core.notifications)
    // CallActivity: setContent, the permission request, the view model.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Android's Telecom as a self-managed calling app (docs/adr/0017-calls-and-telecom.md).
    implementation(libs.androidx.core.telecom)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
