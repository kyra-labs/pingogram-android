import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

// Puts the plugins on the root classpath once, so convention plugins in build-logic
// (which only compile against them) all load the same versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.androidx.baselineprofile) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    // Applied here too: checks this file and settings.gradle.kts.
    alias(libs.plugins.ktlint)
    // `./gradlew dependencyUpdates` lists newer stable versions of every dependency and plugin.
    alias(libs.plugins.gradle.versions)
}

ktlint {
    version = libs.versions.ktlint
}

tasks.withType<DependencyUpdatesTask>().configureEach {
    // Only stable releases are candidates (CLAUDE.md rule 7).
    rejectVersionIf { !candidate.version.isStableVersion() }
}

fun String.isStableVersion(): Boolean =
    listOf("RELEASE", "FINAL", "GA").any { uppercase().contains(it) } || matches(Regex("""^[0-9,.v-]+(-r)?$"""))

// build-logic (an included build) is checked by `./gradlew check` (unit tests, detekt rule tests,
// ktlint) and formatted by `./gradlew ktlintFormat`, like the modules.
val buildLogic = gradle.includedBuild("build-logic")

tasks.register("check") {
    group = "verification"
    description = "Runs the root and build-logic checks; module checks run through each module's own `check`."
    dependsOn(tasks.ktlintCheck)
    dependsOn(buildLogic.task(":ktlintCheck"))
    dependsOn(buildLogic.task(":convention:check"))
    dependsOn(buildLogic.task(":detekt-rules:check"))
}

tasks.ktlintFormat {
    dependsOn(buildLogic.task(":ktlintFormat"))
    dependsOn(buildLogic.task(":convention:ktlintFormat"))
    dependsOn(buildLogic.task(":detekt-rules:ktlintFormat"))
}
