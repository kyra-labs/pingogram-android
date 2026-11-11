package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.scheduled.repeatLabel
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * "Repeat" in [ScheduleDialog]'s time step (td_api.tl `messageSchedulingStateSendAtDate.repeat_period`): the repeat
 * symbol, "Repeat" and the chosen period ("Never", "Weekly") in `primary`, a row of at least 48 dp that opens an
 * expressive menu of Never and [SendOptions.REPEAT_PERIODS], the chosen one with a check. Screen readers hear one
 * drop-down list, "Repeat", with the period as its state.
 */
@Composable
internal fun ScheduleRepeat(
    period: Int,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val resources = LocalResources.current
    val never = stringResource(R.string.composer_schedule_repeat_never)
    val label: (Int) -> String = { if (it == 0) never else repeatLabel(resources, it) }
    val chosen = label(period)
    val spacing = MaterialTheme.spacing
    Box(modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .clickable(role = Role.DropdownList) { open = true }
                    .semantics { stateDescription = chosen }
                    .padding(horizontal = spacing.extraLarge),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_repeat),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.composer_schedule_repeat),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(text = chosen, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Icon(
                painter = painterResource(UiR.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        RepeatMenu(open, period, label, onDismiss = { open = false }) { choice ->
            open = false
            onChoose(choice)
        }
    }
}

/** The menu of [ScheduleRepeat]: Never, then each period, the chosen [period] with a check. */
@Composable
private fun RepeatMenu(
    open: Boolean,
    period: Int,
    label: (Int) -> String,
    onDismiss: () -> Unit,
    onChoose: (Int) -> Unit,
) {
    val choices = remember { listOf(0) + SendOptions.REPEAT_PERIODS }
    DropdownMenuPopup(expanded = open, onDismissRequest = onDismiss) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            choices.forEachIndexed { index, choice ->
                DropdownMenuItem(
                    onClick = { onChoose(choice) },
                    text = { Text(label(choice)) },
                    modifier = Modifier.semantics { selected = choice == period },
                    shape = MenuDefaults.itemShape(index, choices.size).shape,
                    trailingContent = {
                        if (choice == period) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                            )
                        }
                    },
                )
            }
        }
    }
}

/**
 * The composer's [ScheduleDialog]: the moment picked goes to [onSend] as a scheduled send, repeating at the period
 * chosen where the composer [canRepeat] (a Telegram Premium subscriber).
 */
@Composable
internal fun ComposerScheduleDialog(
    canRepeat: Boolean,
    now: () -> Instant,
    onDismiss: () -> Unit,
    onSend: (SendOptions) -> Unit,
) {
    ScheduleDialog(
        now = now,
        onDismiss = onDismiss,
        onSchedule = { at -> onSend(SendOptions(scheduledAt = at.epochSecond)) },
        onScheduleRepeating =
            if (canRepeat) {
                { at, period -> onSend(SendOptions(scheduledAt = at.epochSecond, repeatPeriod = period)) }
            } else {
                null
            },
    )
}
