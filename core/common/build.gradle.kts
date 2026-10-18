plugins {
    alias(libs.plugins.pingogram.jvm.library)
}

dependencies {
    api(libs.javax.inject)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)
}
