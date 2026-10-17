plugins {
    alias(libs.plugins.pingogram.android.application)
    alias(libs.plugins.pingogram.android.compose)
    alias(libs.plugins.pingogram.android.hilt)
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

dependencies {
    implementation(libs.androidx.core.splashscreen)

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
