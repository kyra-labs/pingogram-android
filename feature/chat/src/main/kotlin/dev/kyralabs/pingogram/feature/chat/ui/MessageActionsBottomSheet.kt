package dev.kyralabs.pingogram.feature.chat.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The actions of a long-pressed message (`REQUIREMENTS.md` FR-C6, FR-C7; `docs/DESIGN_SYSTEM.md` §6 "Message actions",
 * "Reactions picker"): a [ModalBottomSheet] with the quick reactions on top as a row of [ToggleButton]s, which scrolls
 * sideways when they do not fit, with a button at its end that opens the reactions picker where it shows more; then
 * the actions as list items, reporting and deleting in `error`.
 *
 * - **Reactions picker** ([ReactionPickerContent]), while [MessageActionsSheet.showsPicker]: it takes the place of the
 *   quick reactions and the actions, the sheet's content crossfading on the motion scheme's default effects springs
 *   while the sheet grows or shrinks to it on its default spatial spring. Its back button returns to the actions.
 * - Choosing a reaction or an action hides the sheet on its spring, then sends its event; a chosen reaction's button
 *   morphs from round to square (unchosen: back) with a toggle haptic meanwhile, on the motion scheme's spring.
 * - It scrolls where the window is too short for it.
 * - Dragging it down, tapping the scrim and the back gesture dismiss it ([ChatEvent.DismissMessageActions]); the back
 *   gesture is Material's predictive back, which shrinks the sheet as it goes, also from the picker.
 * - It is how screen readers that explore by touch, and windows too short for the floating menu, get the actions
 *   ([MessageLiftState]). Over the floating menu ([MessageActionsMenu]) it holds the reactions picker alone: [onClose]
 *   then closes the menu with the chosen reaction, and leaving the picker (its back button, dragging it down, the back
 *   gesture) returns to the menu ([ChatEvent.CollapseReactions]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MessageActionsBottomSheet(
    sheet: MessageActionsSheet,
    onEvent: (ChatEvent) -> Unit,
    onClose: ((ChatEvent) -> Unit)? = null,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnClose by rememberUpdatedState(onClose)
    val hideThen: ((ChatEvent) -> Unit) -> (ChatEvent) -> Unit =
        remember(sheetState, scope) {
            { then -> { event -> scope.launch { sheetState.hide() }.invokeOnCompletion { then(event) } } }
        }
    // Over the floating menu, a reaction closes the menu too, and leaving the picker returns to the menu.
    val choose: (ChatEvent) -> Unit =
        remember(hideThen) { hideThen { event -> (currentOnClose ?: currentOnEvent)(event) } }
    val collapse: () -> Unit =
        remember(hideThen) { { hideThen { event -> currentOnEvent(event) }(ChatEvent.CollapseReactions) } }
    val motion = MaterialTheme.motionScheme
    ModalBottomSheet(
        onDismissRequest = {
            currentOnEvent(if (currentOnClose != null) ChatEvent.CollapseReactions else ChatEvent.DismissMessageActions)
        },
        sheetState = sheetState,
    ) {
        AnimatedContent(
            targetState = sheet.showsPicker && sheet.picker != null,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            label = "reactionPicker",
        ) { showsPicker ->
            val picker = sheet.picker
            if (showsPicker && picker != null) {
                ReactionPickerContent(
                    sheet = sheet,
                    picker = picker,
                    choose = choose,
                    onBack = if (onClose != null) collapse else ({ currentOnEvent(ChatEvent.CollapseReactions) }),
                )
            } else {
                ActionsContent(sheet, choose, onExpand = { currentOnEvent(ChatEvent.ExpandReactions) })
            }
        }
    }
}

/** The quick reactions, where there are any or the picker has more, and the actions; scrolls where it is too tall. */
@Composable
private fun ActionsContent(
    sheet: MessageActionsSheet,
    choose: (ChatEvent) -> Unit,
    onExpand: () -> Unit,
) {
    // Scrolls where the window is too short for it: landscape, large font scales.
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(bottom = MaterialTheme.spacing.large),
    ) {
        if (sheet.reactions.isNotEmpty() || sheet.picker != null) QuickReactions(sheet, choose, onExpand)
        sheet.actions.forEach { action ->
            ActionItem(action, onClick = { choose(action.eventFor(sheet.messageId)) })
        }
    }
}

/**
 * The quick reactions ([ReactionToggleButton]s), scrolling sideways when they do not fit, and at the end, where the
 * sheet has a picker, a medium icon button in the reactions' container color that opens it, always in view.
 */
