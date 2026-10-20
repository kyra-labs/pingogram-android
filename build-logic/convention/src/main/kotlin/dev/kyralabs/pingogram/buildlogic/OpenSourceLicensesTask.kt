package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Writes an app variant's open-source licenses list ([OpenSourceLicenses]) as `raw/open_source_licenses.json` into
 * [outputDirectory], a generated resource directory of the variant (`pingogram.android.licenses`). Fails, naming the
 * artifact, when a dependency has no license that maps to an SPDX id or a license has no text.
 */
@CacheableTask
abstract class OpenSourceLicensesTask : DefaultTask() {
    /** For messages only. */
    @get:Internal
    abstract val variantName: Property<String>

    /** The variant's Maven dependencies with their POMs' values, from [PomResolver]. */
    @get:Input
    abstract val libraries: ListProperty<MavenLibrary>

    /** `config/licenses/texts`: one `<SPDX id>.txt` per license. */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val licenseTexts: DirectoryProperty

    /** `config/licenses/extra.json` ([ExtraLibrary]). */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val extraLibraries: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val texts =
            licenseTexts
                .get()
                .asFile
                .listFiles { file -> file.isFile && file.extension == "txt" }
                .orEmpty()
                .associate { it.nameWithoutExtension to it.readText() }
        val generated =
            try {
                OpenSourceLicenses.generate(
                    variant = variantName.get(),
                    dependencies = libraries.get(),
                    extras = OpenSourceLicenses.parseExtras(extraLibraries.get().asFile.readText()),
                    texts = texts,
                )
            } catch (e: IllegalStateException) {
                throw GradleException(e.message.orEmpty(), e)
            } catch (e: IllegalArgumentException) {
                throw GradleException(e.message.orEmpty(), e)
            }
        val raw = outputDirectory.get().dir("raw").asFile
        raw.deleteRecursively()
        raw.mkdirs()
        raw.resolve("${OpenSourceLicenses.RESOURCE_NAME}.json").writeText(OpenSourceLicenses.toJson(generated))
        logger.info("Listed ${generated.libraries.size} open-source libraries for ${variantName.get()}")
    }
}
