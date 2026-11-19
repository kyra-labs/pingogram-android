package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.R

/**
 * Material's [Switch] with a check in its thumb while on and a close mark while off (`docs/DESIGN_SYSTEM.md` §6,
 * "Settings rows"; owner decision 2026-11-19), in Material's colors: the off thumb `outline` with its mark in
 * `surfaceContainerHighest`. The check is `primary`, the track's color, rather than Material's `onPrimaryContainer`,
 * which is the thumb's own `onPrimary` with high contrast and would vanish. Every switch in the app is one. The marks
 * are decorative: the switch's state says on or off. Usually in a row that toggles it, with [onCheckedChange] null, so
 * the row alone takes the click and screen readers hear the row as the switch. [colors] replace Material's where the
 * switch sits on a container of its own.
 */
@Composable
fun CheckSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
    colors: SwitchColors = SwitchDefaults.colors(checkedIconColor = MaterialTheme.colorScheme.primary),
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent = {
            Icon(
                painter = painterResource(if (checked) R.drawable.ic_check else R.drawable.ic_close),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(SwitchDefaults.IconSize)
                        .testTag(if (checked) CHECK_SWITCH_CHECK_TAG else CHECK_SWITCH_CLOSE_TAG),
            )
        },
        enabled = enabled,
        colors = colors,
    )
}

/** The haptic of a switch or toggle turned to [checked] (`docs/DESIGN_SYSTEM.md` §5): `ToggleOn` or `ToggleOff`. */
fun HapticFeedback.performToggle(checked: Boolean) {
    performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
}

/** The test tag of the check in a [CheckSwitch]'s thumb. */
internal const val CHECK_SWITCH_CHECK_TAG = "CheckSwitch.check"

/** The test tag of the close mark in an off [CheckSwitch]'s thumb. */
internal const val CHECK_SWITCH_CLOSE_TAG = "CheckSwitch.close"
