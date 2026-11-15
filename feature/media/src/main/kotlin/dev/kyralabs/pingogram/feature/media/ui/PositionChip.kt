package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * "3 of 120" on a full pill of `onMediaOverlay` at a sixth of its strength, `labelLargeEmphasized` with tabular
 * figures, a chip's height (`FilterChipDefaults.Height`), so it reads as a counter over any photo.
 */
@Composable
internal fun PositionChip(text: String) {
    val onOverlay = MaterialTheme.pingogramColors.onMediaOverlay
    Text(
        text = text,
        modifier =
            Modifier
                .padding(end = MaterialTheme.spacing.medium)
                .background(onOverlay.copy(alpha = POSITION_CHIP_ALPHA), CircleShape)
                .heightIn(min = FilterChipDefaults.Height)
                .wrapContentHeight()
                .padding(horizontal = MaterialTheme.spacing.medium),
        color = onOverlay,
        maxLines = 1,
        style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
    )
}

/** How strongly the position chip's pill shows `onMediaOverlay` behind its text. */
private const val POSITION_CHIP_ALPHA = 0.16f