@Composable
private fun QuickReactions(
    sheet: MessageActionsSheet,
    choose: (ChatEvent) -> Unit,
    onExpand: () -> Unit,
) {
    val style = rememberReactionButtonStyle()
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(end = spacing.large, top = spacing.small, bottom = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
                    .padding(start = spacing.large),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            sheet.reactions.forEach { reaction ->
                ReactionToggleButton(
                    reaction = reaction,
                    picture = sheet.pictureOf(reaction.label),
                    style = style,
                    onToggle = { choose(ChatEvent.ToggleReaction(sheet.reactionsMessageId, reaction.label)) },
                )
            }
        }
        if (sheet.picker != null) MoreReactionsButton(onExpand)
    }
}

/** The button that opens the reactions picker: a medium filled icon button whose shape morphs while pressed. */
@Composable
internal fun MoreReactionsButton(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = scheme.surfaceContainerHighest,
                contentColor = scheme.onSurfaceVariant,
            ),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = stringResource(R.string.message_reactions_more),
            modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
        )
    }
}

/** One action: its icon and label in a clickable list item on the sheet's color; reporting and deleting in `error`. */
@Composable
private fun ActionItem(
    action: MessageAction,
    onClick: () -> Unit,
) {
    val label = stringResource(action.label)
    val destructive = action.isDestructive
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

/** Reporting and deleting, which menus show in `error`. */
internal val MessageAction.isDestructive: Boolean
    get() = this == MessageAction.Delete || this == MessageAction.Report

internal val MessageAction.label: Int
    @StringRes get() =
        when (this) {
            MessageAction.Resend -> R.string.message_action_resend
            MessageAction.Reply -> R.string.message_action_reply
            MessageAction.Copy -> R.string.message_action_copy
            MessageAction.Translate -> R.string.message_action_translate
            MessageAction.ShowOriginal -> R.string.message_action_show_original
            MessageAction.Forward -> R.string.message_action_forward
            MessageAction.Edit -> R.string.message_action_edit
            MessageAction.Pin -> R.string.message_action_pin
            MessageAction.PinForSelf -> R.string.message_action_pin_for_self
            MessageAction.Unpin -> R.string.message_action_unpin
            MessageAction.RetractVote -> R.string.message_action_retract_vote
            MessageAction.StopPoll -> R.string.message_action_stop_poll
            MessageAction.SaveToDownloads -> R.string.message_action_save_to_downloads
            MessageAction.Select -> R.string.message_action_select
            MessageAction.Report -> R.string.message_action_report
            MessageAction.Delete -> R.string.message_action_delete
        }

internal val MessageAction.icon: Int
    @DrawableRes get() =
        when (this) {
            MessageAction.Resend -> UiR.drawable.ic_refresh
            MessageAction.Reply -> UiR.drawable.ic_reply
            MessageAction.Copy -> UiR.drawable.ic_content_copy
            MessageAction.Translate, MessageAction.ShowOriginal -> R.drawable.ic_translate
            MessageAction.Forward -> UiR.drawable.ic_forward
            MessageAction.Edit -> UiR.drawable.ic_edit
            MessageAction.Pin, MessageAction.PinForSelf -> UiR.drawable.ic_push_pin
            MessageAction.Unpin -> UiR.drawable.ic_keep_off
            MessageAction.RetractVote -> UiR.drawable.ic_history
            MessageAction.StopPoll -> R.drawable.ic_block
            MessageAction.SaveToDownloads -> UiR.drawable.ic_download
            MessageAction.Select -> UiR.drawable.ic_check_circle
            MessageAction.Report -> UiR.drawable.ic_error
            MessageAction.Delete -> UiR.drawable.ic_delete
        }

/** The event of choosing [this] for message [messageId]. */
internal fun MessageAction.eventFor(messageId: Long): ChatEvent =
    when (this) {
        MessageAction.Resend -> ChatEvent.Resend(messageId)
        MessageAction.Reply -> ChatEvent.Reply(messageId)
        MessageAction.Copy -> ChatEvent.Copy(messageId)
        MessageAction.Translate -> TranslationEvent.Translate(messageId)
        MessageAction.ShowOriginal -> TranslationEvent.ShowOriginal(messageId)
        MessageAction.Forward -> ChatEvent.Forward(messageId)
        MessageAction.Edit -> ChatEvent.Edit(messageId)
        MessageAction.Pin -> ChatEvent.Pin(messageId)
        MessageAction.PinForSelf -> ChatEvent.Pin(messageId, onlyForSelf = true)
        MessageAction.Unpin -> ChatEvent.Unpin(messageId)
        MessageAction.RetractVote -> PollEvent.RetractVote(messageId)
        MessageAction.StopPoll -> PollEvent.StopPoll(messageId)
        MessageAction.SaveToDownloads -> SelectionEvent.SaveToDownloads(messageId)
        MessageAction.Select -> SelectionEvent.Select(messageId)
        MessageAction.Report -> SelectionEvent.Report(messageId)
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
