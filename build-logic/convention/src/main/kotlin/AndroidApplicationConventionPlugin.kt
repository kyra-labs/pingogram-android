import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.FilterConfiguration
import dev.kyralabs.pingogram.buildlogic.PingogramAndroid
import dev.kyralabs.pingogram.buildlogic.PingogramVersion
import dev.kyralabs.pingogram.buildlogic.ReleaseSigning
import dev.kyralabs.pingogram.buildlogic.TelegramSecrets
import dev.kyralabs.pingogram.buildlogic.configureKotlinAndroid
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.android.application")
            apply(plugin = "pingogram.android.lint")
            apply(plugin = "pingogram.detekt")
            apply(plugin = "pingogram.ktlint")

            val version = PingogramVersion.parse(providers.gradleProperty("version").orNull)
            val secrets = TelegramSecrets.parse(rootFileText(TelegramSecrets.FILE_NAME))

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                buildFeatures.buildConfig = true
                defaultConfig {
                    targetSdk = PingogramAndroid.TARGET_SDK
                    versionName = version.name
                    versionCode = version.versionCode()
                    buildConfigField("int", TelegramSecrets.API_ID, secrets.apiId.toString())
                    buildConfigField("String", TelegramSecrets.API_HASH, "\"${secrets.apiHash}\"")
                }
                configureSigning(this)
                // One APK per ABI plus a universal one, as published by the release workflow
                // (docs/RELEASE.md). The ABIs are the ones TDLib is built for. AGP cannot build an app
                // bundle while ABI splits are on (https://issuetracker.google.com/402800800), so a build
                // that packages a bundle makes no split APKs; release.yml runs the two separately.
                splits.abi {
                    isEnable = !requestsBundle()
                    reset()
                    include(
                        *PingogramVersion.ABI_INDEX.keys
                            .filterNotNull()
                            .toTypedArray(),
                    )
                    isUniversalApk = true
                }
                // Some AndroidX libraries (androidx.graphics:graphics-path) also ship native code for ABIs
                // TDLib is not built for. Packaging it would let such devices install the app and crash
                // when TDLib loads, instead of refusing the install.
                packaging.jniLibs.excludes += UNSUPPORTED_ABIS.map { "lib/$it/**" }
                // Fonts stay uncompressed, so the platform maps the variable Google Sans Flex file (4 MB) from the
                // APK when the first text draws, instead of inflating all of it into memory (docs/PERFORMANCE.md
                // §8). The APKs grow by what compression saved, about 1.7 MB.
                androidResources.noCompress += "ttf"
            }

            // Split APKs get their own code (ABI_INDEX); outputs without an ABI filter are universal.
            extensions.configure<ApplicationAndroidComponentsExtension> {
                onVariants { variant ->
                    variant.outputs.forEach { output ->
                        val abi =
                            output.filters
                                .find { it.filterType == FilterConfiguration.FilterType.ABI }
                                ?.identifier
                        output.versionCode.set(version.versionCode(abi))
                    }
                }
            }
        }
    }
}

/**
 * Debug builds use the committed `app/debug.keystore` so every contributor gets the same signature;
 * release builds use [ReleaseSigning] or, when it is not configured, fail before any task runs.
 */
private fun Project.configureSigning(android: ApplicationExtension) {
    android.signingConfigs.getByName("debug") {
        // The well-known public debug credentials from docs/RELEASE.md, not a secret.
        storeFile = file("debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }

    val resolution =
        ReleaseSigning.resolve(
            env = { providers.environmentVariable(it).orNull },
            keystoreProperties = rootFileText(ReleaseSigning.PROPERTIES_FILE),
        )
    when (resolution) {
        is ReleaseSigning.Resolution.Configured -> {
            val signing = resolution.signing
            android.buildTypes.getByName("release").signingConfig =
                android.signingConfigs.create("release") {
                    // A relative path in keystore.properties is relative to the repository root.
                    storeFile = layout.settingsDirectory.file(signing.storeFile).asFile
                    storePassword = signing.storePassword
                    keyAlias = signing.keyAlias
                    keyPassword = signing.keyPassword
                }
        }

        is ReleaseSigning.Resolution.Missing -> {
            failReleasePackaging(resolution.message)
        }
    }

    // minSdk 26 needs no v1 (JAR) signature.
    android.signingConfigs.configureEach {
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = true
        enableV4Signing = true
    }
}

/**
 * Without a signing config AGP would quietly produce unsigned release APKs and bundles. Instead, fail as
 * soon as the task graph contains one of their packaging tasks, before anything runs; every other task
 * (debug builds, `./gradlew check`, compiling the release variants) keeps working.
 */
private fun Project.failReleasePackaging(message: String) {
    val packagingTasks = mutableListOf<String>()
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withBuildType("release")) { variant ->
            val name = variant.name.replaceFirstChar(Char::uppercaseChar)
            packagingTasks += listOf("$path:package$name", "$path:package${name}Bundle")
        }
    }
    gradle.taskGraph.whenReady {
        val requested = packagingTasks.filter { hasTask(it) }
        if (requested.isNotEmpty()) throw GradleException("Cannot run ${requested.joinToString()}. $message")
    }
}

/** ABIs that dependencies may ship native code for, but TDLib (and so the app) does not support. */
private val UNSUPPORTED_ABIS = listOf("x86", "riscv64")

/** Whether the requested tasks build an app bundle (`bundleFossRelease`, `:app:bundle`, …). */
private fun Project.requestsBundle(): Boolean =
    gradle.startParameter.taskNames.any { it.substringAfterLast(':').startsWith("bundle") }

/** Text of a file in the repository root, or null if it does not exist; tracked by the configuration cache. */
private fun Project.rootFileText(name: String): String? =
    providers.fileContents(layout.settingsDirectory.file(name)).asText.orNull
