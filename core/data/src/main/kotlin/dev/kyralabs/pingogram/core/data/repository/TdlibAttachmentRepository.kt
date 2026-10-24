package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.attachment.AndroidContentSource
import dev.kyralabs.pingogram.core.data.attachment.AndroidMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.AttachmentPreparer
import dev.kyralabs.pingogram.core.data.attachment.ContentSource
import dev.kyralabs.pingogram.core.data.attachment.MediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.mapper.albumGroups
import dev.kyralabs.pingogram.core.data.mapper.captionIndex
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageAnimation
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageContact
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageContent
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageLocation
import dev.kyralabs.pingogram.core.data.mapper.toInputMessagePoll
import dev.kyralabs.pingogram.core.data.mapper.toInputMessageSticker
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.DeviceLocation
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.repository.AttachmentRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [AttachmentRepository] on TDLib and the app's cache ([UploadFiles]). Preparing and reading picked content runs on
 * [io] ([AttachmentPreparer], through the [ContentSource] and [MediaProcessor] seams); sending only sends requests: the
 * messages reach the conversation through TDLib's updates as being sent, as text messages do, and their copies are
 * deleted by `UploadCleaner` once uploaded. Copies a send leaves unsent are deleted here. One function per kind of
 * attachment, as in [AttachmentRepository] (hence `TooManyFunctions`).
 */
