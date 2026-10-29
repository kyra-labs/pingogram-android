package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.MessageSchedule
import dev.kyralabs.pingogram.core.domain.repository.ScheduledMessageRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The conversation's "3 scheduled messages" bar (`REQUIREMENTS.md` FR-C5), for `ChatViewModel`: while TDLib says the
 * [chat] holds scheduled messages (`Chat.hasScheduledMessages`), how many it holds and when the first goes out
 * ([next] words it), from [repository], which follows them as they are scheduled, sent and deleted; no bar otherwise,
 * without asking TDLib for anything. Only the whole chat's conversation has it ([enabled]): a forum topic's or a
 * thread's has none, as the list holds the chat's every scheduled message.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatScheduledMessages(
    private val chatId: Long,
    private val repository: ScheduledMessageRepository,
    private val chat: Flow<Chat>,
    private val enabled: Boolean,
    private val next: (MessageSchedule) -> UiText,
) {
    /** The bar; null while the chat has no scheduled messages, or while they load. */
    val bar: Flow<ScheduledBar?> =
        if (!enabled) {
            flowOf(null)
        } else {
            chat
                .map { it.hasScheduledMessages }
                .distinctUntilChanged()
                .flatMapLatest { has ->
                    if (has) {
                        repository.scheduledMessages(chatId).map { loaded ->
                            // The earliest first.
                            loaded.getOrNull()?.takeIf { it.isNotEmpty() }?.let {
                                ScheduledBar(
                                    it.size,
                                    next(it.first().schedule),
                                )
                            }
                        }
                    } else {
                        flowOf(null)
                    }
                }.onStart { emit(null) }
                .distinctUntilChanged()
        }
}
