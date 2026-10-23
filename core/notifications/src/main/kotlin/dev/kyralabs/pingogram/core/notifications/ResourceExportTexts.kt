package dev.kyralabs.pingogram.core.notifications

import android.content.Context
import android.text.format.DateFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.repository.ExportLabels
import dev.kyralabs.pingogram.core.domain.repository.ExportTexts
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.contentKindText
import dev.kyralabs.pingogram.core.ui.text.messagePreviewText
import dev.kyralabs.pingogram.core.ui.text.resolve
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [ExportTexts] in the app's language, from [context]'s resources: service messages and what a message is as the chat
 * list words them (`core:ui`'s previews), days in the long localized form, times as the user's clock shows them (12 or
 * 24 hours). The previews' Unicode isolates, which only matter inside the app's own strings, are taken out.
 */
internal class ResourceExportTexts(
    private val context: Context,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ExportTexts {
    private val resources = context.resources
    private val dayFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withZone(zone)
    private val timeFormat = DateFormat.getTimeFormat(context)

    override fun service(
        message: Message,
        name: (MessageSender) -> String?,
    ): String = messagePreviewText(message, null) { sender -> name(sender)?.let(UiText::Plain) }.text()

    override fun summary(content: MessageContent): String? =
        when (content) {
            is MessageContent.Text,
            is MessageContent.Photo,
            is MessageContent.Video,
            is MessageContent.Animation,
            is MessageContent.VoiceNote,
            is MessageContent.VideoNote,
            is MessageContent.Audio,
            is MessageContent.Document,
            -> null

            else -> contentKindText(content).text()
        }

    override fun day(seconds: Long): String = dayFormat.format(Instant.ofEpochSecond(seconds))

    override fun time(seconds: Long): String = timeFormat.format(Date(seconds * MILLIS))

    override val labels: ExportLabels =
        ExportLabels(
            exportedOn = resources.getString(R.string.chat_export_file_exported_on),
            messageCount = { count ->
                resources.getQuantityString(
                    R.plurals.chat_export_file_messages,
                    count,
                    NumberFormat.getIntegerInstance().format(count),
                )
            },
            forwardedFrom = resources.getString(R.string.chat_export_file_forwarded_from),
            inReplyTo = resources.getString(R.string.chat_export_file_in_reply_to),
            replyNotExported = resources.getString(R.string.chat_export_file_reply_not_exported),
            edited = resources.getString(R.string.chat_export_file_edited),
            mediaNotExported = resources.getString(R.string.chat_export_file_not_exported),
            mediaTooLarge = resources.getString(R.string.chat_export_file_too_large),
            mediaSelfDestructing = resources.getString(R.string.chat_export_file_self_destructing),
            mediaUnavailable = resources.getString(R.string.chat_export_file_unavailable),
            noMessages = resources.getString(R.string.chat_export_file_no_messages),
            deletedAccount = resources.getString(UiR.string.user_deleted_account),
            savedMessages = resources.getString(UiR.string.chat_saved_messages),
            mediaName = { type -> resources.getString(type.nameRes()) },
        )

    private fun UiText.text(): String = resolve(resources).filterNot { it.code in ISOLATES }

    private companion object {
        const val MILLIS = 1000L

        /** U+2066–U+2069: the isolates `bidiIsolated` puts around names. */
        val ISOLATES = 0x2066..0x2069
    }
}

private fun ExportMediaType.nameRes(): Int =
    when (this) {
        ExportMediaType.Photo -> UiR.string.preview_photo
        ExportMediaType.Video -> UiR.string.preview_video
        ExportMediaType.Animation -> UiR.string.preview_gif
        ExportMediaType.VideoNote -> UiR.string.preview_video_message
        ExportMediaType.VoiceNote -> UiR.string.preview_voice_message
        ExportMediaType.Audio -> UiR.string.preview_audio
        ExportMediaType.Document -> UiR.string.preview_file
        ExportMediaType.Sticker -> UiR.string.preview_sticker
    }
