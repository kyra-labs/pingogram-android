package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatMute
import dev.kyralabs.pingogram.core.domain.model.ChatMuteChange
import dev.kyralabs.pingogram.core.domain.model.MuteChange
import dev.kyralabs.pingogram.core.domain.model.NotificationException
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.repository.NotificationSettingsRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The exceptions page of a kind of chats, [scope] (`REQUIREMENTS.md` FR-S1): the chats whose notifications differ from
 * its defaults, as TDLib lists them, each with whether it notifies.
 *
 * - Tapping a chat opens its mute options: mute for an hour, 8 hours, a day or forever, unmute, or use the default
 *   again, which removes it from the list, as its remove button does.
 * - A change shows as soon as TDLib accepted it; TDLib's own reports of the chats' settings apply as they come (a chat
 *   whose limited mute ended shows as unmuted; one that uses the default again leaves). If a change fails, the row
 *   stays as it was and a snackbar says why.
 * - Chats that become exceptions elsewhere show the next time the page opens: TDLib reports no list.
 */
@HiltViewModel(assistedFactory = NotificationExceptionsViewModel.Factory::class)
class NotificationExceptionsViewModel
    @AssistedInject
    constructor(
        @Assisted private val scope: NotificationScope,
        private val repository: NotificationSettingsRepository,
        private val timeFormats: SettingsTimeFormats,
    ) : ViewModel() {
        /** Creates the view model of the exceptions page of a kind of chats. */
        @AssistedFactory
        interface Factory {
            fun create(scope: NotificationScope): NotificationExceptionsViewModel
        }

        private val mutableUiState = MutableStateFlow(NotificationExceptionsUiState(scope))
        val uiState: StateFlow<NotificationExceptionsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<NotificationExceptionsEffect>(Channel.BUFFERED)
        val effects: Flow<NotificationExceptionsEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        /** The changes TDLib reported since the running load started; its answer may predate them. */
        private val changedDuringLoad = mutableMapOf<Long, ChatMute>()

        init {
            // Subscribed before the first request, so no change in between is missed.
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.chatMuteChanges.collect(::onChange)
            }
            load()
        }

        fun onEvent(event: NotificationExceptionsEvent) {
            when (event) {
                NotificationExceptionsEvent.Retry -> {
                    if (uiState.value.exceptions is ExceptionsState.Error) load()
                }

                is NotificationExceptionsEvent.OpenMuteOptions -> {
                    val item = items().firstOrNull { it.chatId == event.chatId && !it.isChanging }
                    if (item != null) mutableUiState.update { it.copy(muteOptions = item) }
                }

                NotificationExceptionsEvent.DismissMuteOptions -> {
                    mutableUiState.update { it.copy(muteOptions = null) }
                }

                is NotificationExceptionsEvent.Change -> {
                    change(event.chatId, event.change)
                }
            }
        }

        private fun items(): List<NotificationExceptionItem> =
            (uiState.value.exceptions as? ExceptionsState.Content)?.items.orEmpty()

        private fun load() {
            loadJob?.cancel()
            changedDuringLoad.clear()
            mutableUiState.update { it.copy(exceptions = ExceptionsState.Loading) }
            loadJob =
                viewModelScope.launch {
                    val exceptions =
                        when (val loaded = repository.exceptions(scope)) {
                            is Outcome.Success -> {
                                loaded.value
                                    .map { exception ->
                                        changedDuringLoad[exception.chatId]?.let { exception.copy(mute = it) }
                                            ?: exception
                                    }.filter { it.mute != ChatMute.Default }
                                    .map { it.toItem() }
                                    .toState()
                            }

                            is Outcome.Failure -> {
                                ExceptionsState.Error(loaded.error)
                            }
                        }
                    mutableUiState.update { it.copy(exceptions = exceptions) }
                    changedDuringLoad.clear()
                }
        }

        private fun onChange(change: ChatMuteChange) {
            if (loadJob?.isActive == true) changedDuringLoad[change.chatId] = change.mute
            applyMute(change.chatId, change.mute)
        }

        /** Shows chat [chatId] with [mute], or takes it off the list once it follows the default. */
        private fun applyMute(
            chatId: Long,
            mute: ChatMute,
        ) {
            mutableUiState.update { state ->
                val items = (state.exceptions as? ExceptionsState.Content)?.items ?: return@update state
                if (items.none { it.chatId == chatId }) return@update state
                val changed =
                    if (mute == ChatMute.Default) {
                        items.filter { it.chatId != chatId }
                    } else {
                        items.map { if (it.chatId == chatId) it.copy(mute = mute, status = mute.status()) else it }
                    }
                state.copy(
                    exceptions = changed.toState(),
                    muteOptions = state.muteOptions?.takeIf { it.chatId != chatId },
                )
            }
        }

        private fun change(
            chatId: Long,
            change: MuteChange,
        ) {
            if (items().none { it.chatId == chatId && !it.isChanging }) return
            mutableUiState.update { it.copy(muteOptions = null) }
            setChanging(chatId, changing = true)
            viewModelScope.launch {
                val now = timeFormats.now().epochSecond
                when (val result = repository.setChatMute(chatId, change)) {
                    is Outcome.Success -> {
                        setChanging(chatId, changing = false)
                        // TDLib reports it too, but sends nothing for a change that changes nothing.
                        applyMute(chatId, change.toMute(now))
                    }

                    is Outcome.Failure -> {
                        setChanging(chatId, changing = false)
                        effectChannel.trySend(NotificationExceptionsEffect.ShowError(result.error))
                    }
                }
            }
        }

        private fun setChanging(
            chatId: Long,
            changing: Boolean,
        ) {
            mutableUiState.update { state ->
                val content = state.exceptions as? ExceptionsState.Content ?: return@update state
                val items = content.items.map { if (it.chatId == chatId) it.copy(isChanging = changing) else it }
                state.copy(exceptions = ExceptionsState.Content(items.toImmutableList()))
            }
        }

        private fun NotificationException.toItem() =
            NotificationExceptionItem(
                chatId = chatId,
                title = title,
                photo = photo,
                status = mute.status(),
                mute = mute,
            )

        private fun ChatMute.status(): UiText =
            when (this) {
                ChatMute.Default -> {
                    UiText.resource(R.string.settings_exception_default)
                }

                ChatMute.Unmuted -> {
                    UiText.resource(R.string.settings_exception_unmuted)
                }

                ChatMute.MutedForever -> {
                    UiText.resource(R.string.settings_exception_muted)
                }

                is ChatMute.MutedUntil -> {
                    val until = timeFormats.formatter().until(untilEpochSeconds, timeFormats.now())
                    UiText.resource(R.string.settings_exception_muted_until, until)
                }
            }
    }

private fun List<NotificationExceptionItem>.toState(): ExceptionsState =
    if (isEmpty()) ExceptionsState.Empty else ExceptionsState.Content(toImmutableList())

/** The mute [this] change gives a chat at [nowEpochSeconds]. */
private fun MuteChange.toMute(nowEpochSeconds: Long): ChatMute =
    when (this) {
        is MuteChange.MuteFor -> ChatMute.MutedUntil(nowEpochSeconds + seconds)
        MuteChange.MuteForever -> ChatMute.MutedForever
        MuteChange.Unmute -> ChatMute.Unmuted
        MuteChange.UseDefault -> ChatMute.Default
    }
