package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable

/**
 * The floating menu of [state]'s open actions over the conversation ([MessageActionsMenu]), with the [lifted] copy of
 * their message where a long press left it ([MessageLiftState.anchor]), drawn with the list's [resources].
 */
@Composable
internal fun LiftedMessageMenu(
    state: ChatUiState,
    lifted: ChatItem.Bubble,
    lift: MessageLiftState,
    resources: BubbleResources,
    onEvent: (ChatEvent) -> Unit,
) {
    val actions = state.messageActions ?: return
    val anchor = lift.anchor ?: return
    MessageActionsMenu(actions, anchor, lifted.isOutgoing, lift, onEvent) { LiftedBubble(lifted, state, resources) }
}

/**
 * What shows over the conversation: a message's actions where they are not in the floating menu ([actionsInSheet]),
 * the report of messages with its [reportComment], "About this ad" of a sponsored message, and the confirmation before
 * messages are deleted.
 */
@Composable
internal fun ChatSheets(
    state: ChatUiState,
    onEvent: (ChatEvent) -> Unit,
    reportComment: TextFieldState,
    actionsInSheet: Boolean,
) {
    if (actionsInSheet) state.messageActions?.let { MessageActionsBottomSheet(it, onEvent) }
    state.report?.let { ReportMessagesSheet(it, reportComment, onEvent) }
    state.sponsoredSheet?.let { SponsoredMessageSheet(it, onEvent) }
    state.deleteDialog?.let { dialog ->
        DeleteMessagesAlert(
            dialog = dialog,
            onConfirm = { onEvent(ChatEvent.ConfirmDelete(it)) },
            onDismiss = { onEvent(ChatEvent.DismissDelete) },
        )
    }
}
