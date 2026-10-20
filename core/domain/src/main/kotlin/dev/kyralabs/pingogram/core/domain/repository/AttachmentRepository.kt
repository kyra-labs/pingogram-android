package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.RecordedVoiceNote
import dev.kyralabs.pingogram.core.domain.model.SendOptions

/**
 * What the composer's attach menu sends (`REQUIREMENTS.md` FR-C5): photos, videos and files the user picked, a photo
 * from the camera, a contact and a poll; the voice messages the composer records; and cancelling an upload in progress.
 *
 * Picked files are prepared first ([prepare]): copied into the app's private cache, where they stay readable after the
 * picker's read grant ends, and made ready for TDLib. Each copy then belongs to the caller until it is sent ([send]),
 * which hands it to TDLib, or [discard]ed. Copies TDLib has uploaded, or will never upload, are deleted by themselves.
 *
 * Every send fails with `DomainError.FloodWait`, `DomainError.Network` for a request that gets no answer in time,
 * `DomainError.FileTooLarge`, `DomainError.SendingNotAllowed`, or another `DomainError` when TDLib refuses it.
 */
interface AttachmentRepository {
    /**
     * Copies the file at [uri] (a `content://` URI a picker returned) into the app's cache and prepares it as [kind]
     * says, off the main thread. Fails with `DomainError.FileUnavailable` when it cannot be read; then nothing is left
     * behind, and neither is anything when the caller is cancelled.
     */
    suspend fun prepare(
        uri: String,
        kind: AttachmentKind,
    ): Outcome<Attachment>

    /**
     * A new, empty file in the app's cache for the camera app to write a photo into, by its path. Hand it over as a
     * content URI ([prepareCameraPhoto] after a photo was taken, [discardCameraFile] otherwise).
     */
    suspend fun newCameraFile(): Outcome<String>

    /**
     * The photo the camera app wrote into [path] (from [newCameraFile]) as a photo to send; the camera's file is then
     * deleted. Fails with `DomainError.FileUnavailable` when it holds no photo.
     */
    suspend fun prepareCameraPhoto(path: String): Outcome<Attachment>

    /** Deletes [path], a file from [newCameraFile] that holds no photo to send. Returns at once. */
    fun discardCameraFile(path: String)

    /** Deletes the copies of [attachments], which will not be sent. Returns at once. */
    fun discard(attachments: List<Attachment>)

    /**
     * The name and phone number of the contact at [uri], as the system's contact picker returned it, read while its
     * grant lasts. Fails with `DomainError.FileUnavailable` when it cannot be read or has no phone number.
     */
    suspend fun readContact(uri: String): Outcome<Contact>

    /**
     * Sends [attachments] to chat [chatId] as [options] say, as a reply to message [replyToMessageId] of the chat if
     * it is not null: photos and videos grouped into albums, documents into albums of documents, at most ten
     * messages each, in their order. [caption] goes with the first photo or video, or with the last document. Succeeds
     * once TDLib accepted every message, which it then shows as being sent while it uploads. The copies of attachments
     * that were not sent are deleted; on success none are left to the caller.
     */
    suspend fun send(
        chatId: Long,
        attachments: List<Attachment>,
        caption: FormattedText,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>

    /**
     * A new, empty file in the app's cache for a voice message to be recorded into, by its path, ending in
     * `.`[extension] (the audio format's, which TDLib reads the file's type from). Hand it back to [sendVoiceNote] or
     * [discardVoiceNote].
     */
    suspend fun newVoiceNoteFile(extension: String): Outcome<String>

    /** Deletes [path], a file from [newVoiceNoteFile] that will not be sent. Returns at once. */
    fun discardVoiceNote(path: String)

    /**
     * Sends [voiceNote] to chat [chatId] as [options] say, replying to [replyToMessageId] if it is not null. Succeeds
     * once TDLib accepted the message, which it then shows as being sent while it uploads; its file is then TDLib's to
     * upload and deleted once uploaded. A refused voice note's file is deleted.
     */
    suspend fun sendVoiceNote(
        chatId: Long,
        voiceNote: RecordedVoiceNote,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>

    /** Sends [contact] to chat [chatId] as [options] say, replying to [replyToMessageId] if it is not null. */
    suspend fun sendContact(
        chatId: Long,
        contact: Contact,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>

    /** Sends [poll] to chat [chatId] as [options] say, replying to [replyToMessageId] if it is not null. */
    suspend fun sendPoll(
        chatId: Long,
        poll: NewPoll,
        options: SendOptions = SendOptions(),
        replyToMessageId: Long? = null,
    ): Outcome<Unit>

    /**
     * Stops sending the messages [messageIds] of chat [chatId], which are still being sent, by deleting them: their
     * uploads stop and their copies are deleted.
     */
    suspend fun cancelUpload(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit>
}
