package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent

/** What the composer does besides writing a new message (`REQUIREMENTS.md` FR-C5, FR-C6). */
internal sealed interface ComposerMode {
    /** The message the composer's bar shows; null for a new message. */
    val messageId: Long? get() = null

    /** The message the next message replies to; null if none. */
    val replyToMessageId: Long? get() = null

    /** A new message, replying to nothing. */
    data object Writing : ComposerMode

    /** A new message replying to message [messageId] of the chat. */
    data class Replying(
        override val messageId: Long,
    ) : ComposerMode {
        override val replyToMessageId: Long get() = messageId
    }

    /** The text of message [messageId], being edited; [stash] is what the composer held before, given back after. */
    data class Editing(
        override val messageId: Long,
        val stash: ComposerStash,
    ) : ComposerMode
}

/** What the composer held when an edit started: its text, and the message it replied to. */
internal data class ComposerStash(
    val text: FormattedText,
    val replyToMessageId: Long?,
) {
    val isEmpty: Boolean get() = text.text.isEmpty() && replyToMessageId == null
}

/** Where an edit of a message goes: its text, or the caption of its media, above or below the media. */
internal sealed interface EditTarget {
    data object Text : EditTarget

    data class Caption(
        val showAboveMedia: Boolean,
    ) : EditTarget
}

/** What editing this message changes: the text of a text message, the caption of media; null for anything else. */
internal val Message.editTarget: EditTarget?
    get() =
        when (val content = content) {
            is MessageContent.Text -> {
                EditTarget.Text
            }

            is MessageContent.Photo -> {
                EditTarget.Caption(content.showCaptionAboveMedia)
            }

            is MessageContent.Video -> {
                EditTarget.Caption(content.showCaptionAboveMedia)
            }

            is MessageContent.Animation -> {
                EditTarget.Caption(content.showCaptionAboveMedia)
            }

            is MessageContent.Audio, is MessageContent.Document, is MessageContent.VoiceNote -> {
                EditTarget.Caption(
                    false,
                )
            }

            else -> {
                null
            }
        }

/** The text of a text message or the caption of media, as sent; empty for other messages. */
internal val Message.textOrCaption: FormattedText
    get() =
        when (val content = content) {
            is MessageContent.Text -> content.text
            is MessageContent.Photo -> content.caption
            is MessageContent.Video -> content.caption
            is MessageContent.Animation -> content.caption
            is MessageContent.Audio -> content.caption
            is MessageContent.Document -> content.caption
            is MessageContent.VoiceNote -> content.caption
            else -> FormattedText.Empty
        }

/**
 * The text an edit of this message starts from: its text or caption with the formatting a sender may set. What TDLib
 * finds in the text itself (links, mentions, hashtags) it finds again in the edit.
 */
internal val Message.editableText: FormattedText get() = textOrCaption.withSenderFormatting()

/** Keeps [mode] in [this], for process death. */
internal fun SavedStateHandle.saveMode(mode: ComposerMode) {
    val editing = mode as? ComposerMode.Editing
    this[KEY_REPLY_TO] = mode.replyToMessageId
    this[KEY_EDITING] = editing?.messageId
    this[KEY_STASH_TEXT] = editing?.stash?.text?.text
    this[KEY_STASH_ENTITIES] = editing?.stash?.text?.savedEntities()
    this[KEY_STASH_REPLY_TO] = editing?.stash?.replyToMessageId
}

/** The mode [saveMode] kept; [ComposerMode.Writing] if none. */
internal fun SavedStateHandle.restoredMode(): ComposerMode {
    val editing = get<Long>(KEY_EDITING)
    val replyTo = get<Long>(KEY_REPLY_TO)
    return when {
        editing != null -> {
            val text = restoredText(get(KEY_STASH_TEXT), get(KEY_STASH_ENTITIES)) ?: FormattedText.Empty
            ComposerMode.Editing(editing, ComposerStash(text, get(KEY_STASH_REPLY_TO)))
        }

        replyTo != null -> {
            ComposerMode.Replying(replyTo)
        }

        else -> {
            ComposerMode.Writing
        }
    }
}

/** The message the composer replies to, for process death. */
internal const val KEY_REPLY_TO = "chat.composerReplyTo"

/** The message being edited, for process death. */
internal const val KEY_EDITING = "chat.composerEditing"

/** The composer's text before the edit started, its formatting and its reply, for process death. */
private const val KEY_STASH_TEXT = "chat.composerStashText"
private const val KEY_STASH_ENTITIES = "chat.composerStashEntities"
private const val KEY_STASH_REPLY_TO = "chat.composerStashReplyTo"
