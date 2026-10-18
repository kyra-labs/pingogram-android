package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The Settings tab: hoists [SettingsViewModel] and shows its errors in snackbars. */
@Composable
internal fun SettingsRoute(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is SettingsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    SettingsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Settings until the full set lands (`docs/ROADMAP.md` M3): the account section with "Log out", which asks for
 * confirmation first (FR-A8), above an empty state for everything else.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding),
        ) {
            AccountSection(isLoggingOut = state.isLoggingOut, onLogOut = { onEvent(SettingsEvent.LogOut) })
            // Takes the remaining height and scrolls by itself when that is too little.
            EmptyState(
                illustration = MaterialShapes.Sunny,
                icon = UiR.drawable.ic_settings_filled,
                title = stringResource(R.string.settings_coming_soon_title),
                body = stringResource(R.string.settings_coming_soon_body),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
    if (state.isLogOutDialogOpen) {
        LogOutDialog(
            onConfirm = { onEvent(SettingsEvent.ConfirmLogOut) },
            onDismiss = { onEvent(SettingsEvent.DismissLogOut) },
        )
    }
}

/** A section header (`titleSmallEmphasized` in `primary`) over its rows in a `surfaceContainer` card. */
@Composable
private fun AccountSection(
    isLoggingOut: Boolean,
    onLogOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = MaterialTheme.spacing.windowMarginCompact)) {
        Text(
            text = stringResource(R.string.settings_section_account),
            modifier =
                Modifier
                    .padding(
                        start = MaterialTheme.spacing.large,
                        top = MaterialTheme.spacing.small,
                        bottom = MaterialTheme.spacing.small,
                    ).semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
            LogOutItem(isLoggingOut = isLoggingOut, onClick = onLogOut)
        }
    }
}

/** Destructive, so in the error role; disabled with progress while logging out. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LogOutItem(
    isLoggingOut: Boolean,
    onClick: () -> Unit,
) {
    val loggingOut = stringResource(R.string.settings_logging_out)
    ListItem(
        onClick = onClick,
        enabled = !isLoggingOut,
        leadingContent = { Icon(painter = painterResource(R.drawable.ic_logout), contentDescription = null) },
        trailingContent =
            if (isLoggingOut) {
                { LoadingIndicator(Modifier.semantics { contentDescription = loggingOut }) }
            } else {
                null
            },
        supportingContent = { Text(stringResource(R.string.settings_log_out_summary)) },
        colors =
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.error,
                leadingContentColor = MaterialTheme.colorScheme.error,
            ),
    ) {
        Text(stringResource(R.string.settings_log_out))
    }
}

/** The destructive confirmation (`docs/DESIGN_SYSTEM.md` §6: `AlertDialog` for destructive confirmations only). */
@Composable
private fun LogOutDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    text = stringResource(R.string.settings_log_out_dialog_confirm),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.settings_log_out_dialog_dismiss),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(R.drawable.ic_logout), contentDescription = null) },
        title = { Text(stringResource(R.string.settings_log_out_dialog_title)) },
        text = { Text(stringResource(R.string.settings_log_out_dialog_text)) },
    )
}

@PingogramPreviews
@Composable
private fun SettingsScreenPreview() {
    PingogramPreview { SettingsScreen(state = SettingsUiState(), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SettingsScreenLogOutDialogPreview() {
    PingogramPreview { SettingsScreen(state = SettingsUiState(isLogOutDialogOpen = true), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SettingsScreenLoggingOutPreview() {
    PingogramPreview { SettingsScreen(state = SettingsUiState(isLoggingOut = true), onEvent = {}) }
}
