package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The Active sessions page: hoists [ActiveSessionsViewModel], loads the sessions again whenever the page shows, and
 * shows its results in snackbars. [onBack] leaves the page.
 */
@Composable
internal fun ActiveSessionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActiveSessionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ActiveSessionsEvent.Refresh)
        onStopOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    ActiveSessionsEffect.SessionTerminated -> {
                        resources.getString(R.string.settings_session_terminated)
                    }

                    ActiveSessionsEffect.OtherSessionsTerminated -> {
                        resources.getString(R.string.settings_other_sessions_terminated)
                    }

                    ActiveSessionsEffect.LoginConfirmed -> {
                        resources.getString(R.string.settings_session_confirmed)
                    }

                    ActiveSessionsEffect.TerminationRefused -> {
                        resources.getString(R.string.settings_terminate_refused)
                    }

                    is ActiveSessionsEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            // Snackbars queue up; later effects must not wait for them.
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    ActiveSessionsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Active sessions (`REQUIREMENTS.md` FR-S1): this device and "Terminate all other sessions", the new logins with
 * "Yes, it's me" and "No, terminate", then the other sessions, each of which a tap offers to terminate. Each
 * session shows its device, app and system, and where and when it was last active. Terminations are confirmed in an
 * `AlertDialog` first (destructive).
 */
@Composable
internal fun ActiveSessionsScreen(
    state: ActiveSessionsUiState,
    onEvent: (ActiveSessionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_active_sessions),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (val sessions = state.sessions) {
            SessionsState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_sessions_loading))
            }

            is SessionsState.Failed -> {
                SettingsError(sessions.error, onRetry = { onEvent(ActiveSessionsEvent.Retry) })
            }

            is SessionsState.Loaded -> {
                Sessions(state, sessions, onEvent)
            }
        }
    }
    state.dialog?.let { dialog ->
        TerminationDialog(
            dialog = dialog,
            onConfirm = { onEvent(ActiveSessionsEvent.ConfirmTermination) },
            onDismiss = { onEvent(ActiveSessionsEvent.DismissTermination) },
        )
    }
}

@Composable
private fun Sessions(
    state: ActiveSessionsUiState,
    sessions: SessionsState.Loaded,
    onEvent: (ActiveSessionsEvent) -> Unit,
) {
    val ttl = stringResource(R.string.settings_sessions_ttl, ttlText(sessions.inactiveSessionTtlDays))
    val hasOthers = sessions.unconfirmed.isNotEmpty() || sessions.others.isNotEmpty()
    SettingsGroup(
        title = stringResource(R.string.settings_section_this_device),
        footer = if (sessions.others.isEmpty()) ttl else null,
    ) {
        sessions.current?.let { current ->
            item(key = current.id) { shapes -> SessionRow(current, isBusy = false, shapes = shapes) }
        }
        if (hasOthers) {
            item(key = TERMINATE_ALL) { shapes ->
                TerminateAllItem(
                    isTerminating = state.isTerminatingAll,
                    shapes = shapes,
                    onClick = { onEvent(ActiveSessionsEvent.TerminateAllOthers) },
                )
            }
        }
    }
    if (sessions.unconfirmed.isNotEmpty()) {
        SettingsGroup(
            title = stringResource(R.string.settings_section_new_logins),
            footer = stringResource(R.string.settings_new_logins_note),
            iconTone = SettingsIconTone.Tertiary,
        ) {
            sessions.unconfirmed.forEach { session ->
                item(key = session.id) { shapes ->
                    NewLogin(
                        session = session,
                        isBusy = session.id in state.busySessionIds || state.isTerminatingAll,
                        shapes = shapes,
                        onConfirm = { onEvent(ActiveSessionsEvent.ConfirmLogin(session.id)) },
                        onTerminate = { onEvent(ActiveSessionsEvent.Terminate(session)) },
                    )
                }
            }
        }
    }
    if (sessions.others.isNotEmpty()) {
        SettingsGroup(
            title = stringResource(R.string.settings_section_other_sessions),
            footer = ttl,
            iconTone = SettingsIconTone.Secondary,
        ) {
            sessions.others.forEach { session ->
                item(key = session.id) { shapes ->
                    SessionRow(
                        session = session,
                        isBusy = session.id in state.busySessionIds || state.isTerminatingAll,
                        shapes = shapes,
                        onClick = { onEvent(ActiveSessionsEvent.Terminate(session)) },
                    )
                }
            }
        }
    }
}

@PingogramPreviews
@Composable
private fun ActiveSessionsScreenPreview() {
    PingogramPreview { ActiveSessionsScreen(state = SettingsPreviewData.sessions, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ActiveSessionsScreenOnlyThisDevicePreview() {
    PingogramPreview { ActiveSessionsScreen(state = SettingsPreviewData.onlyThisDevice, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ActiveSessionsScreenDialogPreview() {
    PingogramPreview {
        ActiveSessionsScreen(
            state = SettingsPreviewData.sessions.copy(dialog = TerminateDialog.AllOthers),
            onEvent = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun ActiveSessionsScreenLoadingPreview() {
    PingogramPreview { ActiveSessionsScreen(state = ActiveSessionsUiState(), onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun ActiveSessionsScreenErrorPreview() {
    PingogramPreview {
        ActiveSessionsScreen(
            state = ActiveSessionsUiState(sessions = SessionsState.Failed(DomainError.Network)),
            onEvent = {},
            onBack = {},
        )
    }
}

/** The key of "Terminate all other sessions" among the session rows, which are keyed by session id. */
private const val TERMINATE_ALL = "terminateAll"
