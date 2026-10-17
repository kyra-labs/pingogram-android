package dev.kyralabs.pingogram.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** SDK levels from `REQUIREMENTS.md` §3. Ask the owner before changing any of them. */
object PingogramAndroid {
    const val COMPILE_SDK = 37
    const val TARGET_SDK = 36
    const val MIN_SDK = 26
    const val NAMESPACE_ROOT = "dev.kyralabs.pingogram"
    val JAVA_VERSION = JavaVersion.VERSION_17
    val JVM_TARGET = JvmTarget.JVM_17
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** `:core:ui` → `dev.kyralabs.pingogram.core.ui`, matching the package naming rule in `docs/ARCHITECTURE.md`. */
internal val Project.pingogramNamespace: String
    get() = PingogramAndroid.NAMESPACE_ROOT + path.replace(':', '.').replace('-', '_')

internal fun Project.configureKotlinAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk = PingogramAndroid.COMPILE_SDK
        defaultConfig.minSdk = PingogramAndroid.MIN_SDK
        compileOptions.sourceCompatibility = PingogramAndroid.JAVA_VERSION
        compileOptions.targetCompatibility = PingogramAndroid.JAVA_VERSION
        testOptions.unitTests.all { it.useJUnitPlatform() }
    }
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions.jvmTarget.set(PingogramAndroid.JVM_TARGET)
    }
    configureJUnit()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = PingogramAndroid.JAVA_VERSION
        targetCompatibility = PingogramAndroid.JAVA_VERSION
    }
    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions.jvmTarget.set(PingogramAndroid.JVM_TARGET)
    }
    tasks.withType<Test>().configureEach { useJUnitPlatform() }
    configureJUnit()
}

private fun Project.configureJUnit() {
    dependencies {
        "testImplementation"(platform(libs.findLibrary("junit-bom").get()))
        "testImplementation"(libs.findLibrary("junit-jupiter").get())
        "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    }
}
