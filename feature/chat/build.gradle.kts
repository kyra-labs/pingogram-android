plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // ChatViewModel is tested against fakes of the domain repositories and a fake clock.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
