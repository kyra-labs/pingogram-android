package dev.kyralabs.pingogram.feature.settings.ui.cleanup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.repository.ChatOpenings
import dev.kyralabs.pingogram.core.domain.repository.ChatOpeningsRepository
import dev.kyralabs.pingogram.core.domain.repository.CleanupSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.LocalChatNamesRepository
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Settings → Chats → Cleanup suggestions (`REQUIREMENTS.md` FR-X24, owner decisions 2026-11-25): its settings, and the
 * chats [CleanupChats] suggests, one at a time, each with Mute, Archive, Leave (after a confirmation, where offered)
 * and Not now. A chat acted on leaves the page at once; a choice that fails is a snackbar and the chat stays. Nothing
 * acts by itself or on more than the chat shown.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CleanupViewModel
    @Inject
    constructor(
        cleanupChats: CleanupChats,
        private val actions: CleanupActions,
        private val settings: CleanupSettingsRepository,
        openings: ChatOpeningsRepository,
        localNames: LocalChatNamesRepository,
    ) : ViewModel() {
        /** The chats acted on, left out until the lists say so. */
        private val handled = MutableStateFlow(persistentSetOf<Long>())
        private val confirmingLeave = MutableStateFlow(false)
        private val acting = MutableStateFlow(false)

        private val suggested: Flow<List<Chat>> =
            combine(cleanupChats.chats, handled) { chats, done -> chats.filterNot { it.id in done } }

        private val current: Flow<CleanupSuggestion?> =
            combine(suggested, openings.openings, localNames.names) { chats, opened, names ->
                chats.firstOrNull()?.let { chat -> chat.toSuggestion(opened, names[chat.id]) }
            }.distinctUntilChanged()
                .flatMapLatest { suggestion ->
                    if (suggestion == null) {
                        flowOf(null)
                    } else {
                        flow {
                            emit(suggestion)
                            emit(suggestion.copy(canLeave = actions.canLeave(suggestion.chatId)))
                        }
                    }
                }

        val uiState: StateFlow<CleanupUiState> =
            combine(
                settings.settings,
                current,
                suggested,
                cleanupChats.loaded,
                combine(confirmingLeave, acting, ::Pair),
            ) { cleanup, suggestion, chats, loaded, (confirming, isActing) ->
                CleanupUiState(
                    settings = cleanup,
                    suggestion = suggestion,
                    remaining = (chats.size - 1).coerceAtLeast(0),
                    loading = !loaded && suggestion == null,
                    isConfirmingLeave = confirming && suggestion?.canLeave == true,
                    isActing = isActing,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), CleanupUiState())

        private val effectChannel = Channel<CleanupEffect>(Channel.BUFFERED)
        val effects: Flow<CleanupEffect> = effectChannel.receiveAsFlow()

        fun onEvent(event: CleanupEvent) {
            when (event) {
                is CleanupEvent.SetEnabled -> {
                    save { settings.setEnabled(event.enabled) }
                }

                is CleanupEvent.SetMonths -> {
                    save { settings.setMonths(event.months) }
                }

                CleanupEvent.Mute -> {
                    act(CleanupAction.Muted) { chatId -> mutedAndKept(chatId) }
                }

                CleanupEvent.Archive -> {
                    act(CleanupAction.Archived, actions.archive)
                }

                CleanupEvent.Leave -> {
                    confirmingLeave.value = true
                }

                CleanupEvent.DismissLeave -> {
                    confirmingLeave.value = false
                }

                CleanupEvent.ConfirmLeave -> {
                    confirmingLeave.value = false
                    act(CleanupAction.Left, actions.leave)
                }

                CleanupEvent.NotNow -> {
                    act(action = null, actions.keep)
                }
            }
        }

        /** Muted, the chat stays listed: counted as opened now, it is not suggested again for the months chosen. */
        private suspend fun mutedAndKept(chatId: Long): Outcome<Unit> =
            when (val muted = actions.mute(chatId)) {
                is Outcome.Success -> actions.keep(chatId)
                is Outcome.Failure -> muted
            }

        /** Does [change] to the chat shown, once at a time, then leaves it out and says [action] when there is one. */
        private fun act(
            action: CleanupAction?,
            change: suspend (chatId: Long) -> Outcome<Unit>,
        ) {
            val chatId = uiState.value.suggestion?.chatId ?: return
            if (!acting.compareAndSet(expect = false, update = true)) return
            viewModelScope.launch {
                when (val outcome = change(chatId)) {
                    is Outcome.Success -> {
                        handled.update { it.adding(chatId) }
                        action?.let { effectChannel.trySend(CleanupEffect.Done(it)) }
                    }

                    is Outcome.Failure -> {
                        effectChannel.trySend(CleanupEffect.ShowError(outcome.error))
                    }
                }
                acting.value = false
            }
        }

        private fun save(change: suspend () -> Outcome<Unit>) {
            viewModelScope.launch { change().onFailure { effectChannel.trySend(CleanupEffect.ShowError(it)) } }
        }

        private companion object {
            const val STOP_TIMEOUT_MILLIS = 5_000L
        }
    }

/** This chat as the page shows it, by [localName] where the user named it. */
private fun Chat.toSuggestion(
    openings: ChatOpenings,
    localName: String?,
) = CleanupSuggestion(
    chatId = id,
    title = localName ?: title,
    photo = photo,
    type = type,
    lastOpened = openings.lastOpenedOf(id),
    neverOpened = id !in openings.lastOpened,
)
