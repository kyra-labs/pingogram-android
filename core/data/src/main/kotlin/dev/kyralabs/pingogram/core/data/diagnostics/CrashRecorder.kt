package dev.kyralabs.pingogram.core.data.diagnostics

import android.content.Context
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

/**
 * Keeps the last crash on the device for "Send feedback" (`docs/TESTING_GUIDE.md`): the default uncaught exception
 * handler writes the thread, the time and the exception with its causes (classes, redacted messages, stack frames) to
 * [file], then hands the crash on to the handler before it, so Android still ends the process as it would have. Nothing
 * is sent anywhere (`CLAUDE.md` rule 10). `PingogramApplication` makes one and calls [install] before anything else
 * runs, Hilt's injection included, so a crash while the app starts is kept too.
 */
class CrashRecorder internal constructor(
    private val file: File,
    private val now: () -> Long,
) {
    constructor(context: Context) : this(crashFile(context), System::currentTimeMillis)

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            record(thread.name, throwable)
            previous?.uncaughtException(thread, throwable)
        }
    }

    // Whatever goes wrong while writing must not hide the crash itself from the handler after this one.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    internal fun record(
        thread: String,
        throwable: Throwable,
    ) {
        try {
            file.parentFile?.mkdirs()
            file.writeText("$TIME${now()}\n$THREAD$thread\n${trace(throwable)}")
        } catch (e: Throwable) {
            // Nothing else to do while the process is going down.
        }
    }

    internal companion object {
        const val TIME = "time="
        const val THREAD = "thread="
        private const val MAX_CAUSES = 8
        private const val MAX_FRAMES = 40
        private const val MAX_MESSAGE = 300

        fun crashFile(context: Context): File = context.filesDir.resolve("diagnostics/last_crash.txt")

        /** The exception and its causes, innermost last, as `printStackTrace` would, with redacted messages. */
        fun trace(throwable: Throwable): String =
            buildString {
                val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
                var current: Throwable? = throwable
                while (current != null && seen.size < MAX_CAUSES && seen.add(current)) {
                    if (seen.size > 1) append("Caused by: ")
                    append(current.javaClass.name)
                    current.message?.let { append(": ").append(Redaction.redact(it).take(MAX_MESSAGE)) }
                    append('\n')
                    val frames = current.stackTrace
                    frames.take(MAX_FRAMES).forEach { append("\tat ").append(it).append('\n') }
                    if (frames.size > MAX_FRAMES) append("\t… ${frames.size - MAX_FRAMES} more\n")
                    current = current.cause
                }
            }
    }
}
