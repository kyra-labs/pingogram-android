import org.gradle.plugin.use.PluginDependency
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint)
}

group = "dev.kyralabs.pingogram.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

ktlint {
    version = libs.versions.ktlint
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.google.services.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // SpdxLicensesTest and OpenSourceLicensesTest read the repository's config/licenses (relative to this project,
    // the tests' working directory), so a change there runs them again.
    inputs
        .dir("../../config/licenses")
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("licensesConfig")
}

tasks {
    validatePlugins {
        enableStricterValidation = true
        failOnWarning = true
    }
}

// Plugin ids come from the version catalog, the same entries module build scripts apply.
val Provider<PluginDependency>.pluginId: String get() = get().pluginId

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.pingogram.android.application.pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.pingogram.android.compose.pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.pingogram.android.feature.pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidGms") {
            id = libs.plugins.pingogram.android.gms.pluginId
            implementationClass = "AndroidGmsConventionPlugin"
        }
        register("androidHilt") {
            id = libs.plugins.pingogram.android.hilt.pluginId
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.pingogram.android.library.pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLicenses") {
            id = libs.plugins.pingogram.android.licenses.pluginId
            implementationClass = "AndroidLicensesConventionPlugin"
        }
        register("androidLint") {
            id = libs.plugins.pingogram.android.lint.pluginId
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("androidRoborazzi") {
            id = libs.plugins.pingogram.android.roborazzi.pluginId
            implementationClass = "AndroidRoborazziConventionPlugin"
        }
        register("androidTest") {
            id = libs.plugins.pingogram.android.test.pluginId
            implementationClass = "AndroidTestConventionPlugin"
        }
        register("detekt") {
            id = libs.plugins.pingogram.detekt.pluginId
            implementationClass = "DetektConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.pingogram.jvm.library.pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("ktlint") {
            id = libs.plugins.pingogram.ktlint.pluginId
            implementationClass = "KtlintConventionPlugin"
        }
        register("tdlib") {
            id = libs.plugins.pingogram.tdlib.pluginId
            implementationClass = "TdlibConventionPlugin"
        }
    }
}
