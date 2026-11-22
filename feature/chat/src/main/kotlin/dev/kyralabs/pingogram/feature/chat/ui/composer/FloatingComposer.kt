package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.kyralabs.pingogram.core.ui.theme.spacing

// The composer floats over the messages (docs/DESIGN_SYSTEM.md §6): no bar behind it, with or without a wallpaper.

/**
 * What sits above the pill (suggestions, inline results, the reply or edit bar) or stands for it (why the user cannot
 * write here) on a card of its own, `surfaceContainerHigh` with `extraLarge` corners, readable over the messages and
 * the wallpaper; nothing while none shows.
 */
@Composable
internal fun Modifier.composerBars(): Modifier =
    padding(horizontal = MaterialTheme.spacing.small)
        .clip(MaterialTheme.shapes.extraLarge)
        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
