package dev.kyralabs.pingogram.feature.settings.ui.reactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.SwitchItem
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/** The double-tap page: hoists [QuickReactionViewModel]; a refused change is a snackbar. */
@Composable
internal fun QuickReactionRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuickReactionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is QuickReactionEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    QuickReactionScreen(state, viewModel::onEvent, onBack, modifier, snackbarHostState)
}

/**
 * Settings → Appearance → Chats → "Double-tap to react" (`docs/DESIGN_SYSTEM.md` §6, "Double-tap to react"), a
 * settings page without row icons: "Quick reaction", the emoji Telegram offers as `IconToggleButton`s wrapping in a
 * segment (one radio group for screen readers, the chosen one `secondaryContainer`), with a footer saying that it
 * follows the account; then "Double tap" with a switch row each for others' and the user's own messages.
 */
@Composable
internal fun QuickReactionScreen(
    state: QuickReactionUiState,
    onEvent: (QuickReactionEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_double_tap),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            QuickReactionUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_double_tap_loading))
            }

            is QuickReactionUiState.Content -> {
                ReactionGroup(state) { onEvent(QuickReactionEvent.Choose(it)) }
                DoubleTapGroup(state, onEvent)
            }
        }
    }
}

/** The emoji to pick the quick reaction from. */
@Composable
private fun ReactionGroup(
    state: QuickReactionUiState.Content,
    onChoose: (String) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_quick_reaction),
        footer =
            stringResource(
                if (state.isCustomEmoji) {
                    R.string.settings_quick_reaction_custom
                } else {
                    R.string.settings_quick_reaction_footer
                },
            ),
    ) {
        item { shapes ->
            SettingsSegment(shapes) {
                ReactionChoices(state.reactions, state.chosen, onChoose)
            }
        }
    }
}

@Composable
private fun ReactionChoices(
    reactions: ImmutableList<String>,
    chosen: String?,
    onChoose: (String) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors =
        IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    FlowRow(
        modifier = Modifier.padding(MaterialTheme.spacing.small).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        for (emoji in reactions) {
            val checked = emoji == chosen
            IconToggleButton(
                checked = checked,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onChoose(emoji)
                },
                shapes = IconButtonDefaults.toggleableShapes(),
                colors = colors,
                modifier =
                    Modifier.semantics {
                        role = Role.RadioButton
                        selected = checked
                        contentDescription = emoji
                    },
            ) {
                Text(emoji, style = MaterialTheme.typography.titleLarge)
            }
        }
    }
}

/** Where a double tap adds the quick reaction. */
@Composable
private fun DoubleTapGroup(
    state: QuickReactionUiState.Content,
    onEvent: (QuickReactionEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_double_tap_section),
        footer = stringResource(R.string.settings_double_tap_footer),
    ) {
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_double_tap_incoming),
                summary = stringResource(R.string.settings_double_tap_incoming_summary),
                checked = state.doubleTap.incoming,
                shapes = shapes,
                onCheckedChange = { onEvent(QuickReactionEvent.SetIncoming(it)) },
            )
        }
        item { shapes ->
            SwitchItem(
                title = stringResource(R.string.settings_double_tap_outgoing),
                summary = stringResource(R.string.settings_double_tap_outgoing_summary),
                checked = state.doubleTap.outgoing,
                shapes = shapes,
                onCheckedChange = { onEvent(QuickReactionEvent.SetOutgoing(it)) },
            )
        }
    }
}
