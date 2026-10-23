package dev.kyralabs.pingogram.core.data.export

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.kyralabs.pingogram.core.common.Dispatcher
import dev.kyralabs.pingogram.core.common.PingogramDispatchers.IO
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportChat
import dev.kyralabs.pingogram.core.domain.model.ExportChatKind
import dev.kyralabs.pingogram.core.domain.model.ExportMedia
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import dev.kyralabs.pingogram.core.domain.model.ExportMediaType
import dev.kyralabs.pingogram.core.domain.model.ExportMessage
import dev.kyralabs.pingogram.core.domain.model.ExportSender
import dev.kyralabs.pingogram.core.domain.model.ExportSkipReason
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.ServiceAction
import dev.kyralabs.pingogram.core.domain.repository.ChatExportAccess
import dev.kyralabs.pingogram.core.domain.repository.ChatExportOutcome
import dev.kyralabs.pingogram.core.domain.repository.ChatExportRepository
import dev.kyralabs.pingogram.core.domain.repository.ChatExporter
import dev.kyralabs.pingogram.core.domain.repository.ExportTexts
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * [ChatExporter] into a folder of the Storage Access Framework ([DocumentTree]), `docs/adr/0021-chat-export.md`:
 *
 * 1. It reads the chat ([ChatExportRepository.chat]); secret chats and chats with protected content are refused.
 * 2. It makes the export's folder in the one picked, "<chat> – <date>", with a `media` folder once a file is copied.
 * 3. It reads the history a page at a time from the newest message back, down to [ChatExportOptions.since], maps each
 *    message to an [ExportMessage] (names, service texts and summaries in words), copies its file into `media/` where
 *    the user chose its kind and it is within the size limit, one at a time, and writes the page to a temporary file
 *    in the app's cache ([writePage]).
 * 4. It writes `messages.html` or `messages.json` from the last page to the first, each reversed: oldest first.
 *
 * Cancelling stops it at the next request or file; a cancelled or failed export deletes its folder, and the temporary
 * files go in every case. Nothing is logged of what people wrote; only TDLib is asked for anything.
 */
