package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reports the messages a conversation shows to TDLib as viewed ([ChatRepository.viewMessages]), for [ChatViewModel]
 * (`docs/PERFORMANCE.md` §3): TDLib then reads them and updates the chat's unread counts.
 *
 * - **Batched**: whatever came into view goes as soon as there is any, then at most once per [VIEW_INTERVAL_MILLIS],
 *   everything that came into view meanwhile in one call.
 * - **Once each** while the view model lives (the screen session), however often it scrolls back into view.
 * - **Only while the chat is open** in TDLib ([setOpen]), between the screen's start and stop; what came into view
 *   meanwhile waits for the next start.
 * - **Failures** are logged without the ids and not retried: TDLib then just does not read those messages yet.
 *
 * Runs in [scope] for as long as it lives; main thread only.
 */
internal class MessageViews(
    private val chatId: Long,
    private val repository: ChatRepository,
    scope: CoroutineScope,
    private val log: ChatLog,
) {
    private val isOpen = MutableStateFlow(false)

    /** What came into view and was not reported yet. */
    private val pending = MutableStateFlow<Set<Long>>(emptySet())

    private val viewed = HashSet<Long>()

    init {
        scope.launch { report() }
    }

    /** Whether the chat is open in TDLib, which reading needs. */
    fun setOpen(open: Boolean) {
        isOpen.value = open
    }

    /** The messages the screen shows now. */
    fun setVisible(messageIds: Set<Long>) {
        val new = messageIds - viewed
        if (new.isNotEmpty()) pending.update { it + new }
    }

    private suspend fun report() {
        while (true) {
            combine(isOpen, pending) { open, pending -> open && pending.isNotEmpty() }.first { it }
            val ids = pending.value - viewed
            pending.value = emptySet()
            viewed += ids
            repository.viewMessages(chatId, ids.sorted()).onFailure { error ->
                log.warn("viewMessages failed: ${error::class.simpleName}")
            }
            delay(VIEW_INTERVAL_MILLIS)
        }
    }

    companion object {
        /** How often at most the messages on screen go to TDLib (`docs/PERFORMANCE.md` §3). */
        const val VIEW_INTERVAL_MILLIS = 300L
    }
}
