package dev.kyralabs.pingogram.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Writes a root `robolectric.properties` that sets the SDK Robolectric runs unit tests on by default. */
@DisableCachingByDefault(because = "Writes a one-line file")
abstract class RobolectricConfigTask : DefaultTask() {
    @get:Input
    abstract val sdk: Property<Int>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun write() {
        outputDirectory
            .file("robolectric.properties")
            .get()
            .asFile
            .writeText("sdk=${sdk.get()}\n")
    }
}
