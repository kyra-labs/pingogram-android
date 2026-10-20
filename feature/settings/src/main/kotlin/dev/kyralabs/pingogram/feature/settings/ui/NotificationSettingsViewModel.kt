package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.NotificationPrompt
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.domain.model.PushDelivery
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ScopeNotificationSettingsChange
import dev.kyralabs.pingogram.core.domain.repository.KeepConnectedRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationPromptRepository
import dev.kyralabs.pingogram.core.domain.repository.NotificationSettingsRepository
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The Notifications page (`REQUIREMENTS.md` FR-S1, FR-N1): the defaults of private chats, groups and channels, which
 * belong to the Telegram account, whether Android shows Pingogram's notifications at all, "Keep connected" where the
 * build offers it ([PushDelivery.TdlibConnection]), and resetting everything.
 *
 * - The defaults load when the page opens; TDLib's changes apply as they come, made here or on another device. If
 *   loading fails, the page says why and offers to try again.
 * - A flipped switch shows at once and is saved; if saving fails, the switch flips back (unless something changed it
 *   meanwhile) and a snackbar says why.
 * - Android's side is read by the screen ([NotificationSettingsEvent.AccessChecked]), as only an activity can. Turning
 *   notifications on shows the system's dialog while Android still would (as the chat list's request does, sharing its
 *   answers through [NotificationPromptRepository]), and the app's notification settings otherwise.
 * - Resetting asks first, then `resetAllNotificationSettings`, and loads the defaults again.
 *
 * Nothing to restore after process death: everything is read again.
 */
