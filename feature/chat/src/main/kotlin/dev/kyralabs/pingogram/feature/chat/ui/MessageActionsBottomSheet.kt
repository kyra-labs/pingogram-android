package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The actions of a long-pressed message (`REQUIREMENTS.md` FR-C6, FR-C7; `docs/DESIGN_SYSTEM.md` §6 "Message actions"):
 * a [ModalBottomSheet] with the quick reactions on top as a row of [ToggleButton]s, which scrolls sideways when they do
 * not fit, then the actions as list items, deleting in `error`.
 *
 * - Choosing a reaction or an action hides the sheet on its spring, then sends its event; a chosen reaction's button
 *   morphs from round to square (unchosen: back) with a toggle haptic meanwhile, on the motion scheme's spring.
 * - It scrolls where the window is too short for it.
 * - Dragging it down, tapping the scrim and the back gesture dismiss it ([ChatEvent.DismissMessageActions]); the back
 *   gesture is Material's predictive back, which shrinks the sheet as it goes.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MessageActionsBottomSheet(
    sheet: MessageActionsSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val choose: (ChatEvent) -> Unit =
        remember(sheetState, scope) {
            { event -> scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnEvent(event) } }
        }
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(ChatEvent.DismissMessageActions) },
        sheetState = sheetState,
    ) {
        // Scrolls where the window is too short for it: landscape, large font scales.
        Column(
            modifier =
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = MaterialTheme.spacing.large),
        ) {
            if (sheet.reactions.isNotEmpty()) QuickReactions(sheet, choose)
            sheet.actions.forEach { action ->
                ActionItem(action, onClick = { choose(action.eventFor(sheet.messageId)) })
            }
        }
    }
}

/**
 * The quick reactions: medium [ToggleButton]s in Material's shapes for that height, which morph between round and
 * square as they are checked, `surfaceContainerHighest` unchosen and `secondaryContainer` chosen like the chips under
 * bubbles. A tapped one changes at once, while the sheet hides.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun QuickReactions(
    sheet: MessageActionsSheet,
    choose: (ChatEvent) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val resources = LocalResources.current
    val scheme = MaterialTheme.colorScheme
    val height = ButtonDefaults.MediumContainerHeight
    val shapes = ToggleButtonDefaults.shapesFor(height)
    val colors =
        ToggleButtonDefaults.colors(
            containerColor = scheme.surfaceContainerHighest,
            contentColor = scheme.onSurface,
            checkedContainerColor = scheme.secondaryContainer,
            checkedContentColor = scheme.onSecondaryContainer,
        )
    val spacing = MaterialTheme.spacing
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        sheet.reactions.forEach { reaction ->
            var checked by remember(reaction) { mutableStateOf(reaction.isChosen) }
            val description = reactionToggleLabel(resources, reaction.label, reaction.isChosen)
            ToggleButton(
                checked = checked,
                onCheckedChange = { now ->
                    checked = now
                    val haptic = if (now) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff
                    haptics.performHapticFeedback(haptic)
                    choose(ChatEvent.ToggleReaction(sheet.reactionsMessageId, reaction.label))
                },
                modifier = Modifier.heightIn(min = height).semantics { contentDescription = description },
                shapes = shapes,
                colors = colors,
                contentPadding = ButtonDefaults.SmallContentPadding,
            ) {
                val placeholder = if (checked) colors.checkedContentColor else colors.contentColor
                ReactionGlyph(reaction.label, MaterialTheme.typography.titleLarge, MaterialTheme.sizes.inlineIcon) {
                    placeholder
                }
            }
        }
    }
}

/** One action: its icon and label in a clickable list item on the sheet's color; deleting in `error`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActionItem(
    action: MessageAction,
    onClick: () -> Unit,
) {
    val label = stringResource(action.label)
    val destructive = action == MessageAction.Delete
    val scheme = MaterialTheme.colorScheme
    ListItem(
        onClick = onClick,
        leadingContent = { Icon(painterResource(action.icon), contentDescription = null) },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = if (destructive) scheme.error else scheme.onSurface,
                leadingContentColor = if (destructive) scheme.error else scheme.onSurfaceVariant,
            ),
    ) {
        Text(label)
    }
}

private val MessageAction.label: Int
    @StringRes get() =
        when (this) {
            MessageAction.Resend -> R.string.message_action_resend
            MessageAction.Reply -> R.string.message_action_reply
            MessageAction.Copy -> R.string.message_action_copy
            MessageAction.Edit -> R.string.message_action_edit
            MessageAction.Pin -> R.string.message_action_pin
            MessageAction.PinForSelf -> R.string.message_action_pin_for_self
            MessageAction.Unpin -> R.string.message_action_unpin
            MessageAction.Delete -> R.string.message_action_delete
        }

private val MessageAction.icon: Int
    @DrawableRes get() =
        when (this) {
            MessageAction.Resend -> UiR.drawable.ic_refresh
            MessageAction.Reply -> UiR.drawable.ic_reply
            MessageAction.Copy -> UiR.drawable.ic_content_copy
            MessageAction.Edit -> UiR.drawable.ic_edit
            MessageAction.Pin, MessageAction.PinForSelf -> UiR.drawable.ic_push_pin
            MessageAction.Unpin -> UiR.drawable.ic_keep_off
            MessageAction.Delete -> UiR.drawable.ic_delete
        }

/** The event of choosing [this] for message [messageId]. */
private fun MessageAction.eventFor(messageId: Long): ChatEvent =
    when (this) {
        MessageAction.Resend -> ChatEvent.Resend(messageId)
        MessageAction.Reply -> ChatEvent.Reply(messageId)
        MessageAction.Copy -> ChatEvent.Copy(messageId)
        MessageAction.Edit -> ChatEvent.Edit(messageId)
        MessageAction.Pin -> ChatEvent.Pin(messageId)
        MessageAction.PinForSelf -> ChatEvent.Pin(messageId, onlyForSelf = true)
        MessageAction.Unpin -> ChatEvent.Unpin(messageId)
        MessageAction.Delete -> ChatEvent.Delete(messageId)
    }

@PingogramPreviews
@Composable
private fun MessageActionsOwnPreview() {
    PingogramPreview {
        MessageActionsBottomSheet(ChatPreviewData.ownMessageActions, onEvent = {})
    }
}

@PingogramPreviews
@Composable
private fun MessageActionsIncomingPreview() {
    PingogramPreview {
        MessageActionsBottomSheet(ChatPreviewData.incomingMessageActions, onEvent = {})
    }
}
