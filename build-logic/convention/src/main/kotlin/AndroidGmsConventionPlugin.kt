import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.google.gms.googleservices.GoogleServicesPlugin.GoogleServicesPluginConfig
import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import dev.kyralabs.pingogram.buildlogic.GoogleFreeCheckTask
import dev.kyralabs.pingogram.buildlogic.GoogleServices
import dev.kyralabs.pingogram.buildlogic.moduleIds
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * `:app` only: the `gms` flavor's Firebase setup and the `foss` flavor's freedom from Google's services
 * (`docs/adr/0004-push-delivery.md`).
 *
 * - With `app/src/gms/google-services.json` ([GoogleServices]), applies the Google Services plugin, which turns it
 *   into the resources Firebase reads. The plugin looks for a file for every variant; `foss` variants find none, which
 *   it is told to ignore, so they get no Firebase resources.
 * - Without the file, packaging a `gms` variant (`assembleGms*`, `bundleGms*`, `installGms*`) fails before anything
 *   runs, with a message that names the file and `docs/OWNER_SETUP.md`; everything else works as before, `./gradlew
 *   check` included (CI has no file).
 * - `app/google-services.json` fails configuration: the plugin would find it for the `foss` variants too.
 * - `check<FossVariant>GoogleFree` ([GoogleFreeCheckTask]) fails when a `foss` variant's runtime classpath holds
 *   Firebase or Play services; `check` runs it for every `foss` variant, and each variant's build runs its own.
 */
class AndroidGmsConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.application") {
                val setup =
                    GoogleServices.resolve(
                        flavorFile = projectFileExists(GoogleServices.FLAVOR_FILE),
                        misplacedFile = projectFileExists(GoogleServices.MISPLACED_FILE),
                    )
                when (setup) {
                    GoogleServices.Setup.Configured -> {
                        apply(plugin = "com.google.gms.google-services")
                        extensions.configure<GoogleServicesPluginConfig> {
                            missingGoogleServicesStrategy = MissingGoogleServicesStrategy.IGNORE
                        }
                    }

                    is GoogleServices.Setup.Missing -> {
                        failGmsPackaging(setup.message)
                    }

                    is GoogleServices.Setup.Misplaced -> {
                        throw GradleException(setup.message)
                    }
                }
                registerGoogleFreeChecks()
            }
        }
    }
}

/** Whether the file exists, read through a provider so the configuration cache notices it appear or go. */
private fun Project.projectFileExists(path: String): Boolean =
    providers.fileContents(layout.projectDirectory.file(path)).asBytes.isPresent

/**
 * Fails as soon as the task graph packages a `gms` variant, before anything runs, as release signing does
 * (`AndroidApplicationConventionPlugin`): an APK without Firebase's configuration would run, but never get a push.
 */
private fun Project.failGmsPackaging(message: String) {
    val packagingTasks = mutableListOf<String>()
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants(selector().withFlavor("distribution" to GoogleServices.FLAVOR)) { variant ->
            val name = variant.name.replaceFirstChar(Char::uppercaseChar)
            packagingTasks += listOf("$path:package$name", "$path:package${name}Bundle")
        }
    }
    gradle.taskGraph.whenReady {
        val requested = packagingTasks.filter { hasTask(it) }
        if (requested.isNotEmpty()) throw GradleException("Cannot run ${requested.joinToString()}. $message")
    }
}

private fun Project.registerGoogleFreeChecks() {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        onVariants { variant ->
            if (variant.productFlavors.any { (_, flavor) -> flavor == GoogleServices.FLAVOR }) return@onVariants
            val name = variant.name.replaceFirstChar(Char::uppercaseChar)
            val task =
                tasks.register<GoogleFreeCheckTask>("check${name}GoogleFree") {
                    group = "verification"
                    description = "Checks that ${variant.name} depends on no Firebase or Play services."
                    variantName.set(variant.name)
                    modules.set(
                        variant.runtimeConfiguration.incoming.resolutionResult.rootComponent
                            .map(::moduleIds),
                    )
                    report.set(layout.buildDirectory.file("reports/google-free/${variant.name}.txt"))
                }
            tasks.named("check") { dependsOn(task) }
            tasks.matching { it.name == "pre${name}Build" }.configureEach { dependsOn(task) }
        }
    }
}
