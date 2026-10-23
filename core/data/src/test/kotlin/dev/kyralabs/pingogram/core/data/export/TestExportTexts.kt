package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMessage
import dev.kyralabs.pingogram.core.domain.model.ExportSender
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.repository.ExportLabels
import dev.kyralabs.pingogram.core.domain.repository.ExportTexts
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.ZoneOffset

/** Plain English words with UTC dates, as the export's words come from resources in the app. */
internal object TestExportTexts : ExportTexts {
    override fun service(
        message: Message,
        name: (MessageSender) -> String?,
    ): String = "${name(message.sender) ?: "Someone"} did something"

    override fun summary(content: MessageContent): String? =
        when (content) {
            is MessageContent.Location -> "Location"
            is MessageContent.Sticker -> "Sticker ${content.sticker.emoji}"
            else -> null
        }

    override fun day(seconds: Long): String =
        Instant
            .ofEpochSecond(seconds)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .toString()

    override fun time(seconds: Long): String =
        Instant
            .ofEpochSecond(seconds)
            .atZone(ZoneOffset.UTC)
            .toLocalTime()
            .toString()

    override val labels =
        ExportLabels(
            exportedOn = "Exported from Pingogram on %1\$s",
            messageCount = { "$it messages" },
            forwardedFrom = "Forwarded from %1\$s",
            inReplyTo = "In reply to this message",
            replyNotExported = "In reply to a message not in this export",
            edited = "edited",
            mediaNotExported = "%1\$s not exported",
            mediaTooLarge = "%1\$s not exported: larger than the size limit",
            mediaSelfDestructing = "%1\$s not exported: it self-destructs",
            mediaUnavailable = "%1\$s not exported: no longer available",
            noMessages = "No messages",
            deletedAccount = "Deleted Account",
            savedMessages = "Saved Messages",
            mediaName = { it.name },
        )
}

internal val testChat = ExportChat(id = 100, title = "Ada & <Friends>", kind = ExportChatKind.Group)

internal fun text(
    text: String,
    vararg entities: TextEntity,
) = FormattedText(text, entities.toList().toImmutableList())

internal fun exportMessage(
    id: Long,
    date: Long = 1_700_000_000 + id * 60,
    text: FormattedText = FormattedText(text = "Message $id", entities = persistentListOf()),
    sender: ExportSender? = ExportSender(id = 42, isChat = false, name = "Ada"),
    isOutgoing: Boolean = false,
    editDate: Long = 0,
    replyTo: Long? = null,
    forwardedFrom: String? = null,
    media: ExportMedia? = null,
    summary: String? = null,
    service: String? = null,
) = ExportMessage(
    id = id,
    date = date,
    editDate = editDate,
    sender = sender,
    isOutgoing = isOutgoing,
    text = text,
    replyToMessageId = replyTo,
    forwardedFrom = forwardedFrom,
    forwardDate = if (forwardedFrom != null) date - 3_600 else 0,
    media = media,
    summary = summary,
    service = service,
)
