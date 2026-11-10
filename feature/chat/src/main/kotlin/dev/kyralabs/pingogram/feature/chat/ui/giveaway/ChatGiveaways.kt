package dev.kyralabs.pingogram.feature.chat.ui.giveaway

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.GiveawayInfo
import dev.kyralabs.pingogram.core.domain.model.GiveawayParticipantStatus
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.GiveawayRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.GiveawayEvent
import dev.kyralabs.pingogram.feature.chat.ui.findById
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The giveaways of chat [chatId]'s messages (`REQUIREMENTS.md` FR-C4), for `ChatViewModel`: the details sheet
 * ("Learn more", [sheet]) of a giveaway or of its winners message, which asks TDLib what it knows of it for the user
 * ([GiveawayRepository.info]) once it opens, again on "Try again", and names the chat whose administrators do not take
 * part ([chats]) where that is why the user does not. Nothing is asked before the sheet opens, and nothing enters or
 * buys (Telegram's API terms, `REQUIREMENTS.md` §1.2). The open sheet's message is kept across process death and its
 * info loaded again. Runs in [scope], the view model's, which cancels a load with it; the sheet's state is kept out of
 * `ChatUiState`, like the poll voters'.
 */
internal class ChatGiveaways(
    private val chatId: Long,
    private val repository: GiveawayRepository,
    private val chats: ChatRepository,
    private val messageOf: (messageId: Long) -> Message?,
    private val history: Flow<ChatHistory>,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val open = MutableStateFlow<GiveawayInfoSheet?>(null)

    /** The details sheet; null while it is closed. */
    val sheet: StateFlow<GiveawayInfoSheet?> = open.asStateFlow()

    private var loading: Job? = null

    fun onEvent(event: GiveawayEvent) {
        when (event) {
            is GiveawayEvent.OpenInfo -> openInfo(event.messageId)
            GiveawayEvent.RetryInfo -> load()
            GiveawayEvent.DismissInfo -> dismiss()
        }
    }

    /** Opens the sheet of the message saved before process death again, once the history holds the message. */
    fun restore() {
        val messageId = savedState.get<Long>(KEY_OPEN) ?: return
        scope.launch {
            val loaded =
                withTimeoutOrNull(RESTORE_TIMEOUT_MILLIS) {
                    history.first { it.messages.findById(messageId) != null }
                }
            if (loaded != null && open.value == null) openInfo(messageId)
        }
    }

    private fun openInfo(messageId: Long) {
        val sheet =
            when (val content = messageOf(messageId)?.content) {
                is MessageContent.Giveaway -> GiveawayInfoSheet(messageId, content.prize, content.winnerCount)
                is MessageContent.GiveawayWinners -> GiveawayInfoSheet(messageId, content.prize, content.winnerCount)
                else -> null
            }
        if (sheet == null) {
            savedState.remove<Long>(KEY_OPEN)
            onEffect(ChatEffect.ActionUnavailable)
            return
        }
        open.value = sheet
        savedState[KEY_OPEN] = messageId
        load()
    }

    private fun load() {
        val sheet = open.value ?: return
        loading?.cancel()
        open.update { it?.copy(state = GiveawayInfoState.Loading) }
        loading =
            scope.launch {
                val state =
                    when (val result = repository.info(chatId, sheet.messageId)) {
                        is Outcome.Success -> GiveawayInfoState.Loaded(result.value, chatTitleOf(result.value))
                        is Outcome.Failure -> GiveawayInfoState.Failed(result.error)
                    }
                open.update { current -> current?.takeIf { it.messageId == sheet.messageId }?.copy(state = state) }
            }
    }

    /** The title of the chat whose administrators do not take part, where that keeps the user out. */
    private suspend fun chatTitleOf(info: GiveawayInfo): String? {
        val status = (info as? GiveawayInfo.Ongoing)?.status as? GiveawayParticipantStatus.Administrator ?: return null
        return chats.chat(status.chatId).first()?.title
            ?: (chats.getChat(status.chatId) as? Outcome.Success)?.value?.title
    }

    private fun dismiss() {
        loading?.cancel()
        loading = null
        open.value = null
        savedState.remove<Long>(KEY_OPEN)
    }

    internal companion object {
        const val KEY_OPEN = "giveaway_info_message"

        /** How long a restore waits for the history to hold the sheet's message. */
        private const val RESTORE_TIMEOUT_MILLIS = 5_000L
    }
}
