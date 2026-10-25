package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.TextQuote

/** What the composer does besides writing a new message (`REQUIREMENTS.md` FR-C5, FR-C6). */
internal sealed interface ComposerMode {
    /** The message the composer's bar shows; null for a new message. */
    val messageId: Long? get() = null

    /** What the next message replies to: a message of this chat or of another one, with its quote; null if none. */
    val replyTo: ReplyTarget? get() = null

    /** The message of this chat the next message replies to; null if none, or one of another chat. */
    val replyToMessageId: Long? get() = replyTo?.takeIf { it.chatId == null }?.messageId

    /** A new message, replying to nothing. */
    data object Writing : ComposerMode

    /**
     * A new message replying as [target] says: to a message of the chat, or of another chat ("Reply in another chat"),
     * with the part of its text it quotes (a quote reply).
     */
    data class Replying(
        val target: ReplyTarget,
    ) : ComposerMode {
        /** Replying to the whole message [messageId] of the chat. */
        constructor(messageId: Long) : this(ReplyTarget(messageId))

        override val messageId: Long get() = target.messageId

        override val replyTo: ReplyTarget get() = target
    }

    /**
     * The text of message [messageId], being edited; [stash] is what the composer held before, given back after.
     * [replace] is the picker its media can be replaced from (`REQUIREMENTS.md` FR-C6, td_api.tl `editMessageMedia`),
     * null where TDLib does not let it be replaced.
     */
    data class Editing(
        override val messageId: Long,
        val stash: ComposerStash,
        val replace: ReplaceMedia? = null,
    ) : ComposerMode
}

/**
 * Where the media of a message being edited is replaced from: the Photo Picker for a photo, a video or a GIF, the file
 * picker for a file or a song (TDLib keeps an album's items of one kind: a photo or video for a photo or video, a file
 * for a file).
 */
enum class ReplaceMedia {
    Media,
    Document,
}

/** What the composer held when an edit started: its text, and what it replied to. */
internal data class ComposerStash(
    val text: FormattedText,
    val replyTo: ReplyTarget?,
) {
    val isEmpty: Boolean get() = text.text.isEmpty() && replyTo == null
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

/**
 * Where [this] message's media can be replaced from while it is edited, if TDLib allows it ([canEditMedia]): photos,
 * videos and GIFs from the Photo Picker, files and songs from the file picker; null for anything else.
 */
internal fun Message.replaceMedia(canEditMedia: Boolean): ReplaceMedia? =
    if (!canEditMedia) {
        null
    } else {
        when (content) {
            is MessageContent.Photo, is MessageContent.Video, is MessageContent.Animation -> ReplaceMedia.Media
            is MessageContent.Document, is MessageContent.Audio -> ReplaceMedia.Document
            else -> null
        }
    }

/** Keeps [mode] in [this], for process death. */
internal fun SavedStateHandle.saveMode(mode: ComposerMode) {
    val editing = mode as? ComposerMode.Editing
    saveReply(KEY_REPLY, mode.replyTo)
    this[KEY_EDITING] = editing?.messageId
    this[KEY_EDIT_REPLACE] = editing?.replace?.name
    this[KEY_STASH_TEXT] = editing?.stash?.text?.text
    this[KEY_STASH_ENTITIES] = editing?.stash?.text?.savedEntities()
    saveReply(KEY_STASH_REPLY, editing?.stash?.replyTo)
}

/** The mode [saveMode] kept; [ComposerMode.Writing] if none. */
internal fun SavedStateHandle.restoredMode(): ComposerMode {
    val editing = get<Long>(KEY_EDITING)
    val replyTo = restoredReply(KEY_REPLY)
    return when {
        editing != null -> {
            val text = restoredText(get(KEY_STASH_TEXT), get(KEY_STASH_ENTITIES)) ?: FormattedText.Empty
            val replace = get<String>(KEY_EDIT_REPLACE)?.let { name -> ReplaceMedia.entries.find { it.name == name } }
            ComposerMode.Editing(editing, ComposerStash(text, restoredReply(KEY_STASH_REPLY)), replace)
        }

        replyTo != null -> {
            ComposerMode.Replying(replyTo)
        }

        else -> {
            ComposerMode.Writing
        }
    }
}

/** Keeps [reply] under [prefix]: its message, its chat if another, and its quote with its formatting and position. */
private fun SavedStateHandle.saveReply(
    prefix: String,
    reply: ReplyTarget?,
) {
    this[prefix] = reply?.messageId
    this["$prefix.chat"] = reply?.chatId
    this["$prefix.quote"] = reply?.quote?.text?.text
    this["$prefix.quoteEntities"] = reply?.quote?.text?.savedEntities()
    this["$prefix.quotePosition"] = reply?.quote?.position
}

/** The reply [saveReply] kept under [prefix]; null if none. */
private fun SavedStateHandle.restoredReply(prefix: String): ReplyTarget? {
    val messageId = get<Long>(prefix) ?: return null
    val quote =
        restoredText(get("$prefix.quote"), get("$prefix.quoteEntities"))?.let { text ->
            TextQuote(text, get<Int>("$prefix.quotePosition") ?: 0)
        }
    return ReplyTarget(messageId, quote, get<Long>("$prefix.chat"))
}

/** What the composer replies to, for process death: the message, under this key and the keys it starts. */
internal const val KEY_REPLY = "chat.composerReplyTo"

/** The message being edited, for process death. */
internal const val KEY_EDITING = "chat.composerEditing"

/** The picker the edited message's media can be replaced from ([ReplaceMedia]), for process death. */
private const val KEY_EDIT_REPLACE = "chat.composerEditReplace"

/** The composer's text before the edit started, its formatting and its reply, for process death. */
private const val KEY_STASH_TEXT = "chat.composerStashText"
private const val KEY_STASH_ENTITIES = "chat.composerStashEntities"
private const val KEY_STASH_REPLY = "chat.composerStashReplyTo"
