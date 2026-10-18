package dev.kyralabs.pingogram.core.ui.text

import dev.kyralabs.pingogram.core.domain.model.DraftContent
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.R

// One line of text for a message wherever it is shown in short (REQUIREMENTS.md FR-L2, FR-C1): the chat list, reply
// quotes, notifications. Plain text: entities are dropped, line breaks become spaces. As UiText, so ViewModels map
// messages without a Context. Content previews are in ContentPreview.kt, service messages in ServicePreview.kt.

/**
 * The longest message text a preview keeps. Previews show two lines at most; measuring a whole long message for them
 * would cost layout time on every bind (`docs/PERFORMANCE.md` §4).
 */
const val PREVIEW_MAX_LENGTH = 200

/**
 * The preview of [message]: its text or caption, what kind of media it is, or what a service message reports.
 *
 * @param myUserId the current user, whose service messages say "You …"; null if not known.
 * @param name the short name of a sender or a member of a service message ([shortName] for users, the title for
 *   chats); null if not known, which reads "Someone".
 */
fun messagePreviewText(
    message: Message,
    myUserId: Long?,
    name: (MessageSender) -> UiText?,
): UiText =
    when (val content = message.content) {
        is MessageContent.Service -> serviceText(content.action, message, myUserId, name)
        else -> contentPreviewText(content)
    }

/**
 * What kind of message [content] is, in one line, without its caption: "Photo", "Voice message", a file's name, a
 * poll's question; the text of a text message. For a place that the caption follows with its own formatting, such as
 * a bubble. Service messages need their message to name who did what: see [messagePreviewText].
 */
fun contentKindText(content: MessageContent): UiText = contentPreviewText(content.withoutCaption())

/** The preview of an unsent [draft]'s content. */
fun draftPreviewText(draft: DraftContent): UiText =
    when (draft) {
        is DraftContent.Text -> previewOf(draft.text)
        DraftContent.VoiceNote -> UiText.resource(R.string.preview_voice_message)
        DraftContent.VideoNote -> UiText.resource(R.string.preview_video_message)
        DraftContent.Unsupported -> UiText.Plain("")
    }

/**
 * [text] as one line for a preview: at most [PREVIEW_MAX_LENGTH] characters, never cutting an emoji in half, with
 * line breaks turned into spaces.
 */
fun previewOf(text: FormattedText): UiText = UiText.Plain(previewLine(text.text))

/** One line of at most [PREVIEW_MAX_LENGTH] characters, not ending in half an emoji. */
internal fun previewLine(text: String): String {
    var end = minOf(text.length, PREVIEW_MAX_LENGTH)
    if (end < text.length && end > 0 && Character.isHighSurrogate(text[end - 1])) end--
    return LINE_BREAKS.replace(text.substring(0, end), " ").trim()
}

private val LINE_BREAKS = Regex("""\s*[\r\n]+\s*""")

/** [this] content with an empty caption, if it has one. */
private fun MessageContent.withoutCaption(): MessageContent =
    when (this) {
        is MessageContent.Photo -> copy(caption = FormattedText.Empty)
        is MessageContent.Video -> copy(caption = FormattedText.Empty)
        is MessageContent.Animation -> copy(caption = FormattedText.Empty)
        is MessageContent.VoiceNote -> copy(caption = FormattedText.Empty)
        is MessageContent.Audio -> copy(caption = FormattedText.Empty)
        is MessageContent.Document -> copy(caption = FormattedText.Empty)
        else -> this
    }
