package dev.kyralabs.pingogram.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.data.attachment.AndroidContentSource
import dev.kyralabs.pingogram.core.data.attachment.AndroidMediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.AttachmentPreparer
import dev.kyralabs.pingogram.core.data.attachment.ContentSource
import dev.kyralabs.pingogram.core.data.attachment.MediaProcessor
import dev.kyralabs.pingogram.core.data.attachment.UploadCleaner
import dev.kyralabs.pingogram.core.data.attachment.UploadFiles
import dev.kyralabs.pingogram.core.data.mapper.toTdFormattedText
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.ShareResult
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.repository.ShareRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ShareRepository] on TDLib: the shared files are prepared once ([AttachmentPreparer], into `UploadFiles`' cache, on
 * [io]), as the composer prepares picked files, and the same copies go to every chat. The first chat's upload must not
 * delete them while the next chats are still to send them, so [cleaner] holds them until every chat was sent to; then
 * they go once their last message uploaded them. A text goes as `inputMessageText` with TDLib's default link preview, a
 * comment before it as a message of its own; files with [sendAttachments], the text as their caption.
 */
@Singleton
class TdlibShareRepository internal constructor(
    private val client: TdlibClient,
    private val files: UploadFiles,
    content: ContentSource,
    media: MediaProcessor,
    private val cleaner: UploadCleaner,
    private val io: CoroutineDispatcher,
) : ShareRepository {
    @Inject
    constructor(
        client: TdlibClient,
        files: UploadFiles,
        cleaner: UploadCleaner,
        @ApplicationContext context: Context,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(client, files, AndroidContentSource(context.contentResolver), AndroidMediaProcessor(), cleaner, io)

    private val preparer = AttachmentPreparer(files, content, media)

    override suspend fun share(
        chatIds: List<Long>,
        content: SharedContent,
        comment: FormattedText,
    ): ShareResult {
        val chats = chatIds.distinct()
        if (chats.isEmpty() || (content.isEmpty && comment.text.isBlank())) return ShareResult(sent = 0, failed = 0)
        return when (val prepared = prepare(content)) {
            is Outcome.Success -> sendHolding(chats, content.text, prepared.value, comment)
            is Outcome.Failure -> ShareResult(sent = 0, failed = chats.size, error = prepared.error)
        }
    }

    /** [sendToEach], holding the copies of [attachments] from [cleaner] meanwhile. */
    private suspend fun sendHolding(
        chats: List<Long>,
        text: FormattedText,
        attachments: List<Attachment>,
        comment: FormattedText,
    ): ShareResult {
        val paths = attachments.flatMap { it.localPaths() }
        cleaner.hold(paths)
        try {
            return sendToEach(chats, text, attachments, comment)
        } finally {
            cleaner.release(paths)
        }
    }

    /** Sends to each of [chats] in turn, until a `FLOOD_WAIT`, which the chats left would wait for the same way. */
    private suspend fun sendToEach(
        chats: List<Long>,
        text: FormattedText,
        attachments: List<Attachment>,
        comment: FormattedText,
    ): ShareResult {
        var sent = 0
        var error: DomainError? = null
        for (chatId in chats) {
            val failure = sendTo(chatId, text, attachments, comment)
            if (failure == null) sent++ else error = error ?: failure.error
            if (failure?.error is DomainError.FloodWait) break
        }
        return ShareResult(sent = sent, failed = chats.size - sent, error = error)
    }

    /** Every file of [content] prepared, in their order; the first that cannot be read fails it, leaving no copies. */
    private suspend fun prepare(content: SharedContent): Outcome<List<Attachment>> {
        val attachments = mutableListOf<Attachment>()
        try {
            for (uri in content.files) {
                when (val prepared = withContext(io) { preparer.prepare(uri, content.kind) }) {
                    is Outcome.Success -> {
                        attachments += prepared.value
                    }

                    is Outcome.Failure -> {
                        discard(attachments)
                        return prepared
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            discard(attachments)
            throw cancelled
        }
        return Outcome.Success(attachments)
    }

    private suspend fun discard(attachments: List<Attachment>) {
        if (attachments.isNotEmpty()) withContext(io) { files.delete(attachments) }
    }

    /** Sends the comment, then the text or the files, to [chatId]; the first refusal, or null once all went. */
    private suspend fun sendTo(
        chatId: Long,
        text: FormattedText,
        attachments: List<Attachment>,
        comment: FormattedText,
    ): Outcome.Failure? {
        if (comment.text.isNotBlank()) {
            (client.sendMessage(chatId, textContent(comment), SendOptions(), replyTo = null) as? Outcome.Failure)
                ?.let { return it }
        }
        return when {
            attachments.isNotEmpty() -> {
                client.sendAttachments(chatId, attachments, text, SendOptions(), replyToMessageId = null).failure
            }

            text.text.isNotBlank() -> {
                client.sendMessage(chatId, textContent(text), SendOptions(), replyTo = null) as? Outcome.Failure
            }

            else -> {
                null
            }
        }
    }

    /** A text message with TDLib's default link preview, leaving the chat's draft alone. */
    private fun textContent(text: FormattedText) = TdApi.InputMessageText(text.toTdFormattedText(), null, false)
}

/** The copies TDLib uploads for this attachment: its file, and a video's still. */
private fun Attachment.localPaths(): List<String> =
    when (this) {
        is Attachment.Video -> listOfNotNull(path, thumbnail?.path)
        is Attachment.Photo, is Attachment.Document -> listOf(path)
    }
