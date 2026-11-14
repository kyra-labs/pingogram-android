// Fakes (FakeTdlibClient), fixtures and test rules, used only from test source sets.
plugins {
    alias(libs.plugins.pingogram.android.library)
}

dependencies {
    api(projects.core.domain)
    api(projects.core.tdlib)
    // assertAccessible: semantics checks for Compose UI tests (docs/audit/accessibility.md).
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
    // FakeActivityResultRegistry: activity results without another app, for Compose UI tests.
    api(libs.androidx.activity.compose)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
