package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButtonColors
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable

// The folder tabs' colors and chip shapes (FolderTabs, docs/DESIGN_SYSTEM.md §13.3).

/**
 * The tabs' colors: unchecked on `surfaceContainerHighest` (the bar is `surfaceContainer`, the default unchecked
 * container, so the next step of the ladder keeps the tabs apart), the selected chip on `primary` / `onPrimary`.
 */
@Composable
internal fun folderButtonColors(): ToggleButtonColors {
    val scheme = MaterialTheme.colorScheme
    return ToggleButtonDefaults.colors(
        containerColor = scheme.surfaceContainerHighest,
        checkedContainerColor = scheme.primary,
        checkedContentColor = scheme.onPrimary,
    )
}

/**
 * The folder chips (`docs/DESIGN_SYSTEM.md` §13.3): apart rather than connected, at rest with the
 * shape scale's `small` corners, the selected one a full pill, morphing on press.
 */
@Composable
internal fun chipShapes(): ToggleButtonShapes =
    ToggleButtonShapes(
        shape = MaterialTheme.shapes.small,
        pressedShape = MaterialTheme.shapes.extraSmall,
        checkedShape = CircleShape,
    )
