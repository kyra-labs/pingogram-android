package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository

/**
 * The attach menu's repository: it prepares every picked URI as a photo named after it ([onPrepare]), records what is
 * discarded, sent and cancelled, and answers with what tests set.
 */
class FakeAttachmentRepository : AttachmentRepository {
    /** The URI and kind of every [prepare] call, in order. */
    val prepared = mutableListOf<Pair<String, AttachmentKind>>()

    var onPrepare: suspend (String, AttachmentKind) -> Outcome<Attachment> = { uri, _ -> Outcome.Success(photo(uri)) }

    var cameraFile: Outcome<String> = Outcome.Success(CAMERA_FILE)

    /** The path of every [prepareCameraPhoto] call. */
    val preparedCameraPhotos = mutableListOf<String>()

    var onPrepareCameraPhoto: suspend (String) -> Outcome<Attachment> = { Outcome.Success(photo("camera")) }

    val discarded = mutableListOf<Attachment>()
    val discardedCameraFiles = mutableListOf<String>()

    var onReadContact: suspend (String) -> Outcome<Contact> = { Outcome.Failure(DomainError.FileUnavailable) }

    val sent = mutableListOf<SentAttachments>()
    var onSend: suspend (SentAttachments) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The contact and reply of every [sendContact] call. */
    val sentContacts = mutableListOf<Pair<Contact, Long?>>()
    var onSendContact: suspend (Contact) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The location, options and reply of every [sendLocation] call. */
    val sentLocations = mutableListOf<Triple<DeviceLocation, SendOptions, Long?>>()
    var onSendLocation: suspend (DeviceLocation) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The sticker, options and reply of every [sendSticker] call. */
    val sentStickers = mutableListOf<Triple<Sticker, SendOptions, Long?>>()
    var onSendSticker: suspend (Sticker) -> Outcome<Unit> = { Outcome.Success(Unit) }

    val sentPolls = mutableListOf<SentPoll>()
    var onSendPoll: suspend (NewPoll) -> Outcome<Unit> = { Outcome.Success(Unit) }

    /** The message ids of every [cancelUpload] call. */
    val cancelled = mutableListOf<List<Long>>()
    var onCancelUpload: suspend (List<Long>) -> Outcome<Unit> = { Outcome.Success(Unit) }

    override suspend fun prepare(
        uri: String,
        kind: AttachmentKind,
    ): Outcome<Attachment> {
        prepared += uri to kind
        return onPrepare(uri, kind)
    }

    override suspend fun newCameraFile(): Outcome<String> = cameraFile

    override suspend fun prepareCameraPhoto(path: String): Outcome<Attachment> {
        preparedCameraPhotos += path
        return onPrepareCameraPhoto(path)
    }

    override fun discardCameraFile(path: String) {
        discardedCameraFiles += path
    }

    override fun discard(attachments: List<Attachment>) {
        discarded += attachments
    }

    override suspend fun readContact(uri: String): Outcome<Contact> = onReadContact(uri)

    override suspend fun send(
        chatId: Long,
        attachments: List<Attachment>,
        caption: FormattedText,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        val send = SentAttachments(chatId, attachments, caption, options, replyToMessageId)
        sent += send
        return onSend(send)
    }

    override suspend fun sendContact(
        chatId: Long,
        contact: Contact,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sentContacts += contact to replyToMessageId
        return onSendContact(contact)
    }

    override suspend fun sendLocation(
        chatId: Long,
        location: DeviceLocation,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sentLocations += Triple(location, options, replyToMessageId)
        return onSendLocation(location)
    }

    override suspend fun sendSticker(
        chatId: Long,
        sticker: Sticker,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sentStickers += Triple(sticker, options, replyToMessageId)
        return onSendSticker(sticker)
    }

    override suspend fun sendPoll(
        chatId: Long,
        poll: NewPoll,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        sentPolls += SentPoll(poll, options, replyToMessageId)
        return onSendPoll(poll)
    }

    override suspend fun cancelUpload(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit> {
        cancelled += messageIds
        return onCancelUpload(messageIds)
    }

    /** One [send] call. */
    data class SentAttachments(
        val chatId: Long,
        val attachments: List<Attachment>,
        val caption: FormattedText,
        val options: SendOptions,
        val replyToMessageId: Long?,
    )

    /** One [sendPoll] call. */
    data class SentPoll(
        val poll: NewPoll,
        val options: SendOptions,
        val replyToMessageId: Long?,
    )

    companion object {
        const val CAMERA_FILE = "/cache/camera/shot.jpg"

        /** The photo a picked [uri] is prepared as: its copy is named after the URI. */
        fun photo(uri: String) = Attachment.Photo("/cache/uploads/${uri.substringAfterLast('/')}.jpg", 1_000, 800, 600)
    }
}
