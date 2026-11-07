package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * A list's end fading into its sheet behind the floating navigation bar (`docs/DESIGN_SYSTEM.md` §6.1; owner decisions
 * 2026-11-06 and 2026-11-07): a gradient from transparent to [color] (the sheet's, `surface` by default), which it
 * reaches at [SOLID_FROM] of its height, so the rows fade out above the bar rather than under it. It is as tall as the
 * bar's room at the bottom ([LocalNavigationBarInsets]) and the bar's height again; in the experimental look (§13) it
 * is `sizes.experimentalBottomFade` above the system navigation bar, as the owner's design draws it. Without the bar
 * (rail windows) it takes no height. Its height is worked out in the layout phase and its brush built once per size in
 * the draw phase (`drawWithCache`), never a blur. It takes no touches: the rows under it stay reachable.
 */
@Composable
fun BottomFade(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
) {
    val barInsets = LocalNavigationBarInsets.current
    val barHeight = floatingNavigationBarHeight()
    val experimental = LocalExperimentalUi.current
    val experimentalHeight = MaterialTheme.sizes.experimentalBottomFade.takeIf { experimental }
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val barRoom = barInsets.getBottom(this)
                val height =
                    when {
                        barRoom == 0 -> {
                            0
                        }

                        // The bar's room is the system navigation bar, the bar and its margins.
                        experimentalHeight != null -> {
                            val bar = barHeight + FloatingToolbarDefaults.ScreenOffset * 2
                            barRoom - bar.roundToPx() + experimentalHeight.roundToPx()
                        }

                        else -> {
                            barRoom + barHeight.roundToPx()
                        }
                    }
                val placeable = measurable.measure(Constraints.fixed(constraints.maxWidth, height))
                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }.drawWithCache {
                val brush = Brush.verticalGradient(0f to color.copy(alpha = 0f), SOLID_FROM to color)
                onDrawBehind { drawRect(brush) }
            },
    )
}

/** Where the fade reaches the sheet's color: about two thirds down, as the owner's design has it (70%). */
private const val SOLID_FROM = 0.7f