@HiltViewModel
class NotificationSettingsViewModel
    @Inject
    constructor(
        private val repository: NotificationSettingsRepository,
        private val keepConnectedRepository: KeepConnectedRepository,
        private val promptRepository: NotificationPromptRepository,
        pushDelivery: PushDelivery,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(
                NotificationSettingsUiState(isKeepConnectedOffered = pushDelivery == PushDelivery.TdlibConnection),
            )
        val uiState: StateFlow<NotificationSettingsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<NotificationSettingsEffect>(Channel.BUFFERED)
        val effects: Flow<NotificationSettingsEffect> = effectChannel.receiveAsFlow()

        private val prompt: StateFlow<NotificationPrompt> =
            promptRepository.prompt.stateIn(viewModelScope, SharingStarted.Eagerly, NotificationPrompt())

        /** Android's side as last checked; null until the screen reported it. */
        private var access: NotificationSettingsEvent.AccessChecked? = null

        private var loadJob: Job? = null

        /** The changes TDLib reported since the running load started; its answers may predate them. */
        private val changedDuringLoad = mutableMapOf<NotificationScope, ScopeNotificationSettings>()

        init {
            // Subscribed before the first request, so no change in between is missed.
            viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
                repository.scopeChanges.collect(::onChange)
            }
            if (pushDelivery == PushDelivery.TdlibConnection) {
                viewModelScope.launch {
                    keepConnectedRepository.keepConnected.collect { enabled ->
                        mutableUiState.update { it.copy(keepConnected = enabled) }
                    }
                }
            }
            load()
        }

        fun onEvent(event: NotificationSettingsEvent) {
            when (event) {
                NotificationSettingsEvent.Retry -> {
                    if (uiState.value.scopes is ScopeSettingsState.Error) load()
                }

                is NotificationSettingsEvent.ChangeScope -> {
                    changeScope(event.scope, event.settings)
                }

                is NotificationSettingsEvent.OpenChannelSettings -> {
                    effectChannel.trySend(NotificationSettingsEffect.OpenChannelSettings(event.scope))
                }

                is NotificationSettingsEvent.AccessChecked -> {
                    access = event
                    mutableUiState.update {
                        it.copy(
                            notificationsBlocked = !event.notificationsEnabled,
                            blockedChannels = event.blockedChannels.toImmutableSet(),
                        )
                    }
                }

                NotificationSettingsEvent.TurnOnNotifications -> {
                    turnOnNotifications()
                }

                is NotificationSettingsEvent.SetKeepConnected -> {
                    setKeepConnected(event.enabled)
                }

                NotificationSettingsEvent.Reset -> {
                    if (!uiState.value.isResetting) mutableUiState.update { it.copy(isResetDialogOpen = true) }
                }

                NotificationSettingsEvent.DismissReset -> {
                    mutableUiState.update { it.copy(isResetDialogOpen = false) }
                }

                NotificationSettingsEvent.ConfirmReset -> {
                    reset()
                }
            }
        }

        private fun onChange(change: ScopeNotificationSettingsChange) {
            if (loadJob?.isActive == true) changedDuringLoad[change.scope] = change.settings
            updateScope(change.scope) { change.settings }
        }

        /** Replaces the defaults of [scope] with [transform]'s, while they show. */
        private fun updateScope(
            scope: NotificationScope,
            transform: (ScopeNotificationSettings) -> ScopeNotificationSettings,
        ) {
            mutableUiState.update { state ->
                val scopes = state.scopes as? ScopeSettingsState.Content ?: return@update state
                val current = scopes.settings[scope] ?: return@update state
                state.copy(
                    scopes =
                        ScopeSettingsState.Content(
                            scopes.settings.toPersistentMap().putting(scope, transform(current)),
                        ),
                )
            }
        }

        private fun load() {
            loadJob?.cancel()
            changedDuringLoad.clear()
            mutableUiState.update { it.copy(scopes = ScopeSettingsState.Loading) }
            loadJob =
                viewModelScope.launch {
                    val loaded =
                        coroutineScope {
                            NotificationScope.entries
                                .map { scope -> async { scope to repository.scopeSettings(scope) } }
                                .awaitAll()
                        }
                    val error = loaded.firstNotNullOfOrNull { (it.second as? Outcome.Failure)?.error }
                    val scopes =
                        if (error != null) {
                            ScopeSettingsState.Error(error)
                        } else {
                            val settings =
                                loaded.associate { (scope, outcome) ->
                                    scope to
                                        (outcome as Outcome.Success).value
                                }
                            ScopeSettingsState.Content((settings + changedDuringLoad).toImmutableMap())
                        }
                    mutableUiState.update { it.copy(scopes = scopes) }
                    changedDuringLoad.clear()
                }
        }

        private fun changeScope(
            scope: NotificationScope,
            settings: ScopeNotificationSettings,
        ) {
            val previous = (uiState.value.scopes as? ScopeSettingsState.Content)?.settings?.get(scope) ?: return
            if (previous == settings) return
            updateScope(scope) { settings }
            viewModelScope.launch {
                repository.setScopeSettings(scope, settings).onFailure { error ->
                    // Back to what showed before, unless a later change or TDLib replaced it meanwhile.
                    updateScope(scope) { current -> if (current == settings) previous else current }
                    effectChannel.trySend(NotificationSettingsEffect.ShowError(error))
                }
            }
        }

        private fun turnOnNotifications() {
            val access = access ?: return
            val canAsk = !access.permissionGranted && (!prompt.value.wasRequested || access.shouldShowRationale)
            if (canAsk) {
                // Asked once the dialog shows: a refusal then counts as final when Android stops showing it.
                viewModelScope.launch { promptRepository.setRequested() }
                effectChannel.trySend(NotificationSettingsEffect.RequestPermission)
            } else {
                effectChannel.trySend(NotificationSettingsEffect.OpenAppNotificationSettings)
            }
        }

        private fun setKeepConnected(enabled: Boolean) {
            if (!uiState.value.isKeepConnectedOffered) return
            viewModelScope.launch {
                keepConnectedRepository.setKeepConnected(enabled).onFailure {
                    effectChannel.trySend(NotificationSettingsEffect.ShowError(it))
                }
            }
        }

        private fun reset() {
            if (uiState.value.isResetting) return
            mutableUiState.update { it.copy(isResetDialogOpen = false, isResetting = true) }
            viewModelScope.launch {
                when (val reset = repository.resetAll()) {
                    is Outcome.Success -> {
                        effectChannel.trySend(NotificationSettingsEffect.ResetDone)
                        // TDLib reports the new defaults too; loading them again shows them in any case.
                        mutableUiState.update { it.copy(isResetting = false) }
                        reloadQuietly()
                    }

                    is Outcome.Failure -> {
                        mutableUiState.update { it.copy(isResetting = false) }
                        effectChannel.trySend(NotificationSettingsEffect.ShowError(reset.error))
                    }
                }
            }
        }

        /** Loads the defaults again, keeping what shows meanwhile and if that fails. */
        private suspend fun reloadQuietly() {
            for (scope in NotificationScope.entries) {
                val loaded = repository.scopeSettings(scope)
                if (loaded is Outcome.Success) updateScope(scope) { loaded.value }
            }
        }
    }
