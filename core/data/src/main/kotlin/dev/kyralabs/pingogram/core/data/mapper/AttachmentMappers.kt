package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import org.drinkless.tdlib.TdApi

// What the attach menu sends, as TDLib's input message contents (td_api.tl of the pinned TDLib, 1.8.68).

/**
 * The content that sends this attachment from its local copy, with [caption] (null: none):
 *
 * - `inputMessagePhoto` of an `inputPhoto` with its size, no thumbnail (TDLib makes one; it is sent only to secret
 *   chats), no live photo video, no stickers, no spoiler, the caption below.
 * - `inputMessageVideo` of an `inputVideo` with its thumbnail, length and size, streamable, no cover, from the start.
 * - `inputMessageDocument` of an `inputDocument` without thumbnail, with content type detection, so Telegram shows
 *   a GIF or a song as such; its name is the copy's file name.
 */
internal fun Attachment.toInputMessageContent(caption: TdApi.FormattedText?): TdApi.InputMessageContent =
    when (this) {
        is Attachment.Photo -> {
            val photo = TdApi.InputPhoto(TdApi.InputFileLocal(path), null, null, intArrayOf(), width, height)
            TdApi.InputMessagePhoto(photo, caption, false, null, false)
        }

        is Attachment.Video -> {
            val thumbnail = thumbnail?.let { TdApi.InputThumbnail(TdApi.InputFileLocal(it.path), it.width, it.height) }
            val video =
                TdApi.InputVideo(
                    TdApi.InputFileLocal(path),
                    thumbnail,
                    null,
                    0,
                    intArrayOf(),
                    duration,
                    width,
                    height,
                    true,
                )
            TdApi.InputMessageVideo(video, caption, false, null, false)
        }

        is Attachment.Document -> {
            TdApi.InputMessageDocument(TdApi.InputDocument(TdApi.InputFileLocal(path), null, false), caption)
        }
    }

/**
 * `inputMessageVoiceNote` of an `inputVoiceNote` from the recorded file, with its length and waveform, without caption,
 * not self-destructing. TDLib takes the file's type from its extension (`.ogg`, `.m4a`).
 */
internal fun RecordedVoiceNote.toInputMessageVoiceNote(): TdApi.InputMessageVoiceNote =
    TdApi.InputMessageVoiceNote(TdApi.InputVoiceNote(TdApi.InputFileLocal(path), duration, waveform.bytes), null, null)

/**
 * [attachments] in the messages they are sent as, in their order: runs of photos and videos, and runs of documents
 * (td_api.tl `sendMessageAlbum`: documents group only with documents), split into albums of at most
 * [MAX_ALBUM_SIZE]. A group of one is a message of its own.
 */
internal fun albumGroups(attachments: List<Attachment>): List<List<Attachment>> {
    val runs = mutableListOf<MutableList<Attachment>>()
    for (attachment in attachments) {
        val run = runs.lastOrNull()
        if (run != null && run.first().isDocument == attachment.isDocument) {
            run += attachment
        } else {
            runs += mutableListOf(attachment)
        }
    }
    return runs.flatMap { it.chunked(MAX_ALBUM_SIZE) }
}

/** The attachment of [attachments] that carries the caption: the first photo or video, else the last document. */
internal fun captionIndex(attachments: List<Attachment>): Int =
    attachments.indexOfFirst { !it.isDocument }.takeIf { it >= 0 } ?: attachments.lastIndex

private val Attachment.isDocument: Boolean get() = this is Attachment.Document

/** The most messages one album holds (td_api.tl `sendMessageAlbum`). */
internal const val MAX_ALBUM_SIZE = 10

/** `inputMessageContact` of this contact, with no vCard. */
internal fun Contact.toInputMessageContact(): TdApi.InputMessageContact =
    TdApi.InputMessageContact(TdApi.Contact(phoneNumber, firstName, lastName, "", userId))

/**
 * `inputMessagePoll` of this poll: plain question and options (only Premium users may add custom emoji), no
 * description or media, open until closed by hand. A regular poll lets voters change their vote and adds no options
 * later; a quiz has one correct option and no explanation, and votes in it are final.
 */
internal fun NewPoll.toInputMessagePoll(): TdApi.InputMessagePoll {
    val correct = correctOptionIndex
    val type =
        if (correct != null) {
            TdApi.InputPollTypeQuiz(intArrayOf(correct), TdApi.FormattedText("", emptyArray()), null)
        } else {
            TdApi.InputPollTypeRegular(false)
        }
    return TdApi.InputMessagePoll(
        plainText(question),
        options.map { TdApi.InputPollOption(plainText(it), null) }.toTypedArray(),
        null,
        null,
        isAnonymous,
        allowsMultipleAnswers && correct == null,
        correct == null,
        false,
        emptyArray(),
        false,
        false,
        type,
        0,
        0,
        false,
    )
}

private fun plainText(text: String) = TdApi.FormattedText(text, emptyArray())
