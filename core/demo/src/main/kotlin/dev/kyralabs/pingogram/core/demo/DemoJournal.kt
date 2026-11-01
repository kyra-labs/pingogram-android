package dev.kyralabs.pingogram.core.demo

import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.Base64

/**
 * What the user did in the demo, so a process death keeps it (`docs/adr/0034-demo-mode-for-store-review.md`): one
 * event per line of a small file in the demo's folder, read once when the demo client starts and replayed onto the
 * sample data. Leaving the demo deletes the folder, and with it everything the demo made.
 */
internal sealed interface DemoEvent {
    /** When the demo first started (Unix seconds): the sample messages are dated before it. */
    data class Started(
        val date: Int,
    ) : DemoEvent

    /**
     * A TDLib function the demo took as a change (a sent message, a reaction, a renamed folder…), at [date]: replayed
     * through the same code, it changes the sample data as it did, with the same ids. The files it names are the
     * demo's own copies, kept in its folder.
     */
    class Call(
        val date: Int,
        val function: TdApi.Function<*>,
    ) : DemoEvent {
        override fun equals(other: Any?): Boolean =
            other is Call && other.date == date &&
                DemoCodec.encode(other.function).contentEquals(DemoCodec.encode(function))

        override fun hashCode(): Int = 31 * date + DemoCodec.encode(function).contentHashCode()

        override fun toString(): String = "Call($date, ${function.javaClass.simpleName})"
    }
}

/** Where the journal's lines live: a file in the app, or memory in tests. Blocking: off the main thread. */
internal interface DemoJournalStorage {
    fun read(): List<String>

    fun append(line: String)
}

internal class FileDemoJournalStorage(
    private val file: File,
) : DemoJournalStorage {
    override fun read(): List<String> = if (file.isFile) file.readLines() else emptyList()

    override fun append(line: String) {
        file.parentFile?.mkdirs()
        file.appendText(line + "\n")
    }
}

/** The journal's events in its line format; lines it cannot read (a file cut short by a crash) are left out. */
internal class DemoJournal(
    private val storage: DemoJournalStorage,
) {
    fun read(): List<DemoEvent> = storage.read().mapNotNull(::decode)

    fun append(event: DemoEvent) = storage.append(encode(event))

    private fun encode(event: DemoEvent): String =
        when (event) {
            is DemoEvent.Started -> line(STARTED, event.date)
            is DemoEvent.Call -> line(CALL, event.date, base64.encodeToString(DemoCodec.encode(event.function)))
        }

    private fun decode(line: String): DemoEvent? {
        val fields = line.split('\t')
        return runCatching {
            when (fields[0]) {
                STARTED -> DemoEvent.Started(fields[1].toInt())
                CALL -> DemoEvent.Call(fields[1].toInt(), DemoCodec.decode(Base64.getDecoder().decode(fields[2])))
                else -> null
            }
        }.getOrNull()
    }

    private fun line(vararg fields: Any) = fields.joinToString("\t")

    private companion object {
        const val STARTED = "started"
        const val CALL = "call"
        val base64: Base64.Encoder = Base64.getEncoder()
    }
}
