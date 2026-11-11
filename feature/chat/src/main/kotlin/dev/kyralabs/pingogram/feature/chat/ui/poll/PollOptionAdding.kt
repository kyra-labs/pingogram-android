package dev.kyralabs.pingogram.feature.chat.ui.poll

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.NewPollOption
import dev.kyralabs.pingogram.core.domain.repository.PollRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.PollAdding
import dev.kyralabs.pingogram.feature.chat.ui.PollOptionEvent
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Adding answers to the polls of chat [chatId]'s messages that let the user (td_api.tl `messagePoll.can_add_option`,
 * `addPollOption`), for `ChatViewModel`; each request one tap of the user's. The inline field is open per message
 * ([adding], kept across process death); its text goes out as one answer, the field waiting ([PollAdding.Sending]) and
 * closing once Telegram took it, which TDLib then shows with `updatePoll`; a refusal is a [ChatEffect.ShowError]
 * snackbar and the field stays open. A poll that no longer takes answers says so ([ChatEffect.ActionUnavailable]).
 *
 * Runs in [scope], the view model's, on the main thread.
 */
internal class PollOptionAdding(
    private val chatId: Long,
    private val repository: PollRepository,
    private val messageOf: (messageId: Long) -> Message?,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val mutableAdding =
        MutableStateFlow(
            savedState
                .get<LongArray>(KEY_ADDING)
                ?.associate { it to PollAdding.Open }
                ?.toPersistentMap() ?: persistentMapOf(),
        )

    /** The polls whose inline field is open, and whether its answer is on its way, by message id. */
    val adding: StateFlow<PersistentMap<Long, PollAdding>> = mutableAdding.asStateFlow()

    fun onEvent(event: PollOptionEvent) {
        when (event) {
            is PollOptionEvent.Open -> open(event.messageId)
            is PollOptionEvent.Dismiss -> set(event.messageId, null)
            is PollOptionEvent.Add -> add(event.messageId, event.text)
        }
    }

    private fun open(messageId: Long) {
        if (canAdd(messageId)) set(messageId, PollAdding.Open) else onEffect(ChatEffect.ActionUnavailable)
    }

    private fun add(
        messageId: Long,
        text: String,
    ) {
        val answer = text.trim().take(NewPoll.MAX_OPTION_LENGTH)
        if (!canAdd(messageId) || answer.isEmpty()) {
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        if (mutableAdding.value[messageId] == PollAdding.Sending) return
        set(messageId, PollAdding.Sending)
        scope.launch {
            when (val result = repository.addOption(chatId, messageId, NewPollOption(answer))) {
                is Outcome.Success -> {
                    set(messageId, null)
                }

                is Outcome.Failure -> {
                    set(messageId, PollAdding.Open)
                    onEffect(ChatEffect.ShowError(result.error))
                }
            }
        }
    }

    private fun canAdd(messageId: Long): Boolean {
        val content = messageOf(messageId)?.content as? MessageContent.Poll
        return content != null && content.canAddOption && !content.poll.isClosed
    }

    private fun set(
        messageId: Long,
        state: PollAdding?,
    ) {
        mutableAdding.update { if (state == null) it.remove(messageId) else it.put(messageId, state) }
        savedState[KEY_ADDING] = mutableAdding.value.keys.toLongArray()
    }

    private companion object {
        const val KEY_ADDING = "poll_adding"
    }
}

/**
 * The state with the polls' inline fields ([PollOptionAdding.adding]) copied into their bubbles only, so no other
 * recomposes; the state as it is while none is open.
 */
internal fun ChatUiState.withPollAdding(adding: PersistentMap<Long, PollAdding>): ChatUiState {
    val content = content as? ChatContent.Content
    if (adding.isEmpty() || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            val bubble = item as? ChatItem.Bubble
            val state = bubble?.let { adding[it.id] }
            if (bubble == null || state == null) {
                item
            } else {
                changed = true
                bubble.copy(pollAdding = state)
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}
