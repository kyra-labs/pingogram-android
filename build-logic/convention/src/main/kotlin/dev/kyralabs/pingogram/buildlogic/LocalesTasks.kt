package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * The languages that ship ([Translations.shipped]), read from the `strings.xml` files at configuration time, as the
 * resource filters need them then. A [ValueSource], so the configuration cache reads the files again before reusing
 * an entry: a language that crossed the threshold ships on the next build.
 */
abstract class ShippedLanguagesSource : ValueSource<List<String>, ShippedLanguagesSource.Parameters> {
    interface Parameters : ValueSourceParameters {
        /** Every `strings.xml` of the modules' `res/values` and `res/values-<language>` directories. */
        val stringFiles: ConfigurableFileCollection
    }

    override fun obtain(): List<String> = Translations.shipped(Translations.coverage(parameters.stringFiles.files))
}

/** Writes `res/xml/locales_config.xml` listing [languages] into [outputDirectory], a generated resource directory. */
@CacheableTask
abstract class GenerateLocaleConfigTask : DefaultTask() {
    @get:Input
    abstract val languages: ListProperty<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val file = outputDirectory.file("xml/locales_config.xml").get().asFile
        file.parentFile.mkdirs()
        file.writeText(Translations.localeConfig(languages.get()))
    }
}

/** Prints how much of the source each language translates, and which ship: `./gradlew :app:translationCoverage`. */
@DisableCachingByDefault(because = "It only prints a report")
abstract class TranslationCoverageTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val stringFiles: ConfigurableFileCollection

    @TaskAction
    fun report() {
        val coverage = Translations.coverage(stringFiles.files)
        val shipped = Translations.shipped(coverage)
        if (coverage.isEmpty()) logger.lifecycle("No translations yet; ships ${Translations.SOURCE_LANGUAGE} only.")
        coverage.forEach { (tag, it) ->
            val percent = "%.1f".format(java.util.Locale.ROOT, it.share * PERCENT)
            val state = if (tag in shipped) "ships" else "below ${(Translations.SHIP_THRESHOLD * PERCENT).toInt()}%"
            logger.lifecycle("$tag: ${it.translated}/${it.total} ($percent%), $state")
        }
    }

    private companion object {
        const val PERCENT = 100
    }
}
