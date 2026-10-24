package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.twostep.FloodWaitNote

// The parts of the account's flows (Change number, Login email), steps on one settings page as 2-step verification's.

/** What a flow's step shares: a request on its way, a flood wait's countdown, and whether anything can be sent. */
internal interface FlowStepState {
    val isWorking: Boolean
    val floodWaitSeconds: Long?
    val floodWaitTotalSeconds: Long
    val canSubmit: Boolean get() = !isWorking && floodWaitSeconds == null
}

/** What a step says: `bodyMedium` in `onSurfaceVariant`, inset like the settings notes. */
@Composable
internal fun StepText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The end of a step: the `FLOOD_WAIT` countdown and the progress of a request, then [secondary], if any, as a
 * [TextButton] at the start and [primary] as a default [Button] at the end, both with the press morph.
 */
@Suppress("LongParameterList") // The step's state, its two actions and whether the main one can be used.
@Composable
internal fun StepActions(
    state: FlowStepState,
    primary: String,
    onPrimary: () -> Unit,
    enabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    state.floodWaitSeconds?.let { FloodWaitNote(seconds = it, totalSeconds = state.floodWaitTotalSeconds) }
    if (state.isWorking) {
        val busy = stringResource(R.string.settings_account_flow_busy)
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = busy })
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (secondary != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TextButton(onClick = onSecondary, enabled = !state.isWorking, shapes = ButtonDefaults.shapes()) {
                Text(secondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Button(onClick = onPrimary, enabled = enabled && state.canSubmit, shapes = ButtonDefaults.shapes()) {
            Text(primary, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}
