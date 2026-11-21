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
 * (https://github.com/material-foundation/material-color-utilities): quantize/quantizer_wu, its boxes
 * and sums. Moments are doubles, as upstream's are in JavaScript.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

// The color cube's cumulative moments and the boxes Wu's quantizer cuts it into.

internal const val WU_INDEX_BITS = 5
internal const val WU_SIDE_LENGTH = 33
internal const val WU_TOTAL_SIZE = 35937

/** An axis of the RGB cube. */
internal enum class WuDirection { Red, Green, Blue }

/** A box of the 33 × 33 × 33 RGB cube, from (exclusive) [r0], [g0], [b0] to (inclusive) [r1], [g1], [b1]. */
internal class WuBox {
    var r0: Int = 0
    var r1: Int = 0
    var g0: Int = 0
    var g1: Int = 0
    var b0: Int = 0
    var b1: Int = 0
    var vol: Int = 0
}

/** The cube's weights and moments, cumulative once [computeMoments] ran. */
internal class WuHistogram {
    val weights = DoubleArray(WU_TOTAL_SIZE)
    val momentsR = DoubleArray(WU_TOTAL_SIZE)
    val momentsG = DoubleArray(WU_TOTAL_SIZE)
    val momentsB = DoubleArray(WU_TOTAL_SIZE)
    val moments = DoubleArray(WU_TOTAL_SIZE)
}

/** The index of cell ([r], [g], [b]) of the cube. */
internal fun wuIndex(
    r: Int,
    g: Int,
    b: Int,
): Int = (r shl (WU_INDEX_BITS * 2)) + (r shl (WU_INDEX_BITS + 1)) + r + (g shl WU_INDEX_BITS) + g + b

/** The sum of [moment] over [cube]. */
internal fun volume(
    cube: WuBox,
    moment: DoubleArray,
): Double =
    moment[wuIndex(cube.r1, cube.g1, cube.b1)] -
        moment[wuIndex(cube.r1, cube.g1, cube.b0)] -
        moment[wuIndex(cube.r1, cube.g0, cube.b1)] +
        moment[wuIndex(cube.r1, cube.g0, cube.b0)] -
        moment[wuIndex(cube.r0, cube.g1, cube.b1)] +
        moment[wuIndex(cube.r0, cube.g1, cube.b0)] +
        moment[wuIndex(cube.r0, cube.g0, cube.b1)] -
        moment[wuIndex(cube.r0, cube.g0, cube.b0)]

/** The part of [cube]'s sum of [moment] that a cut along [direction] leaves out, at the cube's lower bound. */
internal fun bottom(
    cube: WuBox,
    direction: WuDirection,
    moment: DoubleArray,
): Double =
    when (direction) {
        WuDirection.Red -> {
            -moment[wuIndex(cube.r0, cube.g1, cube.b1)] +
                moment[wuIndex(cube.r0, cube.g1, cube.b0)] +
                moment[wuIndex(cube.r0, cube.g0, cube.b1)] -
                moment[wuIndex(cube.r0, cube.g0, cube.b0)]
        }

        WuDirection.Green -> {
            -moment[wuIndex(cube.r1, cube.g0, cube.b1)] +
                moment[wuIndex(cube.r1, cube.g0, cube.b0)] +
                moment[wuIndex(cube.r0, cube.g0, cube.b1)] -
                moment[wuIndex(cube.r0, cube.g0, cube.b0)]
        }

        WuDirection.Blue -> {
            -moment[wuIndex(cube.r1, cube.g1, cube.b0)] +
                moment[wuIndex(cube.r1, cube.g0, cube.b0)] +
                moment[wuIndex(cube.r0, cube.g1, cube.b0)] -
                moment[wuIndex(cube.r0, cube.g0, cube.b0)]
        }
    }

/** The sum of [moment] over [cube]'s slab from its lower bound up to [position] along [direction]. */
internal fun top(
    cube: WuBox,
    direction: WuDirection,
    position: Int,
    moment: DoubleArray,
): Double =
    when (direction) {
        WuDirection.Red -> {
            moment[wuIndex(position, cube.g1, cube.b1)] -
                moment[wuIndex(position, cube.g1, cube.b0)] -
                moment[wuIndex(position, cube.g0, cube.b1)] +
                moment[wuIndex(position, cube.g0, cube.b0)]
        }

        WuDirection.Green -> {
            moment[wuIndex(cube.r1, position, cube.b1)] -
                moment[wuIndex(cube.r1, position, cube.b0)] -
                moment[wuIndex(cube.r0, position, cube.b1)] +
                moment[wuIndex(cube.r0, position, cube.b0)]
        }

        WuDirection.Blue -> {
            moment[wuIndex(cube.r1, cube.g1, position)] -
                moment[wuIndex(cube.r1, cube.g0, position)] -
                moment[wuIndex(cube.r0, cube.g1, position)] +
                moment[wuIndex(cube.r0, cube.g0, position)]
        }
    }

/** Turns [histogram]'s per-cell sums into sums over the box from the cube's origin to each cell. */
internal fun computeMoments(histogram: WuHistogram) {
    val sums = with(histogram) { listOf(weights, momentsR, momentsG, momentsB, moments) }
    for (r in 1 until WU_SIDE_LENGTH) {
        val area = Array(sums.size) { DoubleArray(WU_SIDE_LENGTH) }
        for (g in 1 until WU_SIDE_LENGTH) accumulateLine(sums, area, r, g)
    }
}

/** Adds line ([r], [g]) of each of [sums] to its running [area] and to the plane below, as upstream's loop does. */
private fun accumulateLine(
    sums: List<DoubleArray>,
    area: Array<DoubleArray>,
    r: Int,
    g: Int,
) {
    val line = DoubleArray(sums.size)
    for (b in 1 until WU_SIDE_LENGTH) {
        val index = wuIndex(r, g, b)
        val previousIndex = wuIndex(r - 1, g, b)
        for (k in sums.indices) {
            line[k] += sums[k][index]
            area[k][b] += line[k]
            sums[k][index] = sums[k][previousIndex] + area[k][b]
        }
    }
}
