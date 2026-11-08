package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The messages fading into the composer (`docs/DESIGN_SYSTEM.md` §6 "Composer"; owner request 2026-11-07), as the
 * chat list fades behind the floating navigation bar: a gradient from transparent to `surface`, the composer's color,
 * `spacing.extraLarge` tall, just above the composer ([bottom]: its height from the sheet's bottom). It shows as
 * messages scroll under it: not at all while the list ([listState], the reverse layout's) rests on its newest message,
 * so that message is never faded, and fully once the list moved a fade's height away from it. Read in the draw phase
 * and its brush built once per size (`drawWithCache`), so scrolling recomposes nothing; never a blur, and it takes no
 * touches.
 */
@Composable
internal fun ComposerFade(
    listState: LazyListState,
    bottom: Dp,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.surface
    Box(
        modifier
            .fillMaxWidth()
            .padding(bottom = bottom)
            .height(MaterialTheme.spacing.extraLarge)
            .drawWithCache {
                val brush = Brush.verticalGradient(listOf(color.copy(alpha = 0f), color))
                onDrawBehind {
                    val away =
                        if (listState.firstVisibleItemIndex > 0) {
                            size.height
                        } else {
                            listState.firstVisibleItemScrollOffset.toFloat()
                        }
                    val alpha = (away / size.height).coerceIn(0f, 1f)
                    if (alpha > 0f) drawRect(brush, alpha = alpha)
                }
            },
    )
}

/**
 * What sits over the messages' bottom edge: their fade into the composer ([ComposerFade]), then the experimental look's
 * typing bubble ([TypingRowSlot]), for which the list keeps room below the newest message while it shows.
 */
@Composable
internal fun BoxScope.MessagesBottom(
    typing: TypingBubble?,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    ComposerFade(listState, contentPadding.calculateBottomPadding(), Modifier.align(Alignment.BottomCenter))
    TypingRowSlot(typing, contentPadding, Modifier.align(Alignment.BottomStart))
}
