package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.Sticker
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
 * `inputMessageLocation` of this location, a point (TDLib sends live locations as `inputMessageLiveLocation`): its
 * accuracy within td_api.tl's 0–1500 m, 0 where Android gave none, as Telegram reads 0 as unknown. An approximate
 * location (about 2 km on Android 12 and later) goes as accurate to 1.5 km, the most Telegram takes.
 */
internal fun DeviceLocation.toInputMessageLocation(): TdApi.InputMessageLocation {
    val accuracy = accuracyMeters?.toDouble()?.coerceIn(0.0, MAX_HORIZONTAL_ACCURACY) ?: 0.0
    return TdApi.InputMessageLocation(TdApi.Location(location.latitude, location.longitude, accuracy))
}

/** td_api.tl `location.horizontal_accuracy`: 0–1500 m. */
private const val MAX_HORIZONTAL_ACCURACY = 1500.0

/**
 * `inputMessageSticker` of a sticker TDLib already knows (from a sticker set, the recent or the favorite ones): its
 * file by id, which TDLib sends as the existing remote file without uploading anything, its size and its emoji, so the
 * chat list's preview shows "😀 Sticker". No thumbnail: the remote file has its own.
 */
internal fun Sticker.toInputMessageSticker(): TdApi.InputMessageSticker =
    TdApi.InputMessageSticker(TdApi.InputSticker(TdApi.InputFileId(file.id), null, width, height), emoji)

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
