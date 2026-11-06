package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.Message

/** Message [messageId] of chat [chatId] becomes [edit] of itself in every window that holds it. */
internal fun HistoryCache.editing(
    chatId: Long,
    messageId: Long,
    edit: (Message) -> Message,
): HistoryCache {
    val change = HistoryChange.Edit(messageId, edit)
    val edited = windows.filterKeys { it.chatId == chatId }.mapValues { (_, window) -> window.applying(change) }
    return if (edited.isEmpty()) this else copy(windows = windows.putAll(edited))
}
