package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The folder tabs' colors and chip shapes (FolderTabs, docs/DESIGN_SYSTEM.md §13.3).

/**
 * The tabs' colors in the floating bar, as the floating navigation bar's tabs (`docs/DESIGN_SYSTEM.md` §13.3): the
 * chips at rest without a container, their labels in `onSurface` on the bar's `surfaceContainer`, the selected chip a
 * neutral tonal pill, `surfaceContainerHighest` / `onSurface`.
 */
@Composable
internal fun folderButtonColors(): ToggleButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ToggleButtonDefaults.colors(
        containerColor = Color.Transparent,
        contentColor = scheme.onSurface,
        checkedContainerColor = scheme.surfaceContainerHighest,
        checkedContentColor = scheme.onSurface,
    )
}

/**
 * The folder chips (`docs/DESIGN_SYSTEM.md` §13.3): apart rather than connected, at rest with the shape scale's
 * `small` corners, which shape the pressed state of a chip without a container, the selected one a full pill, morphing
 * on press.
 */
@Composable
internal fun chipShapes(): ToggleButtonShapes =
    ToggleButtonShapes(
        shape = MaterialTheme.shapes.small,
        pressedShape = MaterialTheme.shapes.extraSmall,
        checkedShape = CircleShape,
    )
