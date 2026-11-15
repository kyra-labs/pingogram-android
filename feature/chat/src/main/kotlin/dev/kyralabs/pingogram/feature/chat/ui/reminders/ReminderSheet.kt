package dev.kyralabs.pingogram.feature.chat.ui.reminders

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ReminderEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The reminder sheet (`docs/DESIGN_SYSTEM.md` §6 "Message reminders", the owner's board 29b): a `ModalBottomSheet` that
 * opens expanded, at most `contentMaxWidth`: the alarm on a `MaterialShapes.Cookie9Sided` badge in `primaryContainer`
 * beside "Remind me" in `titleLargeEmphasized` (a heading) over the message in one line; "When" over the choices as one
 * radio group of list items (In 1 hour, This evening before 20:00, Tomorrow morning, Next week, each with when it
 * rings; "Pick a date and time", which opens the date and time pickers), the chosen one on `secondaryContainer` with
 * `large` corners; the note in the rounded tonal field; that it stays on this phone; then "Cancel" or "Delete reminder"
 * and "Set reminder".
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
    now: () -> Instant = Instant::now,
) {
    val note = rememberSaveable(state.messageId, saver = TextFieldState.Saver) { TextFieldState(state.note) }
    var picking by rememberSaveable { mutableStateOf(false) }
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
            SheetHeader(state)
            Choices(state, onEvent, onPick = { picking = true })
            TextField(
                state = note,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.reminder_note)) },
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NOTE_LINES),
                shape = TextFieldDefaults.roundedShape,
                colors = TextFieldDefaults.tonalColors(),
            )
            OnThisPhone()
            if (state.isSaving) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            SheetButtons(state, onEvent, onSet = { onEvent(ReminderEvent.Set(note.text.toString())) })
        }
    }
    if (picking) {
        ScheduleDialog(
            now = now,
            onDismiss = { picking = false },
            onSchedule = { at ->
                picking = false
                onEvent(ReminderEvent.Pick(at.epochSecond))
            },
            title = R.string.reminder_pick,
            confirmLabel = R.string.reminder_set,
        )
    }
}

/** The alarm on its badge beside "Remind me", over the message as the chat list previews it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetHeader(state: ReminderSheetUiState) {
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
                painter = painterResource(UiR.drawable.ic_alarm),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.reminder_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            val text = state.text?.resolve()
            if (text != null) {
                val sender = state.sender?.resolve()
                Text(
                    text = if (sender != null) "${sender.bidiIsolated()}: ${text.bidiIsolated()}" else text,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium.userContent,
                )
            }
        }
    }
}

/** "When" over the choices, one radio group. */
@Composable
private fun Choices(
    state: ReminderSheetUiState,
    onEvent: (ReminderEvent) -> Unit,
    onPick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
        Text(
            text = stringResource(R.string.reminder_when),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleSmallEmphasized,
        )
        Column(Modifier.selectableGroup()) {
            state.options.forEach { option ->
                val custom = option.choice == ReminderChoice.Custom
                ChoiceRow(
                    option = option,
                    selected = state.chosen == option.choice,
                    onClick = { if (custom) onPick() else onEvent(ReminderEvent.Choose(option.choice)) },
                )
            }
        }
    }
}

/**
 * One choice: its symbol, its name over when it rings, and its radio button; selected in the list item's selected
 * colors and shape (`secondaryContainer`, `large`).
 */
@Composable
private fun ChoiceRow(
    option: ReminderOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.RadioButton },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { Icon(painterResource(option.choice.icon), contentDescription = null) },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        supportingContent = option.time?.let { time -> { Text(time.resolve()) } },
    ) {
        Text(stringResource(option.choice.label))
    }
}

/** That the reminder stays on this phone, after the phone symbol. */
@Composable
private fun OnThisPhone() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_smartphone),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.reminder_on_this_phone),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** "Cancel" or "Delete reminder" at the start, "Set reminder" at the end. */
@Composable
private fun SheetButtons(
    state: ReminderSheetUiState,
    onEvent: (ReminderEvent) -> Unit,
    onSet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isExisting) {
            TextButton(
                onClick = { onEvent(ReminderEvent.Delete) },
                enabled = !state.isSaving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.reminder_delete)) }
        } else {
            TextButton(onClick = { onEvent(ReminderEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.reminder_cancel))
            }
        }
        val ready = state.chosen != null && state.options.any { it.choice == state.chosen && it.time != null }
        Button(onClick = onSet, enabled = !state.isSaving && ready, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.reminder_set), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

@get:StringRes
private val ReminderChoice.label: Int
    get() =
        when (this) {
            ReminderChoice.InOneHour -> R.string.reminder_in_one_hour
            ReminderChoice.ThisEvening -> R.string.reminder_this_evening
            ReminderChoice.TomorrowMorning -> R.string.reminder_tomorrow_morning
            ReminderChoice.NextWeek -> R.string.reminder_next_week
            ReminderChoice.Custom -> R.string.reminder_pick
        }

@get:DrawableRes
private val ReminderChoice.icon: Int
    get() =
        when (this) {
            ReminderChoice.InOneHour, ReminderChoice.ThisEvening -> UiR.drawable.ic_schedule
            ReminderChoice.TomorrowMorning, ReminderChoice.NextWeek -> UiR.drawable.ic_alarm
            ReminderChoice.Custom -> R.drawable.ic_calendar_month
        }

private const val NOTE_LINES = 4
