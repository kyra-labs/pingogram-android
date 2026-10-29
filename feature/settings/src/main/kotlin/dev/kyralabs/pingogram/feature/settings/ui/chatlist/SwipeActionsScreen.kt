package dev.kyralabs.pingogram.feature.settings.ui.chatlist

import androidx.annotation.StringRes
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.repository.SwipeAction
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.coroutines.launch

/** The Swipe actions page: hoists [SwipeActionsViewModel]; a choice that cannot be saved is a snackbar. */
@Composable
internal fun SwipeActionsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SwipeActionsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SwipeActionsEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    SwipeActionsScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Settings → Appearance → Chat list → "Swipe actions" (`docs/DESIGN_SYSTEM.md` §6, "Swipe actions"), a settings
 * page without row icons: first "Swipe to change folders" as a switch row, its footer saying what it does; then
 * "Swipe right" and "Swipe left" ("Swipe left" first in right-to-left layouts, where a swipe toward the end goes left),
 * each a radio group of what the swipe does, with a `SegmentTick` per pick, disabled while swiping changes the folder;
 * the footer says that screen readers offer every action of a chat whatever the swipes do.
 */
@Composable
internal fun SwipeActionsScreen(
    state: SwipeActionsUiState,
    onEvent: (SwipeActionsEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_swipe_actions),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            SwipeActionsUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_swipe_actions_loading))
            }

            is SwipeActionsUiState.Content -> {
                SettingsGroup(
                    title = stringResource(R.string.settings_swipe_folders),
                    footer = stringResource(R.string.settings_swipe_changes_folder_footer),
                ) {
                    item { shapes ->
                        SwitchItem(
                            title = stringResource(R.string.settings_swipe_changes_folder),
                            summary = stringResource(R.string.settings_swipe_changes_folder_summary),
                            checked = state.changesFolder,
                            shapes = shapes,
                            onCheckedChange = { onEvent(SwipeActionsEvent.SetChangesFolder(it)) },
                        )
                    }
                }
                SwipeGroup(SwipeSide.End, state.start, enabled = !state.changesFolder, footer = null, onEvent)
                SwipeGroup(
                    SwipeSide.Start,
                    state.end,
                    enabled = !state.changesFolder,
                    footer = stringResource(R.string.settings_swipe_actions_footer),
                    onEvent,
                )
            }
        }
    }
}

/** The actions a swipe toward [side] can do, the [chosen] one selected; none can be picked unless [enabled]. */
@Composable
private fun SwipeGroup(
    side: SwipeSide,
    chosen: SwipeAction,
    enabled: Boolean,
    footer: String?,
    onEvent: (SwipeActionsEvent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    SettingsGroup(
        title = stringResource(side.titleRes(LocalLayoutDirection.current)),
        footer = footer,
        rowsModifier = Modifier.selectableGroup(),
    ) {
        SwipeAction.entries.forEach { action ->
            item(key = action) { shapes ->
                RadioItem(
                    label = stringResource(action.labelRes()),
                    selected = action == chosen,
                    enabled = enabled,
                    shapes = shapes,
                    onClick = {
                        if (action != chosen) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onEvent(SwipeActionsEvent.Choose(side, action))
                        }
                    },
                )
            }
        }
    }
}

/** "Swipe right" or "Swipe left": a swipe toward the end goes right in left-to-right layouts, left in right-to-left. */
@StringRes
private fun SwipeSide.titleRes(direction: LayoutDirection): Int {
    val towardRight = (this == SwipeSide.End) == (direction == LayoutDirection.Ltr)
    return if (towardRight) R.string.settings_swipe_right else R.string.settings_swipe_left
}

/** What the action does, as the page lists it. */
@StringRes
internal fun SwipeAction.labelRes(): Int =
    when (this) {
        SwipeAction.Read -> R.string.settings_swipe_action_read
        SwipeAction.Archive -> R.string.settings_swipe_action_archive
        SwipeAction.Pin -> R.string.settings_swipe_action_pin
        SwipeAction.Mute -> R.string.settings_swipe_action_mute
        SwipeAction.Delete -> R.string.settings_swipe_action_delete
        SwipeAction.None -> R.string.settings_swipe_action_none
    }

/** The action's short name, in Appearance's "Swipe actions" summary. */
@StringRes
internal fun SwipeAction.shortLabelRes(): Int =
    when (this) {
        SwipeAction.Read -> R.string.settings_swipe_short_read
        SwipeAction.Archive -> R.string.settings_swipe_short_archive
        SwipeAction.Pin -> R.string.settings_swipe_short_pin
        SwipeAction.Mute -> R.string.settings_swipe_short_mute
        SwipeAction.Delete -> R.string.settings_swipe_short_delete
        SwipeAction.None -> R.string.settings_swipe_short_none
    }
