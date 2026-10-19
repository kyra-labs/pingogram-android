package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The body of a screen whose top app bar sits on `surfaceContainer`, the `Scaffold`'s container color
 * (`docs/DESIGN_SYSTEM.md` §2.2, §4): a `surface` sheet with `extraLarge` top corners from the bottom of the bar to
 * the bottom of the window, Material 3 Expressive's rounded content container. What scrolls in it is clipped to it, so
 * content scrolls under its rounded corners instead of under the bar.
 *
 * [innerPadding] is the `Scaffold`'s: its top places the sheet, read in the layout phase, so a collapsing bar moves the
 * sheet without recomposing. [content] gets the rest (the sides and the bottom: system bars, the floating navigation
 * bar), and a small gap at the top that keeps it clear of the rounded corners, to pad what it shows, also read in the
 * layout phase.
 */
@Composable
fun ContentSheet(
    innerPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(contentPadding: PaddingValues) -> Unit,
) {
    val sheetPadding = remember(innerPadding) { innerPadding.topOnly() }
    val gap = MaterialTheme.spacing.small
    val contentPadding = remember(innerPadding, gap) { innerPadding.withTop(gap) }
    Box(
        modifier
            .fillMaxSize()
            .padding(sheetPadding)
            .clip(MaterialTheme.shapes.extraLarge.top())
            .background(MaterialTheme.colorScheme.surface),
    ) { content(contentPadding) }
}

/** This shape with square bottom corners: a sheet's that rises from the bottom of the window. */
private fun CornerBasedShape.top(): CornerBasedShape = copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)

@PingogramPreviews
@Composable
private fun ContentSheetPreview() {
    PingogramPreview {
        Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainer) { innerPadding ->
            ContentSheet(innerPadding) { contentPadding ->
                Text(text = "Content", modifier = Modifier.padding(contentPadding))
            }
        }
    }
}
