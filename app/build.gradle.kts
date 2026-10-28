import com.android.build.api.variant.ResValue

plugins {
    alias(libs.plugins.pingogram.android.application)
    // Kotlin warnings fail the build here (docs/audit/v1-readiness.md).
    alias(libs.plugins.pingogram.kotlin.warnings)
    alias(libs.plugins.pingogram.android.compose)
    alias(libs.plugins.pingogram.android.hilt)
    alias(libs.plugins.pingogram.android.roborazzi)
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.pingogram.android.licenses)
    // The languages it ships, from the translations (docs/TRANSLATING.md): locale config and resource filters.
    alias(libs.plugins.pingogram.android.locales)
    // Firebase for the gms flavor's push notifications, and the check that keeps foss free of it.
    alias(libs.plugins.pingogram.android.gms)
}

android {
    namespace = "dev.kyralabs.pingogram"

    defaultConfig {
        applicationId = "dev.kyralabs.pingogram"
    }

    // shortcut_target_package below.
    buildFeatures {
        resValues = true
    }

    // Settings → Language switches among these languages at run time, so a bundle installs all of them, not only the
    // device's (Play would otherwise leave out the others, and Pingogram downloads nothing from Play).
    bundle {
        language {
            enableSplit = false
        }
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

    // foss: no Google services, the default for GitHub releases. gms: FCM push (docs/adr/0004-push-delivery.md).
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            isDefault = true
        }
        // google-services.json for its Firebase project: app/src/gms/ (pingogram.android.gms, docs/OWNER_SETUP.md).
        create("gms") {
            dimension = "distribution"
        }
    }
}

// The package the app shortcuts' intents name (res/xml/shortcuts.xml), which a resource cannot read from the build:
// each variant's application id, ".debug" included.
androidComponents {
    onVariants { variant ->
        variant.resValues.put(
            variant.makeResValueKey("string", "shortcut_target_package"),
            variant.applicationId.map { ResValue(it, "The application id, for res/xml/shortcuts.xml") },
        )
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
    implementation(libs.androidx.compose.material3.adaptive.layout)
    implementation(libs.androidx.compose.material3.adaptive.navigation)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.core.splashscreen)
    // The home screen widget of recent chats (docs/adr/0017-home-screen-widget.md).
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    // HiltWorkerFactory for WorkManager's configuration (PingogramApplication); WorkManager itself comes with
    // core:notifications.
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Transfers and "Keep connected" (foss) start their foreground services only while the app is in the foreground
    // (ProcessLifecycleOwner).
    implementation(libs.androidx.lifecycle.process)
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
    implementation(projects.feature.calls)
    implementation(projects.feature.chat)
    implementation(projects.feature.chats)
    implementation(projects.feature.contacts)
    implementation(projects.feature.feedback)
    implementation(projects.feature.media)
    implementation(projects.feature.profile)
    implementation(projects.feature.scanner)
    implementation(projects.feature.settings)

    // Leak detection in debug builds only (docs/PERFORMANCE.md §9); check<Variant>NoDebugTools fails any other build
    // type that gets it.
    debugImplementation(libs.leakcanary.android)

    // FCM push, gms only (docs/adr/0004-push-delivery.md); foss never sees Firebase or Play services.
    "gmsImplementation"(platform(libs.firebase.bom))
    "gmsImplementation"(libs.firebase.messaging)
    "gmsImplementation"(libs.kotlinx.coroutines.play.services)

    // Navigation and view model tests run the real repositories on FakeTdlibClient, bound with Hilt (docs/TESTING.md).
    testImplementation(projects.core.testing)
    // The language repository's tests keep the app's settings in a DataStore of their own.
    testImplementation(libs.androidx.datastore.preferences)
    testImplementation(libs.hilt.android.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    kspTest(libs.hilt.compiler)
}

// Uploading the gms bundle to Google Play's internal track (docs/PLAY_STORE.md §7): only when release.yml's optional
// step passes a service account's key, so no build loads the plugin otherwise and no credentials live here.
providers.gradleProperty("pingogram.play.serviceAccount").orNull?.let { key ->
    apply(plugin = "com.github.triplet.play")
    configure<com.github.triplet.gradle.play.PlayPublisherExtension> {
        serviceAccountCredentials.set(file(key))
        track.set("internal")
        defaultToAppBundles.set(true)
    }
}
