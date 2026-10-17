plugins {
    alias(libs.plugins.pingogram.android.application)
    alias(libs.plugins.pingogram.android.compose)
    alias(libs.plugins.pingogram.android.hilt)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "dev.kyralabs.pingogram"

    defaultConfig {
        applicationId = "dev.kyralabs.pingogram"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            // R8 runs in full mode by default.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        // Release-like, non-debuggable builds for :benchmark (Macrobenchmark and Baseline Profile generation).
        // The baseline profile plugin would create them itself, but copy the release signing; declaring them here
        // signs them with the debug key, so they build without release credentials and never touch the
        // release-signing guard (which covers only `release`). The plugin still makes them profileable and turns
        // R8 off for nonMinifiedRelease.
        listOf("benchmarkRelease", "nonMinifiedRelease").forEach { name ->
            create(name) {
                initWith(getByName("release"))
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    // foss: no Google services, the default for GitHub releases. gms: FCM push (M3).
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            isDefault = true
        }
        create("gms") {
            dimension = "distribution"
        }
    }
}

// Baseline + Startup Profile from :benchmark (docs/PERFORMANCE.md §2). `./gradlew :app:generateBaselineProfile`
// writes it to src/main/generated/baselineProfiles, so both flavors ship it; profileinstaller installs it on
// sideloaded APKs, which get no cloud profiles.
baselineProfile {
    mergeIntoMain = true
    dexLayoutOptimization = true
}

dependencies {
    baselineProfile(projects.benchmark)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.profileinstaller)

    implementation(projects.core.common)
    implementation(projects.core.data)
    implementation(projects.core.datastore)
    implementation(projects.core.navigation)
    implementation(projects.core.notifications)
    implementation(projects.core.tdlib)
    implementation(projects.core.ui)

    implementation(projects.feature.auth)
    implementation(projects.feature.chat)
    implementation(projects.feature.chats)
    implementation(projects.feature.contacts)
    implementation(projects.feature.media)
    implementation(projects.feature.profile)
    implementation(projects.feature.settings)
}
