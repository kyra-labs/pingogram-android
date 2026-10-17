import dev.kyralabs.pingogram.buildlogic.Detekt
import dev.kyralabs.pingogram.buildlogic.libs
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencyScopeConfiguration
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmEnvironment
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * detekt on every Kotlin source set of the module, wired into `check`: `config/detekt/detekt.yml`
 * on top of detekt's defaults.
 *
 * The stable detekt CLI runs through [Detekt] instead of detekt's 1.23 Gradle plugin, which has no
 * AGP 9 support and calls Gradle APIs that Gradle 10 removes. Add rule set jars with `detektPlugins(...)`.
 */
class DetektConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply<LifecycleBasePlugin>()

            val detektClasspath = jvmRuntimeClasspath(configurations.dependencyScope("detekt"))
            val detektPluginsClasspath = jvmRuntimeClasspath(configurations.dependencyScope("detektPlugins"))
            dependencies {
                "detekt"(libs.findLibrary("detekt-cli").get())
            }

            val detektTask =
                tasks.register<Detekt>("detekt") {
                    group = LifecycleBasePlugin.VERIFICATION_GROUP
                    description = "Runs detekt on this module's Kotlin sources."
                    source.from(fileTree("src") { include("**/*.kt", "**/*.kts") })
                    config.set(layout.settingsDirectory.file("config/detekt/detekt.yml"))
                    classpath.from(detektClasspath)
                    plugins.from(detektPluginsClasspath)
                    basePath.set(layout.settingsDirectory.asFile.absolutePath)
                    reportsDirectory.set(layout.buildDirectory.dir("reports/detekt"))
                }
            tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(detektTask) }
        }
    }
}

/** Resolves [dependencies] like a JVM runtime classpath (`<name>Classpath`). */
private fun Project.jvmRuntimeClasspath(dependencies: NamedDomainObjectProvider<DependencyScopeConfiguration>) =
    configurations.resolvable("${dependencies.name}Classpath") {
        extendsFrom(dependencies.get())
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
            attribute(
                TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                objects.named(TargetJvmEnvironment.STANDARD_JVM),
            )
        }
    }
