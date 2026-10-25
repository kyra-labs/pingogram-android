package dev.kyralabs.pingogram.feature.settings.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveSession
import dev.kyralabs.pingogram.core.domain.model.ActiveSessions
import dev.kyralabs.pingogram.core.domain.model.TelegramServers
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * The Active sessions page (`REQUIREMENTS.md` FR-S1): the account's sessions, this device first, then new logins,
 * then the others, as TDLib orders them; terminating one or all others after a destructive confirmation; and confirming
 * a new login as the user's own.
 *
 * - The sessions load when the page opens and again whenever it shows again ([ActiveSessionsEvent.Refresh]), keeping
 *   what shows meanwhile and if that fails; nothing polls.
 * - A terminated session leaves the list at once; terminating all others leaves this device alone. Telegram refuses
 *   terminations from a device that logged in recently, with error 406 ([ActiveSessionsEffect.TerminationRefused]).
 * - A confirmed login loads the list again, where it is one of the others.
 * - This device's row says "Test server" while the account is signed in on Telegram's test servers (FR-A9).
 *
 * The open confirmation survives process death through the [SavedStateHandle]; terminations in progress do not, as
 * TDLib finishes them anyway and the list loads again.
 */
@HiltViewModel
class ActiveSessionsViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val sessionRepository: SessionRepository,
        private val timeFormats: SettingsTimeFormats,
        authRepository: AuthRepository,
    ) : ViewModel() {
        private val mutableUiState = MutableStateFlow(ActiveSessionsUiState(dialog = savedStateHandle.restoredDialog()))
        val uiState: StateFlow<ActiveSessionsUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<ActiveSessionsEffect>(Channel.BUFFERED)
        val effects: Flow<ActiveSessionsEffect> = effectChannel.receiveAsFlow()

        private var loadJob: Job? = null

        init {
            authRepository.servers
                .onEach { servers ->
                    mutableUiState.update { it.copy(isOnTestServers = servers == TelegramServers.Test) }
                }.launchIn(viewModelScope)
            load(keepContent = false)
        }

        fun onEvent(event: ActiveSessionsEvent) {
            when (event) {
                ActiveSessionsEvent.Refresh -> {
                    if (loadJob?.isActive != true) load(keepContent = true)
                }

                ActiveSessionsEvent.Retry -> {
                    if (uiState.value.sessions is SessionsState.Failed) load(keepContent = false)
                }

                is ActiveSessionsEvent.Terminate -> {
                    if (event.session.id !in uiState.value.busySessionIds) {
                        setDialog(TerminateDialog.Session(event.session.id, event.session.title))
                    }
                }

                ActiveSessionsEvent.TerminateAllOthers -> {
                    if (!uiState.value.isTerminatingAll) setDialog(TerminateDialog.AllOthers)
                }

                ActiveSessionsEvent.ConfirmTermination -> {
                    confirmTermination()
                }

                ActiveSessionsEvent.DismissTermination -> {
                    setDialog(null)
                }

                is ActiveSessionsEvent.ConfirmLogin -> {
                    confirmLogin(event.sessionId)
                }
            }
        }

        /** Loads the sessions; with [keepContent], what shows stays meanwhile, and if loading fails. */
        private fun load(keepContent: Boolean) {
            loadJob?.cancel()
            if (!keepContent || uiState.value.sessions !is SessionsState.Loaded) {
                mutableUiState.update { it.copy(sessions = SessionsState.Loading) }
            }
            loadJob =
                viewModelScope.launch {
                    when (val sessions = sessionRepository.activeSessions()) {
                        is Outcome.Success -> {
                            val loaded = sessions.value.toState(timeFormats.formatter(), timeFormats.now())
                            mutableUiState.update { it.copy(sessions = loaded) }
                        }

                        is Outcome.Failure -> {
                            mutableUiState.update {
                                if (it.sessions is SessionsState.Loaded) {
                                    it
                                } else {
                                    it.copy(
                                        sessions = SessionsState.Failed(sessions.error),
                                    )
                                }
                            }
                        }
                    }
                }
        }

        private fun confirmTermination() {
            when (val dialog = uiState.value.dialog) {
                is TerminateDialog.Session -> terminate(dialog.sessionId)
                TerminateDialog.AllOthers -> terminateAllOthers()
                null -> Unit
            }
            setDialog(null)
        }

        /** Terminates one session at a time per session; a second confirmation meanwhile does nothing. */
        private fun terminate(sessionId: Long) {
            if (sessionId in uiState.value.busySessionIds) return
            setBusy(sessionId, busy = true)
            viewModelScope.launch {
                val terminated = sessionRepository.terminate(sessionId)
                setBusy(sessionId, busy = false)
                when (terminated) {
                    is Outcome.Success -> {
                        updateLoaded { loaded ->
                            loaded.copy(
                                unconfirmed = loaded.unconfirmed.filterNot { it.id == sessionId }.toImmutableList(),
                                others = loaded.others.filterNot { it.id == sessionId }.toImmutableList(),
                            )
                        }
                        effectChannel.trySend(ActiveSessionsEffect.SessionTerminated)
                    }

                    is Outcome.Failure -> {
                        showTerminationError(terminated.error)
                    }
                }
            }
        }

        private fun terminateAllOthers() {
            if (uiState.value.isTerminatingAll) return
            mutableUiState.update { it.copy(isTerminatingAll = true) }
            viewModelScope.launch {
                val terminated = sessionRepository.terminateAllOthers()
                mutableUiState.update { it.copy(isTerminatingAll = false) }
                when (terminated) {
                    is Outcome.Success -> {
                        updateLoaded { it.copy(unconfirmed = persistentListOf(), others = persistentListOf()) }
                        effectChannel.trySend(ActiveSessionsEffect.OtherSessionsTerminated)
                    }

                    is Outcome.Failure -> {
                        showTerminationError(terminated.error)
                    }
                }
            }
        }

        private fun confirmLogin(sessionId: Long) {
            if (sessionId in uiState.value.busySessionIds) return
            setBusy(sessionId, busy = true)
            viewModelScope.launch {
                val confirmed = sessionRepository.confirm(sessionId)
                setBusy(sessionId, busy = false)
                when (confirmed) {
                    is Outcome.Success -> {
                        effectChannel.trySend(ActiveSessionsEffect.LoginConfirmed)
                        load(keepContent = true)
                    }

                    is Outcome.Failure -> {
                        if (confirmed.error != DomainError.Silent) {
                            effectChannel.trySend(ActiveSessionsEffect.ShowError(confirmed.error))
                        }
                    }
                }
            }
        }

        private fun showTerminationError(error: DomainError) {
            val effect =
                if (error == DomainError.Silent) {
                    ActiveSessionsEffect.TerminationRefused
                } else {
                    ActiveSessionsEffect.ShowError(error)
                }
            effectChannel.trySend(effect)
        }

        private fun setBusy(
            sessionId: Long,
            busy: Boolean,
        ) = mutableUiState.update {
            val ids = if (busy) it.busySessionIds + sessionId else it.busySessionIds - sessionId
            it.copy(busySessionIds = ids.toPersistentSet())
        }

        private fun updateLoaded(transform: (SessionsState.Loaded) -> SessionsState.Loaded) =
            mutableUiState.update { state ->
                val loaded = state.sessions as? SessionsState.Loaded
                if (loaded == null) state else state.copy(sessions = transform(loaded))
            }

        private fun setDialog(dialog: TerminateDialog?) {
            mutableUiState.update { it.copy(dialog = dialog) }
            savedStateHandle[KEY_DIALOG] =
                when (dialog) {
                    is TerminateDialog.Session -> DIALOG_SESSION
                    TerminateDialog.AllOthers -> DIALOG_ALL_OTHERS
                    null -> null
                }
            savedStateHandle[KEY_DIALOG_SESSION_ID] = (dialog as? TerminateDialog.Session)?.sessionId
            savedStateHandle[KEY_DIALOG_DEVICE_MODEL] =
                ((dialog as? TerminateDialog.Session)?.title as? UiText.Plain)?.text
        }
    }

