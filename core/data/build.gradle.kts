plugins {
    alias(libs.plugins.pingogram.android.library)
    alias(libs.plugins.pingogram.android.hilt)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.datastore)
    implementation(projects.core.tdlib)
    // LocationManagerCompat and ContextCompat (AndroidDeviceLocationRepository, AndroidSharedStorageRepository).
    implementation(libs.androidx.core)

    testImplementation(projects.core.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // AndroidMediaProcessorTest decodes, turns and writes real images on Robolectric's native graphics (JUnit 4).
    testImplementation(libs.robolectric)
    testImplementation(libs.junit4)
    testRuntimeOnly(libs.junit.vintage.engine)
}
