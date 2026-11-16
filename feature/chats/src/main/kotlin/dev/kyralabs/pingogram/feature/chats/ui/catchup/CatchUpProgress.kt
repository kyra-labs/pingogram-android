package dev.kyralabs.pingogram.feature.chats.ui.catchup

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * The round's progress: one segment per chat (`sizes.pinnedIndicator` tall, round ends, `spacing.extraSmall` apart),
 * those handled in `primary`, the shown one in `primary` at half its strength (the owner's board 32b), the rest in
 * `surfaceContainerHighest`, drawn in the draw phase; past
 * [MAX_SEGMENTS] chats one bar filled to the position. Screen readers hear "Chat 3 of 12" as its range.
 */
@Composable
internal fun CatchUpProgress(
    position: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.catch_up_progress, position, total)
    val done = MaterialTheme.colorScheme.primary
    val shown = done.copy(alpha = SHOWN_ALPHA)
    val rest = MaterialTheme.colorScheme.surfaceContainerHighest
    val gap = with(LocalDensity.current) { MaterialTheme.spacing.extraSmall.toPx() }
    Canvas(
        modifier =
            modifier.height(MaterialTheme.sizes.pinnedIndicator).semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(position.toFloat(), 0f..total.toFloat(), total)
            },
    ) {
        val radius = CornerRadius(size.height / 2)
        val segments = if (total <= MAX_SEGMENTS) total else 1
        val width = (size.width - gap * (segments - 1)) / segments
        repeat(segments) { index ->
            val left = index * (width + gap)
            drawRoundRect(rest, Offset(left, 0f), Size(width, size.height), radius)
            val filled = if (segments == 1) width * position / total.coerceAtLeast(1) else width
            when {
                segments == 1 || index < position - 1 -> {
                    drawRoundRect(done, Offset(left, 0f), Size(filled, size.height), radius)
                }

                index == position - 1 -> {
                    drawRoundRect(shown, Offset(left, 0f), Size(width, size.height), radius)
                }
            }
        }
    }
}

private const val MAX_SEGMENTS = 24

/** The shown chat's segment: `primary` at half its strength. */
private const val SHOWN_ALPHA = 0.5f