/** The confirmation [ActiveSessionsViewModel] had open before the process died; null if none was. */
private fun SavedStateHandle.restoredDialog(): TerminateDialog? {
    val sessionId = get<Long>(KEY_DIALOG_SESSION_ID)
    return when (get<String>(KEY_DIALOG)) {
        DIALOG_ALL_OTHERS -> {
            TerminateDialog.AllOthers
        }

        DIALOG_SESSION -> {
            sessionId?.let { TerminateDialog.Session(it, sessionTitle(get<String>(KEY_DIALOG_DEVICE_MODEL).orEmpty())) }
        }

        else -> {
            null
        }
    }
}

private const val KEY_DIALOG = "sessions.dialog"
private const val KEY_DIALOG_SESSION_ID = "sessions.dialogSessionId"
private const val KEY_DIALOG_DEVICE_MODEL = "sessions.dialogDeviceModel"
private const val DIALOG_SESSION = "session"
private const val DIALOG_ALL_OTHERS = "allOthers"

/** This device, the new logins and the others, each in TDLib's order. */
private fun ActiveSessions.toState(
    formatter: SettingsTimeFormatter,
    now: Instant,
): SessionsState.Loaded {
    val items = sessions.map { it to it.toItem(formatter, now) }
    return SessionsState.Loaded(
        current = items.firstOrNull { (session, _) -> session.isCurrent }?.second,
        unconfirmed =
            items
                .filter { (session, _) -> !session.isCurrent && session.isUnconfirmed }
                .map { it.second }
                .toImmutableList(),
        others =
            items
                .filter { (session, _) -> !session.isCurrent && !session.isUnconfirmed }
                .map { it.second }
                .toImmutableList(),
        inactiveSessionTtlDays = inactiveSessionTtlDays,
    )
}

