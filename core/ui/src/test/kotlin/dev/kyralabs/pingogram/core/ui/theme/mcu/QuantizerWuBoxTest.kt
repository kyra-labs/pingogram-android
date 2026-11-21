package dev.kyralabs.pingogram.core.ui.theme.mcu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Random

/**
 * The geometry Wu's quantizer cuts with: once [computeMoments] ran, a box's [volume], and a cut's [bottom] plus [top],
 * give the same sums as adding the histogram's cells one by one.
 */
class QuantizerWuBoxTest {
    @Test
    fun `counts each opaque color once, in the order first seen, and leaves out translucent ones`() {
        val pixels = intArrayOf(0xFF102030.toInt(), 0x80FFFFFF.toInt(), 0xFF405060.toInt(), 0xFF102030.toInt())

        val counts = QuantizerMap.quantize(pixels)

        assertEquals(listOf(0xFF102030.toInt() to 2, 0xFF405060.toInt() to 1), counts.toList())
    }

    @Test
    fun `a box's volume is the sum of the cells inside it`() {
        val (histogram, cells) = randomHistogram()
        computeMoments(histogram)

        repeat(BOXES) {
            val box = randomBox()
            assertEquals(bruteSum(cells, box), volume(box, histogram.weights), EPSILON)
        }
    }

    @Test
    fun `a cut's bottom and top add up to the part of the box below the cut`() {
        val (histogram, cells) = randomHistogram()
        computeMoments(histogram)

        repeat(BOXES) {
            val box = randomBox()
            for (direction in WuDirection.entries) {
                val (low, high) = box.bounds(direction)
                for (position in low + 1..high) {
                    val below = box.copyWithUpper(direction, position)
                    val cut =
                        bottom(box, direction, histogram.weights) +
                            top(box, direction, position, histogram.weights)
                    assertEquals(bruteSum(cells, below), cut, EPSILON, "$direction at $position")
                }
            }
        }
    }

    private val random = Random(SEED)

    private fun randomHistogram(): Pair<WuHistogram, Map<Triple<Int, Int, Int>, Double>> {
        val histogram = WuHistogram()
        val cells = HashMap<Triple<Int, Int, Int>, Double>()
        repeat(FILLED_CELLS) {
            val cell = Triple(side(), side(), side())
            val weight = random.nextInt(MAX_WEIGHT) + 1.0
            histogram.weights[wuIndex(cell.first, cell.second, cell.third)] += weight
            cells[cell] = (cells[cell] ?: 0.0) + weight
        }
        return histogram to cells
    }

    private fun side(): Int = random.nextInt(WU_SIDE_LENGTH - 1) + 1

    private fun randomBox(): WuBox = box(randomRange(), randomRange(), randomRange())

    private fun randomRange(): Pair<Int, Int> {
        val a = random.nextInt(WU_SIDE_LENGTH)
        val b = random.nextInt(WU_SIDE_LENGTH)
        return minOf(a, b) to maxOf(a, b)
    }

    private fun box(
        r: Pair<Int, Int>,
        g: Pair<Int, Int>,
        b: Pair<Int, Int>,
    ): WuBox =
        WuBox().also {
            it.r0 = r.first
            it.r1 = r.second
            it.g0 = g.first
            it.g1 = g.second
            it.b0 = b.first
            it.b1 = b.second
        }

    private fun WuBox.bounds(direction: WuDirection): Pair<Int, Int> =
        when (direction) {
            WuDirection.Red -> r0 to r1
            WuDirection.Green -> g0 to g1
            WuDirection.Blue -> b0 to b1
        }

    private fun WuBox.copyWithUpper(
        direction: WuDirection,
        position: Int,
    ): WuBox =
        box(
            r0 to if (direction == WuDirection.Red) position else r1,
            g0 to if (direction == WuDirection.Green) position else g1,
            b0 to if (direction == WuDirection.Blue) position else b1,
        )

    // Bounds are exclusive below and inclusive above, as in WuBox.
    private fun bruteSum(
        cells: Map<Triple<Int, Int, Int>, Double>,
        box: WuBox,
    ): Double =
        cells.entries
            .filter { (cell, _) ->
                cell.first in box.r0 + 1..box.r1 && cell.second in box.g0 + 1..box.g1 &&
                    cell.third in box.b0 + 1..box.b1
            }.sumOf { it.value }

    private companion object {
        const val SEED = 7L
        const val FILLED_CELLS = 400
        const val MAX_WEIGHT = 50
        const val BOXES = 20
        const val EPSILON = 1e-6
    }
}
