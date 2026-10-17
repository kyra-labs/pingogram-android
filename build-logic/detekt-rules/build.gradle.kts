import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// Pingogram's own detekt rules, loaded by every module's `detekt` task (pingogram.detekt).
plugins {
    `embedded-kotlin`
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
        // detekt-cli 1.23 runs the rules on its own Kotlin 2.0 stdlib, so newer stdlib calls must not
        // compile. Kotlin 2.4 deprecates API 2.0; the warning is silenced until detekt 2 (Kotlin 2.2+).
        apiVersion = KotlinVersion.KOTLIN_2_0
        freeCompilerArgs.add("-Xsuppress-version-warnings")
    }
}

ktlint {
    version = libs.versions.ktlint
}

dependencies {
    // Provided by detekt-cli at runtime.
    compileOnly(libs.detekt.api)

    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.assertj.core)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