class DocumentChatExporter internal constructor(
    private val repository: ChatExportRepository,
    private val tree: DocumentTree,
    private val io: CoroutineDispatcher,
    /** Where the temporary pages go: a folder of the app's cache. */
    private val cacheDirectory: () -> File,
    /** The clock of the folder's date and of "Exported on". */
    private val clock: Clock,
) : ChatExporter {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        repository: ChatExportRepository,
        tree: DocumentTree,
        @Dispatcher(IO) io: CoroutineDispatcher,
    ) : this(repository, tree, io, { File(context.cacheDir, CACHE_FOLDER) }, Clock.systemDefaultZone())

    override suspend fun export(
        chatId: Long,
        options: ChatExportOptions,
        texts: ExportTexts,
        onProgress: suspend (ChatExportProgress) -> Unit,
    ): ChatExportOutcome {
        val found = repository.chat(chatId)
        val (chat, access) = (found as? Outcome.Success)?.value ?: return failure((found as Outcome.Failure).error)
        // The user's own chat is Saved Messages, not their name.
        val named =
            if (chat.kind ==
                ExportChatKind.SavedMessages
            ) {
                chat.copy(title = texts.labels.savedMessages)
            } else {
                chat
            }
        return if (access == ChatExportAccess.Allowed) {
            exportInto(named, options, texts, onProgress)
        } else {
            ChatExportOutcome.Failure(ChatExportError.NotAllowed)
        }
    }

    /** Exports [chat] into a folder of its own; a failure or cancellation deletes it and the temporary pages. */
    private suspend fun exportInto(
        chat: ExportChat,
        options: ChatExportOptions,
        texts: ExportTexts,
        onProgress: suspend (ChatExportProgress) -> Unit,
    ): ChatExportOutcome {
        val temporary = File(cacheDirectory(), UUID.randomUUID().toString())
        var folder: String? = null
        var finished = false
        return try {
            withContext(io) {
                val root = tree.root(options.folderUri) ?: throw FolderGone()
                val created = tree.createFolder(root, folderName(chat, texts)).also { folder = it }
                val result = Run(chat, options, texts, created, temporary, onProgress).export()
                finished = true
                ChatExportOutcome.Success(result)
            }
        } catch (failed: ExportFailed) {
            failure(failed.error)
        } catch (_: FolderGone) {
            ChatExportOutcome.Failure(ChatExportError.FolderUnavailable)
        } catch (failed: IOException) {
            ChatExportOutcome.Failure(
                if (failed.isStorageFull()) ChatExportError.StorageFull else ChatExportError.FolderUnavailable,
            )
        } catch (_: SecurityException) {
            // The grant was taken back meanwhile.
            ChatExportOutcome.Failure(ChatExportError.FolderUnavailable)
        } finally {
            withContext(NonCancellable + io) {
                temporary.deleteRecursively()
                val written = folder
                if (!finished && written != null) tree.delete(written)
            }
        }
    }

    /** "Ada Lovelace – 2026-10-23", without the characters file systems refuse, at most [NAME_MAX] long. */
    private fun folderName(
        chat: ExportChat,
        texts: ExportTexts,
    ): String {
        val title = chat.title.ifBlank { texts.labels.deletedAccount }
        val safe = title.replace(UNSAFE, "_").trim().take(NAME_MAX)
        return "$safe – ${clock.instant().atZone(clock.zone).toLocalDate()}"
    }

    /** One export, from the newest message back. */
    @Suppress("TooManyFunctions") // The steps of one export: read, map, copy, save, write.
    private inner class Run(
        private val chat: ExportChat,
        private val options: ChatExportOptions,
        private val texts: ExportTexts,
        private val folder: String,
        private val temporary: File,
        private val onProgress: suspend (ChatExportProgress) -> Unit,
    ) {
        private val names = HashMap<MessageSender, String?>()
        private val pages = mutableListOf<File>()
        private var ids = LongArray(INITIAL_IDS)
        private var count = 0
        private var mediaFolder: String? = null
        private var mediaCount = 0
        private var skippedCount = 0
        private var total: Int? = null
        private var newest: Long? = null
        private var oldest: Long? = null

        /** The export; TDLib's failures end it as [ExportFailed]. */
        suspend fun export(): ChatExportResult {
            if (!temporary.mkdirs() && !temporary.isDirectory) throw IOException("No cache folder")
            if (options.since == null) total = (repository.messageCount(chat.id) as? Outcome.Success)?.value
            onProgress(progress())
            var from = 0L
            var more = true
            while (more) {
                currentCoroutineContext().ensureActive()
                val page = repository.page(chat.id, from) { onProgress(progress(floodWait = it)) }.orThrow()
                val since = options.since
                val kept = if (since == null) page else page.filter { it.date >= since }
                if (page.isNotEmpty()) {
                    from = page.last().id
                    newest = newest ?: page.first().date
                    oldest = page.last().date
                    if (kept.isNotEmpty()) save(kept.map { map(it) })
                    onProgress(progress())
                }
                // Only an empty page ends the history; one older than the day chosen ends the export.
                more = page.isNotEmpty() && kept.size == page.size
            }
            write()
            return ChatExportResult(count, mediaCount, skippedCount, folder)
        }

        private fun progress(floodWait: Long? = null): ChatExportProgress {
            val since = options.since
            val newest = newest
            val oldest = oldest
            val fraction =
                when {
                    since != null && newest != null && oldest != null && newest > since -> {
                        (newest - oldest).toFloat() / (newest - since)
                    }

                    total != null -> {
                        count.toFloat() / checkNotNull(total)
                    }

                    else -> {
                        null
                    }
                }?.coerceIn(0f, MAX_RUNNING_FRACTION)
            return ChatExportProgress(count, fraction, floodWait)
        }

        /** [messages] as the next temporary page, their ids kept for the replies' links. */
        private fun save(messages: List<ExportMessage>) {
            val file = File(temporary, "page-${pages.size}")
            DataOutputStream(BufferedOutputStream(file.outputStream())).use { it.writePage(messages) }
            pages += file
            for (message in messages) {
                if (count == ids.size) ids = ids.copyOf(ids.size * 2)
                ids[count++] = message.id
            }
        }

        /** The output file, oldest message first. */
        private suspend fun write() {
            val format = options.format
            val document = tree.createFile(folder, format.fileName, format.mimeType)
            // Newest first as read: ascending, for the binary search of the replies' links.
            val sorted = ids.copyOf(count).also { it.sort() }
            tree.openOutput(document).use { stream ->
                val out = BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8), WRITE_BUFFER)
                val writer = format.writer(out, texts) { sorted.binarySearch(it) >= 0 }
                writer.begin(chat, clock.instant().epochSecond, count)
                for (page in pages.asReversed()) {
                    currentCoroutineContext().ensureActive()
                    val messages = DataInputStream(page.inputStream().buffered()).use { it.readPage() }
                    messages.asReversed().forEach(writer::message)
                }
                writer.end()
                out.flush()
            }
        }

        private suspend fun map(message: Message): ExportMessage {
            val content = message.content
            val media = media(message)
            val forward = message.forwardInfo
            // The names a service text needs, read before it is worded (its words are no suspending call).
            if (content is MessageContent.Service) content.action.people().forEach { name(MessageSender.User(it)) }
            return ExportMessage(
                id = message.id,
                date = message.date,
                editDate = message.editDate,
                sender = sender(message),
                isOutgoing = message.isOutgoing,
                text = content.text(),
                replyToMessageId =
                    (message.replyTo as? ReplyTo.Message)
                        ?.takeIf { it.chatId == message.chatId || it.chatId == 0L }
                        ?.messageId
                        ?.takeIf { it != 0L },
                forwardedFrom = forward?.let { originName(it.origin) },
                forwardDate = forward?.date ?: 0,
                media = media,
                summary = if (content is MessageContent.Service) null else texts.summary(content),
                service = (content as? MessageContent.Service)?.let { texts.service(message) { names[it] } },
            )
        }

        /** The sender, but not the channel of its own posts, which the page names once. */
        private suspend fun sender(message: Message): ExportSender? {
            val sender = message.sender
            if (sender is MessageSender.Chat && sender.chatId == chat.id && message.authorSignature.isEmpty()) {
                return null
            }
            val name =
                message.authorSignature.ifEmpty { null }
                    ?: name(sender)
                    ?: texts.labels.deletedAccount
            return when (sender) {
                is MessageSender.User -> ExportSender(sender.userId, isChat = false, name = name)
                is MessageSender.Chat -> ExportSender(sender.chatId, isChat = true, name = name)
            }
        }

        private suspend fun name(sender: MessageSender): String? =
            if (names.containsKey(sender)) {
                names[sender]
            } else {
                repository.senderName(sender).also {
                    names[sender] =
                        it
                }
            }

        private suspend fun originName(origin: MessageOrigin): String =
            when (origin) {
                is MessageOrigin.User -> {
                    name(MessageSender.User(origin.userId))
                }

                is MessageOrigin.HiddenUser -> {
                    origin.name
                }

                is MessageOrigin.Chat -> {
                    origin.authorSignature.ifEmpty { null }
                        ?: name(MessageSender.Chat(origin.chatId))
                }

                is MessageOrigin.Channel -> {
                    name(MessageSender.Chat(origin.chatId))
                }
            }?.takeIf { it.isNotBlank() } ?: texts.labels.deletedAccount

        /** The message's file, copied into `media/` where the user chose it, or named with why it is not. */
        private suspend fun media(message: Message): ExportMedia? {
            val file = message.content.file() ?: return null
            val reason =
                when {
                    file.type == ExportMediaType.Sticker || file.type.kind !in options.media -> {
                        ExportSkipReason.NotChosen
                    }

                    file.isSelfDestructing -> {
                        ExportSkipReason.SelfDestructing
                    }

                    file.ref.size > options.maxFileSize -> {
                        ExportSkipReason.TooLarge
                    }

                    else -> {
                        null
                    }
                }
            return if (reason != null) skipped(file, reason) else downloaded(message, file)
        }

        /**
         * [file] downloaded and copied; named only where Telegram has it no more, or it turns out larger than the
         * limit (its size was not known before). The network, the session or a flood that does not end end the export.
         */
        private suspend fun downloaded(
            message: Message,
            file: MediaFile,
        ): ExportMedia {
            val downloaded = repository.download(file.ref.id) { onProgress(progress(floodWait = it)) }
            val error = (downloaded as? Outcome.Failure)?.error
            if (error != null && error.endsExport) throw ExportFailed(error)
            val source = (downloaded as? Outcome.Success)?.value?.let(::File)
            return when {
                source == null -> skipped(file, ExportSkipReason.Unavailable)
                source.length() > options.maxFileSize -> skipped(file, ExportSkipReason.TooLarge)
                else -> copy(message, file, source)
            }
        }

        /** [source], the file of [message], copied into `media/` as `<message id>_<name>`. */
        private suspend fun copy(
            message: Message,
            file: MediaFile,
            source: File,
        ): ExportMedia {
            val name = file.name.ifEmpty { "${file.type.schemaName}_${message.id}.${file.type.extension}" }
            val copyName = "${message.id}_${name.replace(UNSAFE, "_")}"
            val media = mediaFolder ?: tree.createFolder(folder, MEDIA_FOLDER).also { mediaFolder = it }
            val document = tree.createFile(media, copyName, COPY_MIME_TYPE)
            tree.openOutput(document).use { out -> source.inputStream().use { it.copyTo(out) } }
            mediaCount++
            onProgress(progress())
            return ExportMedia(file.type, file.name, source.length(), "$MEDIA_FOLDER/$copyName", null)
        }

        /** [file] named in the export but not copied, for [reason]; with its own name only, if it has one. */
        private fun skipped(
            file: MediaFile,
            reason: ExportSkipReason,
        ): ExportMedia {
            skippedCount++
            return ExportMedia(file.type, file.name, file.ref.size, null, reason)
        }
    }

    /** Thrown inside the run when the folder picked is gone, to delete what was made and say so. */
    private class FolderGone : IOException("The export's folder is gone")

    /** Thrown inside the run when TDLib fails it with [error]. */
    private class ExportFailed(
        val error: DomainError,
    ) : Exception("TDLib failed the export")

    /** The value, or [ExportFailed] with TDLib's error. */
    private fun <T> Outcome<T>.orThrow(): T =
        when (this) {
            is Outcome.Success -> value
            is Outcome.Failure -> throw ExportFailed(error)
        }

    private companion object {
        const val CACHE_FOLDER = "chat-exports"
        const val MEDIA_FOLDER = "media"

        /** The type copied files are created with, so providers keep the name they are given. */
        const val COPY_MIME_TYPE = "application/octet-stream"

        /** Characters some file systems refuse in names, and control characters. */
        val UNSAFE = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

        const val NAME_MAX = 60
        const val INITIAL_IDS = 1024
        const val WRITE_BUFFER = 64 * 1024

        /** The progress shown while reading; the rest is writing the file. */
        const val MAX_RUNNING_FRACTION = 0.99f
    }
}

