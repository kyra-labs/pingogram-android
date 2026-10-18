// Fakes (FakeTdlibClient), fixtures and test rules, used only from test source sets.
plugins {
    alias(libs.plugins.pingogram.android.library)
}

dependencies {
    api(projects.core.domain)
    api(projects.core.tdlib)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
