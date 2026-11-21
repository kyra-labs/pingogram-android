package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The composer floating over a conversation's Telegram wallpaper (docs/DESIGN_SYSTEM.md §13.5): no bar behind the
// pill, the bars above it on a container of their own, the panels on the surface.

/** The bar behind the composer: `surface`, or none while it [floating]s over a wallpaper. */
@Composable
internal fun bottomBarColor(floating: Boolean): Color =
    if (floating) Color.Transparent else MaterialTheme.colorScheme.surface

/**
 * The bars above a [floating] composer (suggestions, inline results, the reply or edit bar) on a container of their
 * own, `surfaceContainerHigh` with `extraLarge` corners, readable over the wallpaper; nothing while none shows.
 */
@Composable
internal fun Modifier.floatingBars(floating: Boolean): Modifier =
    if (floating) {
        padding(horizontal = MaterialTheme.spacing.small)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    } else {
        this
    }

/** The panels (emoji, a bot's keyboard) on `surface` under a [floating] composer, as under the bar otherwise. */
@Composable
internal fun Modifier.panelSurface(floating: Boolean): Modifier =
    if (floating) background(MaterialTheme.colorScheme.surface) else this