/** A failure of TDLib's that ends the export. */
private fun failure(error: DomainError): ChatExportOutcome = ChatExportOutcome.Failure(ChatExportError.Telegram(error))

/**
 * A download failure the whole export fails for (the network, the session, a flood that does not end); a file that
 * is gone or that Telegram refuses only stays out of the export.
 */
private val DomainError.endsExport: Boolean
    get() = this == DomainError.Network || this == DomainError.Unauthorized || this is DomainError.FloodWait

/** A message's file, as the export copies or names it. */
private class MediaFile(
    val type: ExportMediaType,
    val ref: FileRef,
    val name: String,
    val isSelfDestructing: Boolean,
)

/** The text or caption of a message; empty for those without one. */
private fun MessageContent.text(): FormattedText =
    when (this) {
        is MessageContent.Text -> text
        is MessageContent.Photo -> caption
        is MessageContent.Video -> caption
        is MessageContent.Animation -> caption
        is MessageContent.VoiceNote -> caption
        is MessageContent.Audio -> caption
        is MessageContent.Document -> caption
        is MessageContent.Poll -> description
        else -> FormattedText.Empty
    }

/** The file of a message with one; a photo's largest size. */
private fun MessageContent.file(): MediaFile? =
    when (this) {
        is MessageContent.Photo -> {
            photo.sizes.maxByOrNull { it.width.toLong() * it.height }?.let {
                MediaFile(ExportMediaType.Photo, it.file, "", isSecret)
            }
        }

        is MessageContent.Video -> {
            MediaFile(ExportMediaType.Video, video.file, "", isSecret)
        }

        is MessageContent.Animation -> {
            MediaFile(ExportMediaType.Animation, animation.file, "", isSecret)
        }

        is MessageContent.VideoNote -> {
            MediaFile(ExportMediaType.VideoNote, videoNote.file, "", isSecret)
        }

        is MessageContent.VoiceNote -> {
            MediaFile(ExportMediaType.VoiceNote, voiceNote.file, "", false)
        }

        is MessageContent.Audio -> {
            MediaFile(ExportMediaType.Audio, audio.file, audio.fileName, false)
        }

        is MessageContent.Document -> {
            MediaFile(ExportMediaType.Document, document.file, document.fileName, false)
        }

        is MessageContent.Sticker -> {
            MediaFile(ExportMediaType.Sticker, sticker.file, "", false)
        }

        else -> {
            null
        }
    }

