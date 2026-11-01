package dev.kyralabs.pingogram.core.demo

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

    data class Sent(
        val chatId: Long,
        val messageId: Long,
        val date: Int,
        val text: String,
        val replyToMessageId: Long,
    ) : DemoEvent

    data class Read(
        val chatId: Long,
        val messageId: Long,
    ) : DemoEvent

    data class Reaction(
        val chatId: Long,
        val messageId: Long,
        val emoji: String,
        val added: Boolean,
    ) : DemoEvent

    data class Vote(
        val chatId: Long,
        val messageId: Long,
        val optionIds: List<Int>,
    ) : DemoEvent

    data class Deleted(
        val chatId: Long,
        val messageIds: List<Long>,
    ) : DemoEvent

    data class Pinned(
        val chatId: Long,
        val pinned: Boolean,
    ) : DemoEvent

    data class MarkedUnread(
        val chatId: Long,
        val unread: Boolean,
    ) : DemoEvent

    data class Muted(
        val chatId: Long,
        val muteFor: Int,
    ) : DemoEvent

    data class Archived(
        val chatId: Long,
        val archived: Boolean,
    ) : DemoEvent
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
            is DemoEvent.Started -> {
                line("started", event.date)
            }

            is DemoEvent.Sent -> {
                line("sent", event.chatId, event.messageId, event.date, base64(event.text), event.replyToMessageId)
            }

            is DemoEvent.Read -> {
                line("read", event.chatId, event.messageId)
            }

            is DemoEvent.Reaction -> {
                line("reaction", event.chatId, event.messageId, base64(event.emoji), event.added)
            }

            is DemoEvent.Vote -> {
                line("vote", event.chatId, event.messageId, event.optionIds.joinToString(","))
            }

            is DemoEvent.Deleted -> {
                line("deleted", event.chatId, event.messageIds.joinToString(","))
            }

            is DemoEvent.Pinned -> {
                line("pinned", event.chatId, event.pinned)
            }

            is DemoEvent.MarkedUnread -> {
                line("unread", event.chatId, event.unread)
            }

            is DemoEvent.Muted -> {
                line("muted", event.chatId, event.muteFor)
            }

            is DemoEvent.Archived -> {
                line("archived", event.chatId, event.archived)
            }
        }

    // One branch per kind of event, the counterpart of encode; the numbers are the fields' positions in its lines.
    @Suppress("CyclomaticComplexMethod", "MagicNumber")
    private fun decode(line: String): DemoEvent? {
        val fields = line.split('\t')
        return runCatching {
            when (fields[0]) {
                "started" -> {
                    DemoEvent.Started(fields[1].toInt())
                }

                "sent" -> {
                    DemoEvent.Sent(
                        fields[1].toLong(),
                        fields[2].toLong(),
                        fields[3].toInt(),
                        text(fields[4]),
                        fields[5].toLong(),
                    )
                }

                "read" -> {
                    DemoEvent.Read(fields[1].toLong(), fields[2].toLong())
                }

                "reaction" -> {
                    DemoEvent.Reaction(
                        fields[1].toLong(),
                        fields[2].toLong(),
                        text(fields[3]),
                        fields[4].toBooleanStrict(),
                    )
                }

                "vote" -> {
                    DemoEvent.Vote(fields[1].toLong(), fields[2].toLong(), numbers(fields[3]).map(String::toInt))
                }

                "deleted" -> {
                    DemoEvent.Deleted(fields[1].toLong(), numbers(fields[2]).map(String::toLong))
                }

                "pinned" -> {
                    DemoEvent.Pinned(fields[1].toLong(), fields[2].toBooleanStrict())
                }

                "unread" -> {
                    DemoEvent.MarkedUnread(fields[1].toLong(), fields[2].toBooleanStrict())
                }

                "muted" -> {
                    DemoEvent.Muted(fields[1].toLong(), fields[2].toInt())
                }

                "archived" -> {
                    DemoEvent.Archived(fields[1].toLong(), fields[2].toBooleanStrict())
                }

                else -> {
                    null
                }
            }
        }.getOrNull()
    }

    private fun line(vararg fields: Any) = fields.joinToString("\t")

    private fun base64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun text(field: String) = String(Base64.getDecoder().decode(field))

    private fun numbers(field: String) = field.split(',').filter(String::isNotEmpty)
}
