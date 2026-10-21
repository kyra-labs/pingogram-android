plugins {
    alias(libs.plugins.pingogram.android.feature)
}

dependencies {
    // BackHandler: back from "not on Telegram" returns to the Add contact form.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.savedstate)

    // The view models are tested against fakes of the domain repositories and a fake clock.
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}
