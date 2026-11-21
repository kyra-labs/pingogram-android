/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Ported to Kotlin by the Pingogram authors from material-color-utilities 0.4.0
 * (https://github.com/material-foundation/material-color-utilities): quantize/quantizer_wu.
 * Restructured into smaller functions; the arithmetic and its order are upstream's.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.roundToInt

/**
 * Xiaolin Wu's color quantizer, as upstream's QuantizerWu: it cuts the RGB cube into up to `maxColors` boxes of least
 * variance and answers their mean colors. Celebi uses it to seed its k-means. One instance quantizes one image.
 */
internal class QuantizerWu {
    private val histogram = WuHistogram()
    private var cubes: Array<WuBox> = emptyArray()

    /** Up to [maxColors] colors that represent [pixels] (ARGB); translucent pixels are left out. */
    fun quantize(
        pixels: IntArray,
        maxColors: Int,
    ): List<Int> {
        constructHistogram(pixels)
        computeMoments(histogram)
        return createResult(createBoxes(maxColors))
    }

    private fun constructHistogram(pixels: IntArray) {
        val bitsToRemove = 8 - WU_INDEX_BITS
        for ((pixel, count) in QuantizerMap.quantize(pixels)) {
            val red = redFromArgb(pixel)
            val green = greenFromArgb(pixel)
            val blue = blueFromArgb(pixel)
            val index = wuIndex((red shr bitsToRemove) + 1, (green shr bitsToRemove) + 1, (blue shr bitsToRemove) + 1)
            with(histogram) {
                val weight = count.toDouble()
                weights[index] += weight
                momentsR[index] += weight * red
                momentsG[index] += weight * green
                momentsB[index] += weight * blue
                moments[index] += weight * (red * red + green * green + blue * blue)
            }
        }
    }

    /** Cuts the cube into up to [maxColors] boxes, always cutting the one of most variance; how many it made. */
    private fun createBoxes(maxColors: Int): Int {
        cubes = Array(maxColors) { WuBox() }
        cubes[0].r1 = WU_SIDE_LENGTH - 1
        cubes[0].g1 = WU_SIDE_LENGTH - 1
        cubes[0].b1 = WU_SIDE_LENGTH - 1
        val volumeVariance = DoubleArray(maxColors)
        var generatedColorCount = maxColors
        var next = 0
        var i = 1
        while (i < maxColors) {
            if (cut(cubes[next], cubes[i])) {
                volumeVariance[next] = if (cubes[next].vol > 1) variance(cubes[next]) else 0.0
                volumeVariance[i] = if (cubes[i].vol > 1) variance(cubes[i]) else 0.0
            } else {
                volumeVariance[next] = 0.0
                i--
            }
            next = 0
            var temp = volumeVariance[0]
            for (j in 1..i) {
                if (volumeVariance[j] > temp) {
                    temp = volumeVariance[j]
                    next = j
                }
            }
            if (temp <= 0.0) {
                generatedColorCount = i + 1
                break
            }
            i++
        }
        return generatedColorCount
    }

    /** The mean color of each of the first [colorCount] boxes that holds any pixel. */
    private fun createResult(colorCount: Int): List<Int> =
        (0 until colorCount).mapNotNull { i ->
            val cube = cubes[i]
            val weight = volume(cube, histogram.weights)
            if (weight > 0) {
                val r = (volume(cube, histogram.momentsR) / weight).roundToInt()
                val g = (volume(cube, histogram.momentsG) / weight).roundToInt()
                val b = (volume(cube, histogram.momentsB) / weight).roundToInt()
                (255 shl 24) or ((r and 0x0ff) shl 16) or ((g and 0x0ff) shl 8) or (b and 0x0ff)
            } else {
                null
            }
        }

    private fun variance(cube: WuBox): Double {
        val dr = volume(cube, histogram.momentsR)
        val dg = volume(cube, histogram.momentsG)
        val db = volume(cube, histogram.momentsB)
        val xx = volume(cube, histogram.moments)
        val hypotenuse = dr * dr + dg * dg + db * db
        return xx - hypotenuse / volume(cube, histogram.weights)
    }

    /** Cuts [one] in two along its best axis, the far part into [two]; false when it cannot be cut. */
    private fun cut(
        one: WuBox,
        two: WuBox,
    ): Boolean {
        val whole = sums(one)
        val maxR = maximize(one, WuDirection.Red, one.r0 + 1, one.r1, whole)
        val maxG = maximize(one, WuDirection.Green, one.g0 + 1, one.g1, whole)
        val maxB = maximize(one, WuDirection.Blue, one.b0 + 1, one.b1, whole)
        val direction =
            when {
                maxR.maximum >= maxG.maximum && maxR.maximum >= maxB.maximum -> {
                    WuDirection.Red.takeIf { maxR.cutLocation >= 0 }
                }

                maxG.maximum >= maxR.maximum && maxG.maximum >= maxB.maximum -> {
                    WuDirection.Green
                }

                else -> {
                    WuDirection.Blue
                }
            }
        if (direction != null) split(one, two, direction, listOf(maxR, maxG, maxB)[direction.ordinal].cutLocation)
        return direction != null
    }

    private fun split(
        one: WuBox,
        two: WuBox,
        direction: WuDirection,
        cutLocation: Int,
    ) {
        two.r1 = one.r1
        two.g1 = one.g1
        two.b1 = one.b1
        two.r0 = one.r0
        two.g0 = one.g0
        two.b0 = one.b0
        when (direction) {
            WuDirection.Red -> one.r1 = cutLocation.also { two.r0 = it }
            WuDirection.Green -> one.g1 = cutLocation.also { two.g0 = it }
            WuDirection.Blue -> one.b1 = cutLocation.also { two.b0 = it }
        }
        one.vol = (one.r1 - one.r0) * (one.g1 - one.g0) * (one.b1 - one.b0)
        two.vol = (two.r1 - two.r0) * (two.g1 - two.g0) * (two.b1 - two.b0)
    }

    private fun sums(cube: WuBox): WuSums =
        WuSums(
            volume(cube, histogram.momentsR),
            volume(cube, histogram.momentsG),
            volume(cube, histogram.momentsB),
            volume(cube, histogram.weights),
        )

    /** Where along [direction], from [first] until [last], cutting [cube] leaves the two parts of least variance. */
    private fun maximize(
        cube: WuBox,
        direction: WuDirection,
        first: Int,
        last: Int,
        whole: WuSums,
    ): MaximizeResult {
        val bottom =
            WuSums(
                bottom(cube, direction, histogram.momentsR),
                bottom(cube, direction, histogram.momentsG),
                bottom(cube, direction, histogram.momentsB),
                bottom(cube, direction, histogram.weights),
            )
        var max = 0.0
        var cut = -1
        for (i in first until last) {
            val half =
                WuSums(
                    bottom.r + top(cube, direction, i, histogram.momentsR),
                    bottom.g + top(cube, direction, i, histogram.momentsG),
                    bottom.b + top(cube, direction, i, histogram.momentsB),
                    bottom.w + top(cube, direction, i, histogram.weights),
                )
            val temp = half.cutScore(whole)
            if (temp != null && temp > max) {
                max = temp
                cut = i
            }
        }
        return MaximizeResult(cut, max)
    }
}

/** A box's sums of red, green, blue and weight. */
private class WuSums(
    val r: Double,
    val g: Double,
    val b: Double,
    val w: Double,
) {
    /** How well cutting [whole] into this part and the rest separates it; null when either part is empty. */
    fun cutScore(whole: WuSums): Double? {
        val otherW = whole.w - w
        return if (w == 0.0 || otherW == 0.0) {
            null
        } else {
            val otherR = whole.r - r
            val otherG = whole.g - g
            val otherB = whole.b - b
            (r * r + g * g + b * b) / w + (otherR * otherR + otherG * otherG + otherB * otherB) / otherW
        }
    }
}

private class MaximizeResult(
    val cutLocation: Int,
    val maximum: Double,
)
