package dev.kyralabs.pingogram.core.demo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import java.io.File
import javax.inject.Inject

/**
 * Makes the demo's client and keeps what the demo leaves on the phone (`docs/adr/0034-demo-mode-for-store-review.md`):
 * the marker that says the demo is on (`noBackupFilesDir/demo`) and the demo's folder (`filesDir/demo`: its journal
 * and its files), both deleted when the demo ends. Nothing here creates a TDLib instance.
 */
class DemoClients internal constructor(
    private val marker: () -> File,
    private val folder: () -> File,
    private val text: (Int) -> String,
    private val io: CoroutineDispatcher,
    private val fileWriter: DemoFileWriter,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(
        marker = { context.noBackupFilesDir.resolve(MARKER) },
        folder = { context.filesDir.resolve(FOLDER) },
        text = context::getString,
        io = io,
        fileWriter = AndroidDemoFileWriter,
    )

    /** The demo's client, running in [scope]; it reads what the user did in the demo before when it starts. */
    fun client(scope: CoroutineScope): DemoTdlibClient =
        DemoTdlibClient(
            text = text,
            journal = DemoJournal(FileDemoJournalStorage(folder().resolve(JOURNAL))),
            directory = folder().resolve(FILES),
            fileWriter = fileWriter,
            scope = scope,
            io = io,
            clock = { (System.currentTimeMillis() / MILLIS).toInt() },
        )

    /** Whether the demo is on: it was entered and not left. Blocking: off the main thread. */
    fun isOn(): Boolean = marker().exists()

    /** Turns the demo on. Blocking: off the main thread. */
    fun turnOn() {
        marker().apply { parentFile?.mkdirs() }.createNewFile()
    }

    /** Turns the demo off and deletes everything it made. Blocking: off the main thread, with its client stopped. */
    fun turnOffAndDelete() {
        folder().deleteRecursively()
        marker().delete()
    }

    private companion object {
        const val MARKER = "demo"
        const val FOLDER = "demo"
        const val JOURNAL = "journal.txt"
        const val FILES = "files"
        const val MILLIS = 1_000L
    }
}
