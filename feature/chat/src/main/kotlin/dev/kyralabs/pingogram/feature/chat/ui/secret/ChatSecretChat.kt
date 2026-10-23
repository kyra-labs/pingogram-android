package dev.kyralabs.pingogram.feature.chat.ui.secret

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.SecretChat
import dev.kyralabs.pingogram.core.domain.model.SecretChatState
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.SecretChatRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.shortName
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.SecretChatEvent
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The secret chat behind chat [chatId] for `ChatViewModel` (`REQUIREMENTS.md` FR-E1): its state ([secretChat], which
 * the header and the composer follow), and the sheets of its top bar's actions ([sheet]). Other chats have neither.
 *
 * - **Timer** ([SecretChatEvent.OpenTimer]): the sheet lists the self-destruct timers ([timerChoices]) with the chat's
 *   own (`Chat.messageAutoDeleteTime`) chosen, only while the chat is ready. A tapped timer shows chosen at once and
 *   other taps wait while TDLib sets it ([SecretChatRepository.setSelfDestructTime]); the sheet closes once it did,
 *   and a failure marks the timer before it again, with a snackbar ([ChatEffect.ShowError]).
 * - **Key** ([SecretChatEvent.OpenKey]): the sheet shows the key hash, following it from pending (no key yet) to ready,
 *   with the other user's name; "End secret chat" asks first, then closes the chat for both sides
 *   ([SecretChatRepository.closeSecretChat]) and the sheet, a failure with a snackbar.
 *
 * Runs in [scope], the view model's, on the main thread.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatSecretChat(
    private val chatId: Long,
    private val chats: ChatRepository,
    users: UserRepository,
    private val repository: SecretChatRepository,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    /** The chat's secret chat, then every change; null for other chats and while TDLib has not announced it. */
    val secretChat: Flow<SecretChat?> =
        chats
            .chat(chatId)
            .map { (it?.type as? ChatType.Secret)?.secretChatId }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) flowOf(null) else repository.secretChat(id) }

    private val open = MutableStateFlow<OpenSheet?>(null)

    /** The other user of the secret chat with its secret chat, for the key sheet's name. */
    private val peer: Flow<Pair<SecretChat, User?>?> =
        secretChat.flatMapLatest { secret ->
            if (secret == null) flowOf(null) else users.user(secret.userId).map { secret to it }
        }

    /** The open sheet, following the chat and its secret chat; null while none is open. */
    val sheet: StateFlow<SecretChatSheet?> =
        combine(open, chats.chat(chatId), peer) { open, chat, peer ->
            if (chat == null || peer == null) null else sheetOf(open, chat, peer.first, peer.second)
        }.stateIn(scope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    fun onEvent(event: SecretChatEvent) {
        when (event) {
            SecretChatEvent.OpenTimer -> {
                openTimer()
            }

            is SecretChatEvent.ChooseTimer -> {
                chooseTimer(event.seconds)
            }

            SecretChatEvent.OpenKey -> {
                open.value = OpenSheet.Key(confirmingEnd = false)
            }

            SecretChatEvent.EndChat -> {
                open.update { if (it is OpenSheet.Key) it.copy(confirmingEnd = true) else it }
            }

            SecretChatEvent.DismissEnd -> {
                open.update {
                    if (it is OpenSheet.Key) {
                        it.copy(
                            confirmingEnd = false,
                        )
                    } else {
                        it
                    }
                }
            }

            SecretChatEvent.ConfirmEnd -> {
                endChat()
            }

            SecretChatEvent.Dismiss -> {
                open.value = null
            }
        }
    }

    private fun openTimer() {
        scope.launch {
            if (secretChat.first()?.state == SecretChatState.Ready) open.value = OpenSheet.Timer(changingTo = null)
        }
    }

    private fun chooseTimer(seconds: Int) {
        val current = open.value as? OpenSheet.Timer ?: return
        if (current.changingTo != null) return
        // The tapped timer shows chosen at once, and other taps wait; the one before comes back if TDLib refuses it.
        open.value = OpenSheet.Timer(changingTo = seconds)
        scope.launch {
            if (chats.chat(chatId).first()?.messageAutoDeleteTime == seconds) {
                open.update { if (it is OpenSheet.Timer) null else it }
                return@launch
            }
            when (val set = repository.setSelfDestructTime(chatId, seconds)) {
                is Outcome.Success -> {
                    open.update { if (it is OpenSheet.Timer) null else it }
                }

                is Outcome.Failure -> {
                    // Unless dismissed meanwhile: as it was before the tap.
                    open.update { if (it is OpenSheet.Timer) OpenSheet.Timer(changingTo = null) else it }
                    onEffect(ChatEffect.ShowError(set.error))
                }
            }
        }
    }

    private fun endChat() {
        if ((open.value as? OpenSheet.Key)?.confirmingEnd != true) return
        open.value = null
        scope.launch {
            val secret = secretChat.first() ?: return@launch
            val closed = repository.closeSecretChat(secret.id)
            if (closed is Outcome.Failure) onEffect(ChatEffect.ShowError(closed.error))
        }
    }

    /** What the view model keeps of the open sheet; the rest comes from the chat and its secret chat. */
    private sealed interface OpenSheet {
        /** [changingTo] the timer on its way to TDLib; null while none is. */
        data class Timer(
            val changingTo: Int?,
        ) : OpenSheet

        data class Key(
            val confirmingEnd: Boolean,
        ) : OpenSheet
    }

    private fun sheetOf(
        open: OpenSheet?,
        chat: Chat,
        secret: SecretChat,
        user: User?,
    ): SecretChatSheet? =
        when (open) {
            null -> {
                null
            }

            is OpenSheet.Timer -> {
                // The chat may end while the sheet is open: nothing can be set any more.
                if (secret.state != SecretChatState.Ready) {
                    null
                } else {
                    val chosen = open.changingTo ?: chat.messageAutoDeleteTime
                    SecretChatSheet.Timer(
                        options =
                            timerChoices(chat.messageAutoDeleteTime)
                                .map { TimerOption(it, selected = it == chosen) }
                                .toImmutableList(),
                        isChanging = open.changingTo != null,
                    )
                }
            }

            is OpenSheet.Key -> {
                SecretChatSheet.Key(
                    name = user?.shortName() ?: UiText.Plain(chat.title),
                    keyHash = secret.keyHash,
                    canEnd = secret.state != SecretChatState.Closed,
                    confirmingEnd = open.confirmingEnd && secret.state != SecretChatState.Closed,
                )
            }
        }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