@Suppress("TooManyFunctions")
@Singleton
class TdlibAttachmentRepository internal constructor(
    private val client: TdlibClient,
    private val files: UploadFiles,
    private val content: ContentSource,
    media: MediaProcessor,
    private val io: CoroutineDispatcher,
    private val scope: CoroutineScope,
) : AttachmentRepository {
    @Inject
    constructor(
        client: TdlibClient,
        files: UploadFiles,
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
        @ApplicationScope scope: CoroutineScope,
    ) : this(client, files, AndroidContentSource(context.contentResolver), AndroidMediaProcessor(), io, scope)

    private val preparer = AttachmentPreparer(files, content, media)

    override suspend fun prepare(
        uri: String,
        kind: AttachmentKind,
    ): Outcome<Attachment> = withContext(io) { preparer.prepare(uri, kind) }

    override suspend fun newCameraFile(): Outcome<String> =
        withContext(io) {
            try {
                Outcome.Success(files.newCameraFile().path)
            } catch (_: IOException) {
                Outcome.Failure(DomainError.FileUnavailable)
            }
        }

    override suspend fun prepareCameraPhoto(path: String): Outcome<Attachment> =
        withContext(io) {
            val file = files.cameraFile(path) ?: return@withContext Outcome.Failure(DomainError.FileUnavailable)
            try {
                preparer.prepareCameraPhoto(file)
            } finally {
                file.delete()
            }
        }

    override fun discardCameraFile(path: String) {
        scope.launch(io) { files.cameraFile(path)?.delete() }
    }

    override fun discard(attachments: List<Attachment>) {
        if (attachments.isEmpty()) return
        scope.launch(io) { files.delete(attachments) }
    }

    override suspend fun readContact(uri: String): Outcome<Contact> =
        withContext(io) {
            val picked = content.phoneContact(uri)?.takeIf { it.phoneNumber.isNotBlank() }
            if (picked == null) {
                Outcome.Failure(DomainError.FileUnavailable)
            } else {
                // td_api.tl `contact`: a first name of 1–64 characters; the number stands in for a contact without one.
                val name = picked.displayName.trim().ifEmpty { picked.phoneNumber.trim() }
                Outcome.Success(Contact(picked.phoneNumber.trim(), name.take(MAX_NAME_LENGTH), "", 0))
            }
        }

    /**
     * [sendAttachments]; the copies of the attachments TDLib did not accept are deleted, as nothing will send them.
     */
    override suspend fun send(
        chatId: Long,
        attachments: List<Attachment>,
        caption: FormattedText,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> {
        val result = client.sendAttachments(chatId, attachments, caption, options, replyToMessageId)
        result.failure?.let { failure ->
            discard(attachments.drop(result.sent))
            return failure
        }
        return Outcome.Success(Unit)
    }

    override suspend fun sendContact(
        chatId: Long,
        contact: Contact,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> =
        client.sendMessage(chatId, contact.toInputMessageContact(), options, replyToMessageId?.let(::replyToMessage))

    override suspend fun sendLocation(
        chatId: Long,
        location: DeviceLocation,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> =
        client.sendMessage(chatId, location.toInputMessageLocation(), options, replyToMessageId?.let(::replyToMessage))

    override suspend fun sendSticker(
        chatId: Long,
        sticker: Sticker,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> =
        client.sendMessage(chatId, sticker.toInputMessageSticker(), options, replyToMessageId?.let(::replyToMessage))

    override suspend fun sendAnimation(
        chatId: Long,
        animation: Animation,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> =
        client.sendMessage(
            chatId,
            animation.toInputMessageAnimation(),
            options,
            replyToMessageId?.let(::replyToMessage),
        )

    override suspend fun sendPoll(
        chatId: Long,
        poll: NewPoll,
        options: SendOptions,
        replyToMessageId: Long?,
    ): Outcome<Unit> =
        client.sendMessage(chatId, poll.toInputMessagePoll(), options, replyToMessageId?.let(::replyToMessage))

    /**
     * `deleteMessages` for everyone, which for messages still being sent stops their uploads; TDLib deletes them, and
     * `UploadCleaner` their copies.
     */
    override suspend fun cancelUpload(
        chatId: Long,
        messageIds: List<Long>,
    ): Outcome<Unit> = client.request(TdApi.DeleteMessages(chatId, messageIds.toLongArray(), true)).map {}

    private companion object {
        /** td_api.tl `contact.first_name`: 1–64 characters. */
        const val MAX_NAME_LENGTH = 64
    }
}

/**
 * Sends [attachments] to chat [chatId]: `sendMessage` for a group of one and `sendMessageAlbum` for the others
 * ([albumGroups]), one after the other in their order, with [options] (into their topic, if any) and the caption on
 * [captionIndex]'s attachment;
 * only the first group replies to [replyToMessageId]. The first group TDLib refuses ends the send with its error. The
 * copies are left as they are: those sent are TDLib's now (`UploadCleaner`), the others still the caller's.
 */
internal suspend fun TdlibClient.sendAttachments(
    chatId: Long,
    attachments: List<Attachment>,
    caption: FormattedText,
    options: SendOptions,
    replyToMessageId: Long?,
): AttachmentSend {
    val captioned = captionIndex(attachments)
    val tdCaption = caption.takeIf { it.text.isNotBlank() }?.toTdFormattedText()
    var sent = 0
    for ((index, group) in albumGroups(attachments).withIndex()) {
        val contents =
            group.mapIndexed { offset, attachment ->
                attachment.toInputMessageContent(tdCaption.takeIf { sent + offset == captioned })
            }
        val replyTo = replyToMessageId?.takeIf { index == 0 }?.let(::replyToMessage)
        val result =
            if (contents.size == 1) {
                sendMessage(chatId, contents[0], options, replyTo)
            } else {
                val all = contents.toTypedArray()
                request(TdApi.SendMessageAlbum(chatId, options.tdTopic(), replyTo, options.toTdOptions(), all))
            }
        if (result is Outcome.Failure) return AttachmentSend(sent, result)
        sent += group.size
    }
    return AttachmentSend(sent, failure = null)
}

/** What [sendAttachments] did: [sent] attachments TDLib accepted, in their order, then [failure], if any refused. */
internal class AttachmentSend(
    val sent: Int,
    val failure: Outcome.Failure?,
)

/** `sendMessage` of [content] to [chatId] as [options] say (into their topic, if any), replying as [replyTo] says. */
internal suspend fun TdlibClient.sendMessage(
    chatId: Long,
    content: TdApi.InputMessageContent,
    options: SendOptions,
    replyTo: TdApi.InputMessageReplyTo?,
): Outcome<Unit> =
    request(TdApi.SendMessage(chatId, options.tdTopic(), replyTo, options.toTdOptions(), null, content)).map {}
