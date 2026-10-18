package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.ui.unit.IntRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.math.abs

class AlbumLayoutTest {
    private val width = 840
    private val gap = 6

    private fun layout(vararg ratios: Float) = albumLayout(ratios.toList(), width, gap)

    /** The number of items in each row, top to bottom. */
    private fun AlbumLayout.rows(): List<Int> =
        cells
            .groupBy { it.top }
            .toSortedMap()
            .values
            .map { it.size }

    @Test
    fun `two squares go side by side`() {
        val album = layout(1f, 1f)

        assertEquals(listOf(2), album.rows())
        assertEquals(listOf(IntRect(0, 0, 417, 417), IntRect(423, 0, 840, 417)), album.cells)
        assertEquals(417, album.height)
    }

    @Test
    fun `two wide photos are stacked`() {
        assertEquals(listOf(1, 1), layout(4 / 3f, 4 / 3f).rows())
    }

    @Test
    fun `two portrait photos go side by side`() {
        assertEquals(listOf(2), layout(0.75f, 0.75f).rows())
    }

    @Test
    fun `three squares are one large over two`() {
        assertEquals(listOf(1, 2), layout(1f, 1f, 1f).rows())
    }

    @Test
    fun `four squares are two by two and come out square`() {
        val album = layout(1f, 1f, 1f, 1f)

        assertEquals(listOf(2, 2), album.rows())
        assertEquals(width, album.height)
    }

    @Test
    fun `seven squares make three rows`() {
        assertEquals(listOf(2, 2, 3), layout(*FloatArray(7) { 1f }).rows())
    }

    @Test
    fun `ten squares are two, four and four`() {
        assertEquals(listOf(2, 4, 4), layout(*FloatArray(10) { 1f }).rows())
    }

    @ParameterizedTest
    @ValueSource(ints = [2, 3, 4, 5, 6, 7, 8, 9, 10])
    fun `rows fill the width with exact gaps and keep aspect ratios`(count: Int) {
        val ratios = List(count) { listOf(1f, 0.75f, 4 / 3f, 16 / 9f, 9 / 16f)[it % 5] }
        val album = albumLayout(ratios, width, gap)

        assertEquals(count, album.cells.size)
        album.cells.groupBy { it.top }.values.forEach { row ->
            assertEquals(0, row.first().left)
            assertEquals(width, row.last().right)
            row.zipWithNext().forEach { (left, right) -> assertEquals(gap, right.left - left.right) }
            assertTrue(row.all { it.top == row.first().top && it.bottom == row.first().bottom })
        }
        album.cells.forEachIndexed { index, cell ->
            val ratio = ratios[index].coerceIn(0.5f, 2f)
            assertTrue(abs(cell.width - cell.height * ratio) <= 2, "cell $index: $cell for $ratio")
        }
    }

    @Test
    fun `rows follow each other with exact gaps and the album ends at the last row`() {
        val album = layout(*FloatArray(7) { listOf(1f, 1.5f, 0.6f)[it % 3] })
        val tops =
            album.cells
                .map { it.top }
                .distinct()
                .sorted()
        val bottoms =
            album.cells
                .map { it.bottom }
                .distinct()
                .sorted()

        assertEquals(0, tops.first())
        tops.drop(1).zip(bottoms.dropLast(1)).forEach { (top, bottom) -> assertEquals(gap, top - bottom) }
        assertEquals(album.height, bottoms.last())
    }

    @Test
    fun `extreme shapes are cropped to between 1 to 2 and 2 to 1`() {
        assertEquals(layout(2f, 2f), layout(10f, 10f))
        assertEquals(layout(0.5f, 0.5f), layout(0.05f, 0.05f))
    }

    @Test
    fun `items of unknown shape count as squares`() {
        assertEquals(layout(1f, 1f), layout(0f, Float.NaN))
    }

    @Test
    fun `no items make an empty album`() {
        assertEquals(AlbumLayout(width, 0, emptyList()), albumLayout(emptyList(), width, gap))
    }
}
