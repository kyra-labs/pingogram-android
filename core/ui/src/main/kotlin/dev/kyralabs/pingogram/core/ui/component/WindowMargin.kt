package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The horizontal window margin (`docs/DESIGN_SYSTEM.md` §7): [MaterialTheme.spacing]'s `windowMarginCompact` (16 dp)
 * in compact windows, narrower than 600 dp, and `windowMarginMedium` (24 dp) in medium and wider ones. Read it once per
 * screen and pass it down. Only crossing the 600 dp breakpoint recomposes the caller, not every change of the window's
 * size.
 */
@Composable
fun windowMargin(): Dp {
    val windowInfo = LocalWindowInfo.current
    val compact by remember(windowInfo) {
        derivedStateOf { windowInfo.containerDpSize.width < MEDIUM_WIDTH_LOWER_BOUND }
    }
    val spacing = MaterialTheme.spacing
    return if (compact) spacing.windowMarginCompact else spacing.windowMarginMedium
}

/** Where medium windows start, `WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND` of `androidx.window`. */
private val MEDIUM_WIDTH_LOWER_BOUND = 600.dp
