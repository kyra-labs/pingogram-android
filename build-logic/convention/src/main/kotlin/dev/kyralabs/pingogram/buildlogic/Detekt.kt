package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.IgnoreEmptyDirectories
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import javax.inject.Inject

/**
 * Runs the detekt CLI in its own JVM on [source], with [config] on top of detekt's default config
 * and the rule sets in [plugins]. Fails when detekt reports an issue.
 */
@CacheableTask
abstract class Detekt : DefaultTask() {
    @get:InputFiles
    @get:SkipWhenEmpty
    @get:IgnoreEmptyDirectories
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val source: ConfigurableFileCollection

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val config: RegularFileProperty

    /** detekt-cli and its dependencies. */
    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    /** Extra rule set jars, e.g. build-logic/detekt-rules. */
    @get:Classpath
    abstract val plugins: ConfigurableFileCollection

    /** Report paths are relative to this directory, which keeps the reports relocatable. */
    @get:Internal
    abstract val basePath: Property<String>

    @get:OutputDirectory
    abstract val reportsDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun detekt() {
        val htmlReport = reportsDirectory.file("detekt.html").get().asFile
        val sarifReport = reportsDirectory.file("detekt.sarif").get().asFile
        val arguments =
            buildList {
                add("--input")
                add(source.files.joinToString(","))
                add("--config")
                add(config.get().asFile.path)
                add("--build-upon-default-config")
                if (!plugins.isEmpty) {
                    add("--plugins")
                    add(plugins.files.joinToString(","))
                }
                add("--base-path")
                add(basePath.get())
                add("--report")
                add("html:$htmlReport")
                add("--report")
                add("sarif:$sarifReport")
            }
        // One argument per line; detekt's command-line parser expands `@file`, so long source lists fit.
        val argumentsFile = temporaryDir.resolve("arguments.txt")
        argumentsFile.writeText(arguments.joinToString("\n"))

        val result =
            execOperations.javaexec {
                classpath = this@Detekt.classpath
                mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
                args("@$argumentsFile")
                isIgnoreExitValue = true
            }
        when (result.exitValue) {
            0 -> Unit
            ISSUES_FOUND -> throw GradleException("detekt found issues, listed above. Report: ${htmlReport.toURI()}")
            INVALID_CONFIG -> throw GradleException("Invalid detekt config ${config.get().asFile}; see above.")
            else -> throw GradleException("detekt failed with exit code ${result.exitValue}; see above.")
        }
    }

    private companion object {
        const val ISSUES_FOUND = 2
        const val INVALID_CONFIG = 3
    }
}
