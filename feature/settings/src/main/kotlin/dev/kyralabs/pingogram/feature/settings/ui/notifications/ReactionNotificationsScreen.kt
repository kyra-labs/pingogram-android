package dev.kyralabs.pingogram.feature.settings.ui.notifications

import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSettings
import dev.kyralabs.pingogram.core.domain.model.ReactionNotificationSource
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.coroutines.launch

/** The reactions page: hoists [ReactionNotificationsViewModel]; a refused change is a snackbar. */
@Composable
internal fun ReactionNotificationsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReactionNotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ReactionNotificationsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ReactionNotificationsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Settings → Notifications → Reactions (`docs/DESIGN_SYSTEM.md` §6, "Notification settings"), a settings page without
 * row icons: "Reactions to my messages", "Reactions to my stories" and "Votes in my polls" as radio groups (Everyone,
 * My contacts, Nobody), then "Show the reaction" as a switch row, with a footer saying that these settings follow the
 * account to its other apps and that Pingogram does not show reaction notifications yet (TDLib does not pass them on).
 */
@Composable
internal fun ReactionNotificationsScreen(
    state: ReactionNotificationsUiState,
    onEvent: (ReactionNotificationsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_reactions),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        val settings = state.settings
        if (settings == null) {
            SettingsLoading(stringResource(R.string.settings_reactions_loading))
            return@SettingsPageScaffold
        }
        for (kind in ReactionNotificationKind.entries) {
            SourceGroup(kind, settings.sourceOf(kind)) { source ->
                onEvent(ReactionNotificationsEvent.Choose(kind, source))
            }
        }
        SettingsGroup(footer = stringResource(R.string.settings_reactions_footer)) {
            item { shapes ->
                SwitchItem(
                    title = stringResource(R.string.settings_reactions_preview),
                    summary = stringResource(R.string.settings_reactions_preview_summary),
                    checked = settings.showPreview,
                    shapes = shapes,
                    onCheckedChange = { onEvent(ReactionNotificationsEvent.SetShowPreview(it)) },
                )
            }
        }
    }
}

/** Whose reactions of [kind] notify, as radio rows under its header. */
@Composable
private fun SourceGroup(
    kind: ReactionNotificationKind,
    selected: ReactionNotificationSource,
    onChoose: (ReactionNotificationSource) -> Unit,
) {
    SettingsGroup(title = stringResource(kind.title), rowsModifier = Modifier.selectableGroup()) {
        for (source in Sources) {
            item(key = source) { shapes ->
                RadioItem(
                    label = stringResource(source.label),
                    selected = source == selected,
                    enabled = true,
                    shapes = shapes,
                    onClick = { onChoose(source) },
                )
            }
        }
    }
}

/** The choices from widest to narrowest, as Telegram's apps list them. */
private val Sources =
    listOf(ReactionNotificationSource.All, ReactionNotificationSource.Contacts, ReactionNotificationSource.None)

private val ReactionNotificationKind.title: Int
    get() =
        when (this) {
            ReactionNotificationKind.Messages -> R.string.settings_reactions_messages
            ReactionNotificationKind.Stories -> R.string.settings_reactions_stories
            ReactionNotificationKind.PollVotes -> R.string.settings_reactions_poll_votes
        }

private val ReactionNotificationSource.label: Int
    get() =
        when (this) {
            ReactionNotificationSource.All -> R.string.settings_reactions_everyone
            ReactionNotificationSource.Contacts -> R.string.settings_reactions_contacts
            ReactionNotificationSource.None -> R.string.settings_reactions_nobody
        }

private fun ReactionNotificationSettings.sourceOf(kind: ReactionNotificationKind) =
    when (kind) {
        ReactionNotificationKind.Messages -> messages
        ReactionNotificationKind.Stories -> stories
        ReactionNotificationKind.PollVotes -> pollVotes
    }