/** The switch on the export page that copies files of this type. */
private val ExportMediaType.kind: ExportMediaKind
    get() =
        when (this) {
            ExportMediaType.Photo -> ExportMediaKind.Photos
            ExportMediaType.Video, ExportMediaType.Animation, ExportMediaType.VideoNote -> ExportMediaKind.Videos
            ExportMediaType.VoiceNote -> ExportMediaKind.VoiceMessages
            ExportMediaType.Audio, ExportMediaType.Document, ExportMediaType.Sticker -> ExportMediaKind.Files
        }

/** The extension of a file that has no name of its own. */
private val ExportMediaType.extension: String
    get() =
        when (this) {
            ExportMediaType.Photo -> "jpg"
            ExportMediaType.Video, ExportMediaType.Animation, ExportMediaType.VideoNote -> "mp4"
            ExportMediaType.VoiceNote -> "ogg"
            ExportMediaType.Audio -> "mp3"
            ExportMediaType.Document -> "bin"
            ExportMediaType.Sticker -> "webp"
        }

/** The users a service message names besides its sender. */
private fun ServiceAction.people(): List<Long> =
    when (this) {
        is ServiceAction.BasicGroupCreated -> memberUserIds
        is ServiceAction.MembersAdded -> userIds
        is ServiceAction.MemberRemoved -> listOf(userId)
        is ServiceAction.VideoChatInvited -> userIds
        else -> emptyList()
    }
