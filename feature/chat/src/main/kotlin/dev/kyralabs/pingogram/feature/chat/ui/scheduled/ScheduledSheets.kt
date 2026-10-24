package dev.kyralabs.pingogram.feature.chat.ui.scheduled

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.preview.ScheduledPreviewData
import kotlinx.coroutines.launch
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The actions of a scheduled message (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6 "Scheduled messages"), as
 * the conversation's actions sheet shows a message's: a [ModalBottomSheet] headed by when it goes out ("Sends Today,
 * 9:41 AM" in `titleMedium`, a heading for screen readers, how often it repeats in `bodyMedium` `onSurfaceVariant`,
 * beside the schedule-send symbol), a divider, then the actions as transparent list items with their outlined icons,
 * "Delete" in `error`. Choosing one hides the sheet on its spring, then sends its event; dragging it down, the scrim
 * and the predictive back gesture dismiss it ([ScheduledEvent.DismissActions]). It scrolls where the window is too
 * short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduledActionsBottomSheet(
    sheet: ScheduledActionsSheet,
    onEvent: (ScheduledEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val currentOnEvent by rememberUpdatedState(onEvent)
    val choose: (ScheduledEvent) -> Unit =
        remember(sheetState, scope) {
            { event -> scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnEvent(event) } }
        }
    ModalBottomSheet(
        onDismissRequest = { currentOnEvent(ScheduledEvent.DismissActions) },
        sheetState = sheetState,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = MaterialTheme.spacing.large)) {
            SheetHeader(sheet)
            HorizontalDivider(
                modifier = Modifier.padding(vertical = MaterialTheme.spacing.small),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            sheet.actions.forEach { action ->
                ActionItem(action, onClick = { choose(ScheduledEvent.Act(sheet.messageId, action)) })
            }
        }
    }
}

/** When the message goes out, and how often it repeats where it does, beside the schedule-send symbol. */
@Composable
private fun SheetHeader(sheet: ScheduledActionsSheet) {
    val resources = LocalResources.current
    ListItem(
        leadingContent = {
            Icon(painterResource(R.drawable.ic_schedule_send), contentDescription = null)
        },
        supportingContent =
            if (sheet.repeatPeriod > 0) {
                { Text(repeatLabel(resources, sheet.repeatPeriod)) }
            } else {
                null
            },
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                leadingContentColor = MaterialTheme.colorScheme.primary,
            ),
    ) {
        Text(
            text = sheet.sendsAt.resolve(),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

/** One action: its icon and label in a list item on the sheet's color; deleting in `error`. */
@Composable
private fun ActionItem(
    action: ScheduledAction,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val destructive = action == ScheduledAction.Delete
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
        Text(stringResource(action.label))
    }
}

private val ScheduledAction.label: Int
    @StringRes get() =
        when (this) {
            ScheduledAction.SendNow -> R.string.scheduled_action_send_now
            ScheduledAction.Reschedule -> R.string.scheduled_action_reschedule
            ScheduledAction.Edit -> R.string.scheduled_action_edit
            ScheduledAction.Copy -> R.string.scheduled_action_copy
            ScheduledAction.Delete -> R.string.scheduled_action_delete
        }

private val ScheduledAction.icon: Int
    @DrawableRes get() =
        when (this) {
            ScheduledAction.SendNow -> UiR.drawable.ic_send_filled
            ScheduledAction.Reschedule -> UiR.drawable.ic_schedule
            ScheduledAction.Edit -> UiR.drawable.ic_edit
            ScheduledAction.Copy -> UiR.drawable.ic_content_copy
            ScheduledAction.Delete -> UiR.drawable.ic_delete
        }

/**
 * The confirmation before a scheduled message is deleted (`docs/DESIGN_SYSTEM.md` §6: an [AlertDialog], for
 * destructive confirmations only): "Delete scheduled message?", that it will not be sent, and the confirm button in the
 * `error` color.
 */
@Composable
internal fun ScheduledDeleteAlert(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.scheduled_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.scheduled_delete_cancel))
            }
        },
        icon = { Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.scheduled_delete_title)) },
        text = { Text(stringResource(R.string.scheduled_delete_body)) },
    )
}

/**
 * The composer's date and time pickers ([ScheduleDialog]) for [dialog]'s message, starting at its current time;
 * [onReschedule] gets the new one in Unix time (seconds).
 */
@Composable
internal fun RescheduleDialogHost(
    dialog: RescheduleDialog,
    onReschedule: (messageId: Long, sendDate: Long) -> Unit,
    onDismiss: () -> Unit,
    now: () -> Instant = Instant::now,
) {
    ScheduleDialog(
        now = now,
        onDismiss = onDismiss,
        onSchedule = { at -> onReschedule(dialog.messageId, at.epochSecond) },
        initial = dialog.sendDate?.let(Instant::ofEpochSecond),
    )
}

@PingogramPreviews
@Composable
private fun ScheduledActionsBottomSheetPreview() {
    PingogramPreview { ScheduledActionsBottomSheet(ScheduledPreviewData.actions, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun ScheduledDeleteAlertPreview() {
    PingogramPreview { ScheduledDeleteAlert(onConfirm = {}, onDismiss = {}) }
}
