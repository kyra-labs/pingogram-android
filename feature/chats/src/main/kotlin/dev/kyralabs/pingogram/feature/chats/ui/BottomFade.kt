package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarInsets
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * The list's end fading into the sheet behind the floating navigation bar (`docs/DESIGN_SYSTEM.md` §6.1; owner
 * decisions 2026-11-06 and 2026-11-07): a gradient from transparent to the sheet's `surface`, which it reaches at
 * [SOLID_FROM] of its height, so the rows fade out above the bar rather than under it. It is as tall as the bar's room
 * at the bottom ([LocalNavigationBarInsets]) and the bar's height again (`sizes.floatingNavigationBar`); without the
 * bar (rail windows) it takes no height. Its height is worked out in the layout phase and its brush built once per
 * size in the draw phase (`drawWithCache`), never a blur. It takes no touches: the rows under it stay reachable.
 */
@Composable
internal fun BottomFade(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.surface
    val barInsets = LocalNavigationBarInsets.current
    val barHeight = MaterialTheme.sizes.floatingNavigationBar
    Box(
        modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val barRoom = barInsets.getBottom(this)
                val height = if (barRoom == 0) 0 else barRoom + barHeight.roundToPx()
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
