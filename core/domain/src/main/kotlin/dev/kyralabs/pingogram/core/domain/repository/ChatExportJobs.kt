package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.domain.model.ChatExportJob
import dev.kyralabs.pingogram.core.domain.model.ChatExportOptions
import kotlinx.coroutines.flow.Flow

/**
 * Exports in the background (`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`): one at a time per chat, each
 * running [ChatExporter] with a progress notification that can cancel it, so it goes on after the export page closed
 * and while the app is in the background.
 */
interface ChatExportJobs {
    /** Starts exporting chat [chatId], named [chatTitle] in its notification, unless an export of it is running. */
    fun start(
        chatId: Long,
        chatTitle: String,
        options: ChatExportOptions,
    )

    /** Cancels the export of chat [chatId], if one runs; it deletes what it wrote. */
    fun cancel(chatId: Long)

    /** The latest export of chat [chatId], then every change; null if there was none lately. */
    fun job(chatId: Long): Flow<ChatExportJob?>
}
