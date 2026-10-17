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
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
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
        register("androidHilt") {
            id = libs.plugins.pingogram.android.hilt.pluginId
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.pingogram.android.library.pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLint") {
            id = libs.plugins.pingogram.android.lint.pluginId
            implementationClass = "AndroidLintConventionPlugin"
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
    }
}
