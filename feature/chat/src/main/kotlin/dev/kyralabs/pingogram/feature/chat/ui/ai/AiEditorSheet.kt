package dev.kyralabs.pingogram.feature.chat.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.GrowingVisibility
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The AI editor's sheet (`docs/DESIGN_SYSTEM.md` §6 "AI text editor"): a [ModalBottomSheet] that opens expanded and
 * scrolls, at most `contentMaxWidth`, showing the one-time explanation ([AiConsent]) or the editor ([AiEditor]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiEditorSheet(
    state: AiEditorUiState,
    onEvent: (AiEditorEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(AiEditorEvent.Dismiss) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The one-time explanation gives way to the editor, crossfading in place.
            StateCrossfade(
                targetState = state,
                modifier = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth),
                contentKey = { it is AiEditorUiState.Consent },
                label = "aiEditor",
            ) { shown ->
                Column {
                    when (shown) {
                        AiEditorUiState.Consent -> AiConsent(onEvent)
                        is AiEditorUiState.Editor -> AiEditor(shown, onEvent)
                    }
                }
            }
        }
    }
}

/**
 * Shown once, before the first use: the edit symbol on a `Cookie9Sided` badge in `primaryContainer`, "Edit with AI" in
 * `titleLargeEmphasized` and what happens to the text in `bodyMedium` `onSurfaceVariant`, then "Not now" and
 * "Continue".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiConsent(onEvent: (AiEditorEvent) -> Unit) {
    val spacing = MaterialTheme.spacing
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        AiBadge()
        Text(
            text = stringResource(R.string.ai_editor_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Text(
            text = stringResource(R.string.ai_editor_consent_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onEvent(AiEditorEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.ai_editor_not_now))
            }
            Button(onClick = { onEvent(AiEditorEvent.AcceptConsent) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.ai_editor_continue), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AiBadge() {
    val shape = MaterialShapes.Cookie9Sided.toShape()
    Box(
        Modifier
            .size(MaterialTheme.sizes.headlineBadge)
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_edit),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * The editor: "Edit with AI" (a heading), Fix / Rewrite / Translate as connected [ToggleButton]s, the mode's choices
 * ([AiChoices]), the result ([AiResultCard]), a note that nothing reaches the chat before it is sent, then "Cancel",
 * the mode's action (the one tap that sends the text to Telegram's AI) and "Use".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun AiEditor(
    state: AiEditorUiState.Editor,
    onEvent: (AiEditorEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
        Text(
            text = stringResource(R.string.ai_editor_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        // The parts a mode or a result adds bring their own gap above them, so a hidden one leaves none.
        Column {
            ModeButtons(state.mode) { onEvent(AiEditorEvent.SetMode(it)) }
            AiChoices(state, onEvent)
            AiResultCard(state.result)
        }
        Text(
            text = stringResource(R.string.ai_editor_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            TextButton(onClick = { onEvent(AiEditorEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.ai_editor_cancel))
            }
            FilledTonalButton(
                onClick = { onEvent(AiEditorEvent.Run) },
                enabled = state.canRun,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(state.mode.actionLabel()))
            }
            Button(
                onClick = { onEvent(AiEditorEvent.Use) },
                enabled = state.result is AiResult.Done,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.ai_editor_use), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeButtons(
    mode: AiMode,
    onSelect: (AiMode) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        AiMode.entries.forEachIndexed { index, option ->
            val checked = option == mode
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                        onSelect(option)
                    }
                },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        AiMode.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors = colors,
            ) {
                val typography = MaterialTheme.typography
                Text(
                    text = stringResource(option.label()),
                    maxLines = 1,
                    style = if (checked) typography.labelLargeEmphasized else typography.labelLarge,
                )
            }
        }
    }
}

/**
 * What the mode lets the user choose: for a rewrite the style; for a translation the language ([LanguageField]) and
 * the style, "Keep its style" first; for both, "Add emoji" as a switch row. A fix has nothing to choose.
 */
@Composable
private fun AiChoices(
    state: AiEditorUiState.Editor,
    onEvent: (AiEditorEvent) -> Unit,
) {
    // What a mode adds grows in as it is chosen and shrinks away as another is.
    val gap = MaterialTheme.spacing.medium
    GrowingVisibility(visible = state.mode == AiMode.Translate) {
        Column(Modifier.padding(top = gap)) {
            LanguageField(state.language, state.languages) { onEvent(AiEditorEvent.SetLanguage(it)) }
        }
    }
    GrowingVisibility(visible = state.mode != AiMode.Fix) {
        Column(Modifier.padding(top = gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            StyleChips(
                styles = state.styles,
                selected = state.styleName,
                canKeep = state.mode == AiMode.Translate,
                onSelect = { onEvent(AiEditorEvent.SetStyle(it)) },
            )
            EmojiRow(state.addEmoji) { onEvent(AiEditorEvent.SetAddEmoji(it)) }
        }
    }
}

@Composable
private fun EmojiRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.ai_editor_add_emoji),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
        )
        CheckSwitch(checked = checked)
    }
}

private fun AiMode.label(): Int =
    when (this) {
        AiMode.Fix -> R.string.ai_editor_mode_fix
        AiMode.Rewrite -> R.string.ai_editor_mode_rewrite
        AiMode.Translate -> R.string.ai_editor_mode_translate
    }

private fun AiMode.actionLabel(): Int =
    when (this) {
        AiMode.Fix -> R.string.ai_editor_action_fix
        AiMode.Rewrite -> R.string.ai_editor_action_rewrite
        AiMode.Translate -> R.string.ai_editor_action_translate
    }
