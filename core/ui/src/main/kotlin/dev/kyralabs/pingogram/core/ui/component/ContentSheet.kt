package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The body of a screen whose top app bar sits on `surfaceContainer`, the `Scaffold`'s container color
 * (`docs/DESIGN_SYSTEM.md` §2.2, §4): a `surface` sheet ([color]; clear where a conversation's wallpaper shows through)
 * with `extraLarge` top corners from the bottom of the bar to the bottom of the window, Material 3 Expressive's
 * rounded content container. What scrolls in it is clipped to it, so
 * content scrolls under its rounded corners instead of under the bar.
 *
 * [innerPadding] is the `Scaffold`'s: its top places the sheet, read in the layout phase, so a collapsing bar moves the
 * sheet without recomposing. [content] gets the rest (the sides and the bottom: system bars, the floating navigation
 * bar), and a small gap at the top that keeps it clear of the rounded corners, to pad what it shows, also read in the
 * layout phase.
 *
 * The clip is [SheetShape]: a rounded rect with the same corners all round, its bottom corners below the sheet, so
 * Android clips to a round rect rather than a path, which the GPU draws without a clip mask.
 *
 * The sheet paints the `surfaceContainer` around it itself ([surround]: above it, and behind its rounded corners; clear
 * over a conversation's wallpaper, which shows there instead), so its `Scaffold` is transparent, with `onSurface` as
 * its content color, and nothing full-screen lies under the sheet (`docs/PERFORMANCE.md`, "Overdraw"): the window,
 * then the sheet.
 */
@Composable
fun ContentSheet(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    surround: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val sheetPadding = remember(innerPadding) { innerPadding.topOnly() }
    val gap = MaterialTheme.spacing.small
    val contentPadding = remember(innerPadding, gap) { innerPadding.withTop(gap) }
    val corner = MaterialTheme.shapes.extraLarge.topStart
    val shape = remember(corner) { SheetShape(corner) }
    Box(
        modifier
            .fillMaxSize()
            .sheetSurround(innerPadding, corner, surround)
            .padding(sheetPadding)
            .clip(shape)
            .background(color),
    ) { content(contentPadding) }
}

/**
 * [color] above the sheet that [innerPadding]'s top places, and behind its two rounded corners down to their end: what
 * shows around them is the color the corners were drawn over before, to the pixel. Not between the corners, where the
 * opaque sheet would paint over it at once: the folder bar floats there, its shadow and fill already layers deep
 * (`docs/PERFORMANCE.md`, "Overdraw"). Read in the draw phase, so a collapsing bar redraws it without recomposing or
 * measuring.
 */
private fun Modifier.sheetSurround(
    innerPadding: PaddingValues,
    corner: CornerSize,
    color: Color,
): Modifier =
    drawBehind {
        if (color.alpha == 0f) return@drawBehind
        val top = innerPadding.calculateTopPadding().toPx().coerceAtMost(size.height)
        val radius = corner.toPx(size, this).coerceAtMost(size.height - top)
        drawRect(color, size = Size(size.width, top))
        val cornerSize = Size(radius, radius)
        drawRect(color, topLeft = Offset(0f, top), size = cornerSize)
        drawRect(color, topLeft = Offset(size.width - radius, top), size = cornerSize)
    }

/**
 * A sheet's shape that rises from the bottom of the window: [corner] at the top, square at the bottom edge. Its outline
 * is a rounded rect with [corner] all round that reaches [corner] below the sheet, so the bottom corners fall outside
 * it, rather than a rect rounded at the top only. Compose hands Android a round rect only where all four corners are
 * the same ([RoundRect.isSimple]), and anything else as a path; Android 13+ clips to a path, which the GPU cannot do
 * analytically: every draw across the rounded corners (rows, the chat list's floating folder bar and its shadow, a
 * list's overscroll layer) would then render a clip mask on the CPU and upload it, in every frame of a scroll.
 */
@Immutable
internal class SheetShape(
    private val corner: CornerSize,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = corner.toPx(size, density)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height + radius, CornerRadius(radius)))
    }

    override fun equals(other: Any?): Boolean = other is SheetShape && other.corner == corner

    override fun hashCode(): Int = corner.hashCode()
}

@PingogramPreviews
@Composable
private fun ContentSheetPreview() {
    PingogramPreview {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) { innerPadding ->
            ContentSheet(innerPadding) { contentPadding ->
                Text(text = "Content", modifier = Modifier.padding(contentPadding))
            }
        }
    }
}
