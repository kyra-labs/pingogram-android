// Macrobenchmarks and the Baseline Profile generator for :app (docs/PERFORMANCE.md §9). They need a device:
// `./gradlew check` only compiles them.
plugins {
    alias(libs.plugins.pingogram.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    targetProjectPath = ":app"

    // Measure and profile foss, the flavor GitHub releases ship. The profile is merged into :app's main
    // source set, so gms ships the same one.
    defaultConfig {
        missingDimensionStrategy("distribution", "foss")
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
}
