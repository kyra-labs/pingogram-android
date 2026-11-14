package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSimple
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The content sheet's clip is a round rect Android can clip to without a mask: the same corners all round, the bottom
 * ones below the sheet, so the sheet shows rounded top corners and a square bottom edge.
 */
class SheetShapeTest {
    private val density = Density(3.5f)

    @Test
    fun `the outline is a simple round rect whose bottom corners lie below the sheet`() {
        val size = Size(1440f, 2600f)

        val outline = SheetShape(CornerSize(28.dp)).createOutline(size, LayoutDirection.Ltr, density)

        val roundRect = (outline as Outline.Rounded).roundRect
        assertTrue(roundRect.isSimple)
        val radius = with(density) { 28.dp.toPx() }
        assertEquals(radius, roundRect.topLeftCornerRadius.x, 0.01f)
        assertEquals(0f, roundRect.top, 0f)
        assertEquals(size.width, roundRect.right, 0f)
        assertEquals(size.height + radius, roundRect.bottom, 0.01f)
    }

    @Test
    fun `shapes of the same corner are equal, so the clip is not set again`() {
        assertEquals(SheetShape(CornerSize(28.dp)), SheetShape(CornerSize(28.dp)))
    }
}
