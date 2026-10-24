package dev.kyralabs.pingogram.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.NotificationScope
import dev.kyralabs.pingogram.core.notifications.PingogramChannel
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch

/**
 * The Notifications page: hoists [NotificationSettingsViewModel], reads what Android allows whenever the page shows
 * (the user may have changed it in Android's settings meanwhile), shows the system's permission dialog or Android's
 * notification settings when the view model asks, and its results in snackbars. A row calls [onOpen] with the page it
 * opens; [onBack] leaves the page.
 */
@Composable
internal fun NotificationSettingsRoute(
    onOpen: (NotificationSettingsPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val check = rememberAccessCheck(onChecked = viewModel::onEvent)
    LifecycleResumeEffect(check) {
        check()
        onPauseOrDispose {}
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { check() }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is NotificationSettingsEffect.ShowError -> {
                    // Snackbars queue up; later effects must not wait for them.
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }

                NotificationSettingsEffect.ResetDone -> {
                    val message = resources.getString(R.string.settings_notifications_reset_done)
                    launch { snackbarHostState.showSnackbar(message) }
                }

                NotificationSettingsEffect.RequestPermission -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                NotificationSettingsEffect.OpenAppNotificationSettings -> {
                    context.openSystemSettings(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                    )
                }

                is NotificationSettingsEffect.OpenChannelSettings -> {
                    context.openSystemSettings(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, PingogramChannel.of(effect.scope).id),
                    )
                }
            }
        }
    }
    NotificationSettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpen = onOpen,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** A group per kind of chat once their settings are read, or why they are not. */
@Composable
private fun ScopeGroups(
    state: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit,
    onOpen: (NotificationSettingsPage) -> Unit,
) {
    when (val scopes = state.scopes) {
        ScopeSettingsState.Loading -> {
            SettingsLoading(stringResource(R.string.settings_notifications_loading))
        }

        is ScopeSettingsState.Error -> {
            SettingsError(scopes.error, onRetry = { onEvent(NotificationSettingsEvent.Retry) })
        }

        is ScopeSettingsState.Content -> {
            for (scope in NotificationScope.entries) {
                val settings = scopes.settings[scope] ?: continue
                ScopeGroup(
                    scope = scope,
                    settings = settings,
                    channelBlocked = scope in state.blockedChannels,
                    onEvent = onEvent,
                    onOpenExceptions = { onOpen(NotificationSettingsPage.Exceptions(scope)) },
                )
            }
        }
    }
}

/**
 * Notifications (`REQUIREMENTS.md` FR-S1, FR-N1; `docs/DESIGN_SYSTEM.md` §6, "Settings rows"):
 * - when Android shows none of Pingogram's notifications, a banner first that turns them on ([NotificationsOffItem]);
 * - private chats, groups and channels, each with its defaults ([ScopeGroup]): notifications, message previews,
 *   mentions and pinned messages where they apply, "Sound and vibration" in Android's settings (sounds belong to the
 *   notification channels there), and the chats with settings of their own;
 * - "Reactions", whom the account hears of reactions and poll votes from ([NotificationSettingsPage.Reactions]);
 * - "Keep connected", where the build offers it, with its cost in battery said under the switch;
 * - "Reset all notification settings", after a destructive confirmation.
 */
@Composable
internal fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    onEvent: (NotificationSettingsEvent) -> Unit,
    onOpen: (NotificationSettingsPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_notifications),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        if (state.notificationsBlocked) {
            SettingsGroup {
                item { shapes ->
                    NotificationsOffItem(shapes, onClick = { onEvent(NotificationSettingsEvent.TurnOnNotifications) })
                }
            }
        }
        ScopeGroups(state, onEvent, onOpen)
        ReactionsGroup(
            contactJoined = state.contactJoined,
            onOpenReactions = { onOpen(NotificationSettingsPage.Reactions) },
            onContactJoinedChange = { onEvent(NotificationSettingsEvent.SetContactJoined(it)) },
        )
        if (state.isKeepConnectedOffered) {
            KeepConnectedGroup(
                enabled = state.keepConnected,
                onCheckedChange = { onEvent(NotificationSettingsEvent.SetKeepConnected(it)) },
            )
        }
        SettingsGroup {
            item { shapes ->
                ResetNotificationsItem(
                    isResetting = state.isResetting,
                    shapes = shapes,
                    onClick = { onEvent(NotificationSettingsEvent.Reset) },
                )
            }
        }
    }
    if (state.isResetDialogOpen) {
        ResetNotificationsDialog(
            onConfirm = { onEvent(NotificationSettingsEvent.ConfirmReset) },
            onDismiss = { onEvent(NotificationSettingsEvent.DismissReset) },
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenPreview() {
    PingogramPreview {
        NotificationSettingsScreen(state = SettingsPreviewData.notifications, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun NotificationSettingsScreenExpandedPreview() {
    PingogramPreview {
        NotificationSettingsScreen(state = SettingsPreviewData.notifications, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenBlockedPreview() {
    PingogramPreview {
        NotificationSettingsScreen(
            state = SettingsPreviewData.notificationsBlocked,
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenWithoutKeepConnectedPreview() {
    PingogramPreview {
        NotificationSettingsScreen(
            state = SettingsPreviewData.notifications.copy(isKeepConnectedOffered = false),
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenLoadingPreview() {
    PingogramPreview {
        NotificationSettingsScreen(state = NotificationSettingsUiState(), onEvent = {}, onOpen = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenErrorPreview() {
    PingogramPreview {
        NotificationSettingsScreen(
            state = NotificationSettingsUiState(scopes = ScopeSettingsState.Error(DomainError.Network)),
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}

@PingogramPreviews
@Composable
private fun NotificationSettingsScreenResetDialogPreview() {
    PingogramPreview {
        NotificationSettingsScreen(
            state = SettingsPreviewData.notifications.copy(isResetDialogOpen = true),
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}
