package dev.kyralabs.pingogram.feature.media.storypost

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository

/**
 * The picking screen's repository: it prepares every URI as a photo at the URI's own path ([onPrepare]), makes camera
 * files, and records what is discarded. Nothing is ever sent from the picking screen, so every send fails.
 */
internal class FakeAttachmentRepository : AttachmentRepository {
    val prepared = mutableListOf<Pair<String, AttachmentKind>>()
    var onPrepare: suspend (String) -> Outcome<Attachment> = { uri -> Outcome.Success(photo(uri)) }
    var cameraFile: Outcome<String> = Outcome.Success(CAMERA_FILE)
    var onPrepareCameraPhoto: suspend (String) -> Outcome<Attachment> = { Outcome.Success(photo("camera.jpg")) }
    val discarded = mutableListOf<Attachment>()
    val discardedCameraFiles = mutableListOf<String>()

    override suspend fun prepare(
        uri: String,
        kind: AttachmentKind,
    ): Outcome<Attachment> {
        prepared += uri to kind
        return onPrepare(uri)
    }

    override suspend fun newCameraFile(): Outcome<String> = cameraFile

    override suspend fun prepareCameraPhoto(path: String): Outcome<Attachment> = onPrepareCameraPhoto(path)

    override fun discardCameraFile(path: String) {
        discardedCameraFiles += path
    }

    override fun discard(attachments: List<Attachment>) {
        discarded += attachments
    }

    override suspend fun readContact(uri: String): Outcome<Contact> = notHere()

    override suspend fun send(
        chatId: Long,
        attachments: List<Attachment>,
        caption: FormattedText,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = notHere()

    override suspend fun sendContact(
        chatId: Long,
        contact: Contact,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = notHere()

    override suspend fun sendLocation(
        chatId: Long,
        location: DeviceLocation,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = notHere()

    override suspend fun sendSticker(
        chatId: Long,
        sticker: Sticker,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = notHere()

    override suspend fun sendChecklist(
        chatId: Long,
        checklist: NewChecklist,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = notHere()

    override suspend fun sendGreetingSticker(
        chatId: Long,
        replyTo: ReplyTarget,
        options: SendOptions,
    ): Outcome<Unit> = notHere()

    override suspend fun sendAnimation(
        chatId: Long,
        animation: Animation,
        options: SendOptions,
        replyTo: ReplyTarget?,
        caption: FormattedText?,
        captionAbove: Boolean,
    ): Outcome<Unit> = notHere()

    override suspend fun sendPoll(
        chatId: Long,
        poll: NewPoll,
        options: SendOptions,
        replyTo: ReplyTarget?,
    ): Outcome<Unit> = notHere()

    override suspend fun editMedia(
        chatId: Long,
        messageId: Long,
        attachment: Attachment,
        caption: FormattedText,
        showCaptionAboveMedia: Boolean,
    ): Outcome<Unit> = notHere()

    override suspend fun cancelUpload(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit> = notHere()

    private fun <T> notHere(): Outcome<T> = Outcome.Failure(DomainError.Unknown(0, "not used by the picking screen"))

    companion object {
        const val CAMERA_FILE = "camera/1.jpg"

        fun photo(path: String) = Attachment.Photo(path, size = 1_000, width = 1080, height = 1920)

        fun video(path: String) =
            Attachment.Video(path, size = 5_000, width = 1080, height = 1920, duration = 14, thumbnail = null)
    }
}