/**
 * The row of [this] session: its device, its app and system ("Pingogram 0.3.0 · Android 15", the version alone when
 * it already names the app), and where and when it was last active ("Online" for this device).
 */
internal fun ActiveSession.toItem(
    formatter: SettingsTimeFormatter,
    now: Instant,
): SessionItem {
    val app =
        if (applicationVersion.startsWith(
                applicationName,
            )
        ) {
            applicationVersion
        } else {
            "$applicationName $applicationVersion"
        }
    val system = "$platform $systemVersion"
    val time =
        if (isCurrent) {
            UiText.resource(R.string.settings_session_online)
        } else {
            UiText.Plain(formatter.lastActive(lastActiveDate, now))
        }
    return SessionItem(
        id = id,
        device = device,
        title = sessionTitle(deviceModel),
        app = parts(UiText.Plain(app.trim()), UiText.Plain(system.trim())),
        activity = parts(UiText.Plain(location.trim()), time),
        isPasswordPending = isPasswordPending,
    )
}

/** The device model as a session's title, or "Unknown device" without one. */
private fun sessionTitle(deviceModel: String): UiText =
    deviceModel.takeIf { it.isNotBlank() }?.let { UiText.Plain(it) }
        ?: UiText.resource(R.string.settings_session_unknown_device)

/**
 * "[first] · [second]", each isolated (`bidiIsolated`): a Hebrew city before an English time no longer swaps them. Or
 * the one that is not blank, on its own.
 */
private fun parts(
    first: UiText,
    second: UiText,
): UiText =
    when {
        first is UiText.Plain && first.text.isEmpty() -> second
        second is UiText.Plain && second.text.isEmpty() -> first
        else -> UiText.resource(R.string.settings_session_parts, first.bidiIsolated(), second.bidiIsolated())
    }
