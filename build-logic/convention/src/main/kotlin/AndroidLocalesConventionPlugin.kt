import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import dev.kyralabs.pingogram.buildlogic.GenerateLocaleConfigTask
import dev.kyralabs.pingogram.buildlogic.ShippedLanguagesSource
import dev.kyralabs.pingogram.buildlogic.TranslationCoverageTask
import dev.kyralabs.pingogram.buildlogic.Translations
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.register

/**
 * `:app` only: the languages Pingogram ships, from the translations themselves (`docs/TRANSLATING.md`). English and
 * every language at least [Translations.SHIP_THRESHOLD] translated across all modules' `strings.xml`:
 *
 * - `generate<Variant>LocaleConfig` writes them as the generated resource `xml/locales_config.xml`, which the manifest
 *   names (`android:localeConfig`, Android 13+'s app languages) and Settings → Language reads on every version;
 * - `androidResources.localeFilters` packages only their resources, the libraries' included, so no screen mixes in a
 *   language Pingogram does not ship; plus the pseudo-locales `en-XA` (long text) and `ar-XB` (right to left), which
 *   debug builds generate (`isPseudoLocalesEnabled`) for layout checks and goldens;
 * - `translationCoverage` prints each language's share.
 *
 * Strings a shipped language lacks show in English.
 */
class AndroidLocalesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            val stringFiles =
                files(
                    rootProject.layout.projectDirectory.asFileTree.matching {
                        include("**/src/*/res/values*/strings.xml")
                        exclude("**/build/**", "build-logic/**", "**/src/test/**", "**/src/androidTest/**")
                    },
                )
            val shipped =
                providers.of(ShippedLanguagesSource::class.java) { parameters.stringFiles.from(stringFiles) }

            pluginManager.withPlugin("com.android.application") {
                extensions.configure<ApplicationExtension> {
                    androidResources.localeFilters +=
                        shipped.get().map(Translations::qualifierOf) + PSEUDO_LOCALES
                    buildTypes.getByName("debug").isPseudoLocalesEnabled = true
                }
                extensions.configure<ApplicationAndroidComponentsExtension> {
                    onVariants { variant ->
                        val name = variant.name.replaceFirstChar(Char::uppercaseChar)
                        val task =
                            tasks.register<GenerateLocaleConfigTask>("generate${name}LocaleConfig") {
                                group = "build"
                                description = "Lists the languages ${variant.name} ships in its locale config."
                                languages.set(shipped)
                            }
                        variant.sources.res?.addGeneratedSourceDirectory(
                            task,
                            GenerateLocaleConfigTask::outputDirectory,
                        )
                    }
                }
            }
            tasks.register<TranslationCoverageTask>("translationCoverage") {
                group = "help"
                description = "Prints how much of the app each language translates, and which ship."
                this.stringFiles.from(stringFiles)
            }
        }
    }

    private companion object {
        /** Android's pseudo-locales: accented, longer English, and mirrored right-to-left English. */
        val PSEUDO_LOCALES = listOf("en-rXA", "ar-rXB")
    }
}
