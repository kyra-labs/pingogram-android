package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import dev.kyralabs.pingogram.core.ui.component.LocalNavigationBarInsets

/**
 * The list's end fading into the sheet behind the floating navigation bar (`docs/DESIGN_SYSTEM.md` §6.1; owner
 * decision 2026-11-06): a gradient from transparent to the sheet's `surface`, as tall as the bar's room at the bottom
 * ([LocalNavigationBarInsets]: none without the bar), drawn in the draw phase with its brush built once per size
 * (`drawWithCache`), never a blur. It takes no touches: the rows under it stay reachable.
 */
@Composable
internal fun BottomFade(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(LocalNavigationBarInsets.current)
            .drawWithCache {
                val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0f), color))
                onDrawBehind { drawRect(brush) }
            },
    )
}
