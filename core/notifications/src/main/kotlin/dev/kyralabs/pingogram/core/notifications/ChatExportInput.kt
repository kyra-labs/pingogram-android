package dev.kyralabs.pingogram.core.notifications

import androidx.work.Data
import androidx.work.workDataOf
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatExportError
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import dev.kyralabs.pingogram.core.domain.model.ChatExportProgress
import dev.kyralabs.pingogram.core.domain.model.ChatExportResult
import dev.kyralabs.pingogram.core.domain.model.ExportFormat
import dev.kyralabs.pingogram.core.domain.model.ExportMediaKind
import kotlinx.collections.immutable.toImmutableSet

// What ChatExportWorker gets and reports through WorkManager (docs/adr/0021-chat-export.md): the export's options as
// its input, its progress while it runs, and its result or error once it ended. WorkManager keeps them in its
// database, so they hold names of enums and numbers only, besides the chat's title and the folder's URI.

/** What [ChatExportWorker] exports: the chat, its title for the notification, and the options. */
internal data class ChatExportInput(
    val chatId: Long,
    val chatTitle: String,
    val options: ChatExportOptions,
) {
    fun toData(): Data =
        workDataOf(
            CHAT_ID to chatId,
            CHAT_TITLE to chatTitle,
            FORMAT to options.format.name,
            MEDIA to options.media.joinToString(SEPARATOR) { it.name },
            MAX_FILE_SIZE to options.maxFileSize,
            SINCE to (options.since ?: NONE),
            FOLDER to options.folderUri,
        )

    override fun toString() = "ChatExportInput(chatId=$chatId, options=$options, …)"

    companion object {
        /** The input in [data]; null if it holds none (a worker that outlived an app update that changed it). */
        fun from(data: Data): ChatExportInput? {
            val chatId = data.getLong(CHAT_ID, 0)
            val folder = data.getString(FOLDER)
            val format = ExportFormat.entries.firstOrNull { it.name == data.getString(FORMAT) }
            if (chatId == 0L || folder == null || format == null) return null
            val media =
                data
                    .getString(MEDIA)
                    .orEmpty()
                    .split(SEPARATOR)
                    .mapNotNull { name -> ExportMediaKind.entries.firstOrNull { it.name == name } }
                    .toImmutableSet()
            return ChatExportInput(
                chatId = chatId,
                chatTitle = data.getString(CHAT_TITLE).orEmpty(),
                options =
                    ChatExportOptions(
                        format = format,
                        media = media,
                        maxFileSize = data.getLong(MAX_FILE_SIZE, ChatExportOptions.NO_SIZE_LIMIT),
                        since = data.getLong(SINCE, NONE).takeIf { it != NONE },
                        folderUri = folder,
                    ),
            )
        }
    }
}

internal fun ChatExportProgress.toData(): Data =
    workDataOf(
        MESSAGES to messages,
        FRACTION to (fraction ?: NO_FRACTION),
        FLOOD_WAIT to (floodWaitSeconds ?: NONE),
    )

/** The progress in [data]; a worker that has reported none yet has read nothing. */
internal fun progressOf(data: Data): ChatExportProgress =
    ChatExportProgress(
        messages = data.getInt(MESSAGES, 0),
        fraction = data.getFloat(FRACTION, NO_FRACTION).takeIf { it >= 0 },
        floodWaitSeconds = data.getLong(FLOOD_WAIT, NONE).takeIf { it != NONE },
    )

internal fun ChatExportResult.toData(): Data =
    workDataOf(
        MESSAGES to messageCount,
        MEDIA_COUNT to mediaCount,
        SKIPPED to skippedMediaCount,
        FOLDER to folderUri,
    )

internal fun resultOf(data: Data): ChatExportResult =
    ChatExportResult(
        messageCount = data.getInt(MESSAGES, 0),
        mediaCount = data.getInt(MEDIA_COUNT, 0),
        skippedMediaCount = data.getInt(SKIPPED, 0),
        folderUri = data.getString(FOLDER).orEmpty(),
    )

internal fun ChatExportError.toData(): Data =
    when (this) {
        ChatExportError.FolderUnavailable -> {
            workDataOf(ERROR to "folder_unavailable")
        }

        ChatExportError.StorageFull -> {
            workDataOf(ERROR to "storage_full")
        }

        ChatExportError.NotAllowed -> {
            workDataOf(ERROR to "not_allowed")
        }

        is ChatExportError.Telegram -> {
            val error = error
            when (error) {
                DomainError.Network -> workDataOf(ERROR to "network")
                DomainError.Unauthorized -> workDataOf(ERROR to "unauthorized")
                DomainError.NotFound -> workDataOf(ERROR to "not_found")
                is DomainError.FloodWait -> workDataOf(ERROR to "flood_wait", FLOOD_WAIT to error.seconds)
                else -> workDataOf(ERROR to "unknown")
            }
        }
    }

/** The error in [data]; one this version does not know (or none) is TDLib's, of no particular kind. */
internal fun errorOf(data: Data): ChatExportError =
    when (data.getString(ERROR)) {
        "folder_unavailable" -> ChatExportError.FolderUnavailable
        "storage_full" -> ChatExportError.StorageFull
        "not_allowed" -> ChatExportError.NotAllowed
        "network" -> ChatExportError.Telegram(DomainError.Network)
        "unauthorized" -> ChatExportError.Telegram(DomainError.Unauthorized)
        "not_found" -> ChatExportError.Telegram(DomainError.NotFound)
        "flood_wait" -> ChatExportError.Telegram(DomainError.FloodWait(data.getLong(FLOOD_WAIT, 0)))
        else -> ChatExportError.Telegram(DomainError.Unknown(0, ""))
    }

private const val CHAT_ID = "chat_id"
private const val CHAT_TITLE = "chat_title"
private const val FORMAT = "format"
private const val MEDIA = "media"
private const val MAX_FILE_SIZE = "max_file_size"
private const val SINCE = "since"
private const val FOLDER = "folder"
private const val MESSAGES = "messages"
private const val FRACTION = "fraction"
private const val FLOOD_WAIT = "flood_wait"
private const val MEDIA_COUNT = "media_count"
private const val SKIPPED = "skipped"
private const val ERROR = "error"
private const val SEPARATOR = ","
private const val NONE = -1L
private const val NO_FRACTION = -1f
