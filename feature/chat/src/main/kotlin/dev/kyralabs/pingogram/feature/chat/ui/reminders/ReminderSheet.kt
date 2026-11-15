package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import java.time.Instant

/**
 * The reminder sheet (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29b): a `ModalBottomSheet` that
 * opens expanded, at most `contentMaxWidth`: the alarm on a `MaterialShapes.Cookie9Sided` badge in `primaryContainer`
 * beside "Remind me" in `titleLargeEmphasized` (a heading); what the message says on `surfaceContainerHigh` with
 * `large` corners; "When" over the quick choices as one radio group, each reading its time ("In 1 hour", "Today, 10:41
 * AM"), and "Pick a date and time", which opens the composer's date and time pickers; the note in the rounded tonal
 * field; that it stays on this phone; then "Cancel" or "Remove reminder" and "Set reminder".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderSheet(
    state: ReminderSheetUiState,
    onEvent: (ReminderEvent) -> Unit,
) {
    PingogramBottomSheet(
        onDismissRequest = { onEvent(ReminderEvent.Dismiss) },
        sheetState =
            rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        ReminderSheetContent(state, onEvent)
    }
}

/** The sheet's content, apart from its window for previews and tests. */
@Composable
internal fun ReminderSheetContent(
    state: ReminderSheetUiState,
    onEvent: (ReminderEvent) -> Unit,
) {
    val note = rememberSaveable(state.messageId, saver = TextFieldState.Saver) { TextFieldState(state.note) }
    val resources = LocalResources.current
    var picking by rememberSaveable(state.messageId) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .padding(bottom = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            SheetHeader()
            MessagePreview(state)
            WhenChoices(state, onEvent, onPick = { picking = true })
            TextField(
                state = note,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reminder_note)) },
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NOTE_LINES),
                shape = TextFieldDefaults.roundedShape,
                colors = TextFieldDefaults.tonalColors(),
            )
            Text(
                text = stringResource(R.string.reminder_on_this_phone),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.isSaving) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            SheetButtons(state, onEvent) {
                onEvent(
                    ReminderEvent.Save(
                        note = note.text.toString(),
                        sender = state.sender?.resolve(resources).orEmpty(),
                        text = state.text?.resolve(resources).orEmpty(),
                    ),
                )
            }
        }
    }
    if (picking) {
        val custom = state.choices.firstOrNull { it.option == ReminderOption.Custom }?.at
        ScheduleDialog(
            now = Instant::now,
            onDismiss = { picking = false },
            onSchedule = { at ->
                picking = false
                onEvent(ReminderEvent.PickCustom(at.epochSecond))
            },
            initial = custom?.let(Instant::ofEpochSecond),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetHeader() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.avatarRow)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_alarm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.reminder_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
    }
}

/** Who sent the message and what it says, on `surfaceContainerHigh`. */
@Composable
private fun MessagePreview(state: ReminderSheetUiState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(MaterialTheme.spacing.medium),
    ) {
        state.sender?.let {
            Text(
                text = it.resolve(),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
        }
        Text(
            text = state.text?.resolve().orEmpty(),
            maxLines = PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
    }
}

/** "When" over the choices as one radio group; "Pick a date and time" opens the pickers ([onPick]). */
@Composable
private fun WhenChoices(
    state: ReminderSheetUiState,
    onEvent: (ReminderEvent) -> Unit,
    onPick: () -> Unit,
) {
    Text(
        text = stringResource(R.string.reminder_when),
        modifier = Modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
    Column(Modifier.selectableGroup()) {
        state.choices.forEach { choice ->
            val custom = choice.option == ReminderOption.Custom
            ChoiceRow(
                choice = choice,
                selected = state.selected == choice.option && choice.at != null,
                onClick = { if (custom) onPick() else onEvent(ReminderEvent.Choose(choice.option)) },
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    choice: ReminderChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .padding(vertical = MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(MaterialTheme.spacing.small))
        FlowRow(
            modifier = Modifier.weight(1f).padding(end = MaterialTheme.spacing.small),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = stringResource(choice.option.label()), style = MaterialTheme.typography.bodyLarge)
            choice.whenText?.let {
                Text(
                    text = it.resolve(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium.userContent,
                )
            }
        }
    }
}

private fun ReminderOption.label(): Int =
    when (this) {
        ReminderOption.InOneHour -> R.string.reminder_in_one_hour
        ReminderOption.ThisEvening -> R.string.reminder_this_evening
        ReminderOption.TomorrowMorning -> R.string.reminder_tomorrow_morning
        ReminderOption.NextWeek -> R.string.reminder_next_week
        ReminderOption.Custom -> R.string.reminder_pick
    }

@Composable
private fun SheetButtons(
    state: ReminderSheetUiState,
    onEvent: (ReminderEvent) -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.hasReminder) {
            TextButton(
                onClick = { onEvent(ReminderEvent.Remove) },
                enabled = !state.isSaving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.reminder_remove)) }
        } else {
            TextButton(onClick = { onEvent(ReminderEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.reminder_cancel))
            }
        }
        Button(
            onClick = onSave,
            enabled = !state.isSaving && state.at != null,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.reminder_set), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

private const val NOTE_LINES = 4
private const val PREVIEW_LINES = 3
