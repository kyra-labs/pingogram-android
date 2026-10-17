// Puts the plugins on the root classpath once, so convention plugins in build-logic
// (which only compile against them) all load the same versions.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

// The build-logic unit tests (version code formula) run as part of `./gradlew check`.
tasks.register("check") {
    group = "verification"
    description = "Runs the build-logic checks; module checks run through each module's own `check`."
    dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
}
