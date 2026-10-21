package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.R

/**
 * Material's [Switch] with a check in its thumb while on and none while off, as Material recommends and Android 16's
 * Settings shows (`docs/DESIGN_SYSTEM.md` §6, "Settings rows"). Usually in a row that toggles it, with
 * [onCheckedChange] null, so the row alone takes the click and screen readers hear the row as the switch.
 */
@Composable
fun CheckSwitch(
    checked: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onCheckedChange: ((Boolean) -> Unit)? = null,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        thumbContent =
            if (checked) {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize).testTag(CHECK_SWITCH_CHECK_TAG),
                    )
                }
            } else {
                null
            },
        enabled = enabled,
    )
}

/** The haptic of a switch or toggle turned to [checked] (`docs/DESIGN_SYSTEM.md` §5): `ToggleOn` or `ToggleOff`. */
fun HapticFeedback.performToggle(checked: Boolean) {
    performHapticFeedback(if (checked) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
}

/** The test tag of the check in a [CheckSwitch]'s thumb. */
internal const val CHECK_SWITCH_CHECK_TAG = "CheckSwitch.check"
