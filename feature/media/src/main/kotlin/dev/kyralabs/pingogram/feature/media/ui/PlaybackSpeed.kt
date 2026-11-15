package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.feature.media.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The speeds a video plays at in the viewer (board 25, owner decision 2026-11-15: chosen from More, a chip among the
 * controls while not 1×), slowest first.
 */
internal val PlaybackSpeeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

/** "1.5×", in the digits and decimal separator of the current language. */
@Composable
internal fun speedText(speed: Float): String {
    val format = NumberFormat.getNumberInstance(LocalConfiguration.current.locales[0])
    format.maximumFractionDigits = 2
    return stringResource(R.string.media_speed_value, format.format(speed))
}

/**
 * The speeds as items of an expressive menu ([DropdownMenuItem]), [first] being the index of the first in its group of
 * [count] items: each "1.5×", the [current] one checked and selected for screen readers; a tap goes to [onPick].
 */
@Composable
internal fun SpeedItems(
    current: Float,
    first: Int,
    count: Int,
    onPick: (Float) -> Unit,
) {
    PlaybackSpeeds.forEachIndexed { index, speed ->
        val selected = speed == current
        DropdownMenuItem(
            onClick = { onPick(speed) },
            text = { Text(speedText(speed), style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) },
            modifier = Modifier.semantics { this.selected = selected },
            shape = MenuDefaults.itemShape(first + index, count).shape,
            trailingContent =
                if (selected) {
                    { Icon(painterResource(UiR.drawable.ic_check), contentDescription = null) }
                } else {
                    null
                },
        )
    }
}

/**
 * "1.5×" among a video's controls while it does not play at 1× (owner decision 2026-11-15): an [AssistChip] in the
 * overlay's colors whose tap opens the speeds ([SpeedItems]) anchored to it. Screen readers hear "Playback speed 1.5×".
 */
@Composable
internal fun SpeedChip(
    speed: Float,
    onPick: (Float) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val label = speedText(speed)
    val description = stringResource(R.string.media_speed_chip, label)
    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(label, style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum")) },
            modifier = Modifier.semantics { contentDescription = description },
        )
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                SpeedItems(current = speed, first = 0, count = PlaybackSpeeds.size) {
                    expanded = false
                    onPick(it)
                }
            }
        }
    }
}
