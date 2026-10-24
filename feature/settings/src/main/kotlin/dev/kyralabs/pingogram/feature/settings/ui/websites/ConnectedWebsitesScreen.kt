package dev.kyralabs.pingogram.feature.settings.ui.websites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsIconTone
import dev.kyralabs.pingogram.feature.settings.ui.SettingsItemIcon
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.destructiveItemColors
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import kotlinx.coroutines.launch

/**
 * The Websites page: hoists [ConnectedWebsitesViewModel], loads the websites again whenever the page shows, and shows
 * its results in snackbars.
 */
@Composable
internal fun ConnectedWebsitesRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectedWebsitesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(ConnectedWebsitesEvent.Refresh)
        onStopOrDispose {}
    }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            val message =
                when (effect) {
                    is ConnectedWebsitesEffect.Disconnected -> {
                        resources.getString(R.string.settings_website_disconnected, effect.domain.bidiIsolated())
                    }

                    ConnectedWebsitesEffect.AllDisconnected -> {
                        resources.getString(R.string.settings_websites_all_disconnected)
                    }

                    is ConnectedWebsitesEffect.ShowError -> {
                        errorMessage(resources, effect.error, showErrorDetails)
                    }
                }
            launch { snackbarHostState.showSnackbar(message) }
        }
    }
    ConnectedWebsitesScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Privacy and security → Websites (`docs/DESIGN_SYSTEM.md` §6, "Websites"): "Log out of all websites" in the error
 * role, then the websites, each its domain over the bot, browser and system and where and when it was last active,
 * with a "Log out" `TextButton` naming it; both log-outs ask first in an `AlertDialog`. Without websites, a note says
 * where they come from.
 */
@Composable
internal fun ConnectedWebsitesScreen(
    state: ConnectedWebsitesUiState,
    onEvent: (ConnectedWebsitesEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_websites),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (val websites = state.websites) {
            WebsitesState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_websites_loading))
            }

            is WebsitesState.Failed -> {
                SettingsError(websites.error, onRetry = { onEvent(ConnectedWebsitesEvent.Retry) })
            }

            is WebsitesState.Loaded -> {
                if (websites.websites.isEmpty()) {
                    NoWebsites()
                } else {
                    Websites(state, websites, onEvent)
                }
            }
        }
    }
    state.dialog?.let { dialog ->
        DisconnectConfirmation(
            dialog = dialog,
            onConfirm = { onEvent(ConnectedWebsitesEvent.Confirm) },
            onDismiss = { onEvent(ConnectedWebsitesEvent.Dismiss) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Websites(
    state: ConnectedWebsitesUiState,
    websites: WebsitesState.Loaded,
    onEvent: (ConnectedWebsitesEvent) -> Unit,
) {
    SettingsGroup {
        item(key = DISCONNECT_ALL) { shapes ->
            val disconnecting = stringResource(R.string.settings_websites_disconnecting_all)
            SettingsRow(
                title = stringResource(R.string.settings_websites_disconnect_all),
                shapes = shapes,
                icon = R.drawable.ic_logout,
                trailingContent =
                    if (state.isDisconnectingAll) {
                        { LoadingIndicator(Modifier.semantics { contentDescription = disconnecting }) }
                    } else {
                        null
                    },
                onClick = { onEvent(ConnectedWebsitesEvent.DisconnectAll) },
                enabled = !state.isDisconnectingAll,
                colors = destructiveItemColors(),
                iconTone = SettingsIconTone.Error,
            )
        }
    }
    SettingsGroup(
        title = stringResource(R.string.settings_section_websites),
        footer = stringResource(R.string.settings_websites_footer),
    ) {
        websites.websites.forEach { website ->
            item(key = website.id) { shapes ->
                WebsiteRow(
                    website = website,
                    isBusy = website.id in state.busyIds || state.isDisconnectingAll,
                    shapes = shapes,
                    onDisconnect = { onEvent(ConnectedWebsitesEvent.Disconnect(website)) },
                )
            }
        }
    }
}

/**
 * A website, a row of its group ([shapes]): the globe in the group's tonal circle, the domain, the bot, browser and
 * system, where and when it was last active, and "Log out" named with the domain, or progress while it logs out.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WebsiteRow(
    website: WebsiteItem,
    isBusy: Boolean,
    shapes: ListItemShapes,
    onDisconnect: () -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        leadingContent = { SettingsItemIcon(R.drawable.ic_public) },
        trailingContent = {
            if (isBusy) {
                val disconnecting = stringResource(R.string.settings_website_disconnecting, website.domain)
                LoadingIndicator(Modifier.semantics { contentDescription = disconnecting })
            } else {
                val disconnect = stringResource(R.string.settings_website_disconnect, website.domain)
                TextButton(
                    onClick = onDisconnect,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.semantics { contentDescription = disconnect },
                ) {
                    Text(stringResource(R.string.settings_website_disconnect_button))
                }
            }
        },
        supportingContent = {
            Column {
                Text(text = website.details.resolve(), style = LocalTextStyle.current.userContent)
                Text(text = website.activity.resolve(), style = LocalTextStyle.current.userContent)
            }
        },
        colors = settingsRowColors(),
    ) {
        Text(text = website.domain, style = LocalTextStyle.current.userContent)
    }
}

/** No website: what logs one in. */
@Composable
private fun NoWebsites() {
    Text(
        text = stringResource(R.string.settings_websites_none),
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun DisconnectConfirmation(
    dialog: DisconnectDialog,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val texts =
        when (dialog) {
            is DisconnectDialog.Website -> {
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_website_dialog_title),
                    text =
                        stringResource(R.string.settings_website_dialog_text, dialog.domain.bidiIsolated())
                            .bidiIsolated(),
                    confirm = stringResource(R.string.settings_website_disconnect_button),
                )
            }

            DisconnectDialog.All -> {
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_websites_dialog_title),
                    text = stringResource(R.string.settings_websites_dialog_text),
                    confirm = stringResource(R.string.settings_websites_dialog_confirm),
                )
            }
        }
    DestructiveDialog(icon = R.drawable.ic_public, texts = texts, onConfirm = onConfirm, onDismiss = onDismiss)
}

private const val DISCONNECT_ALL = "disconnectAll"
