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
 * (https://github.com/material-foundation/material-color-utilities): hct/hct_solver, the bisection
 * fallback. Restructured into smaller functions; the arithmetic and its order are upstream's.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow

// Where Newton's method on J finds no color in gamut, the solver bisects the sRGB cube's edges and then its critical
// planes for the color of the target hue and Y nearest the gamut's edge, as upstream's HctSolver does.

/** The linear RGB color of luminance [y] and the hue [targetHue] (radians) at the gamut's edge. */
internal fun bisectToLimit(
    y: Double,
    targetHue: Double,
): DoubleArray {
    val segment = bisectToSegment(y, targetHue)
    for (axis in 0 until 3) {
        if (segment.left[axis] != segment.right[axis]) segment.narrow(axis, targetHue)
    }
    return DoubleArray(3) { (segment.left[it] + segment.right[it]) / 2 }
}

/** Two points of the gamut's edge whose hues enclose the target's; [narrow] moves them closer. */
private class Segment(
    var left: DoubleArray,
    var right: DoubleArray,
) {
    var leftHue: Double = hueOf(left)
}

/** The two vertices of the plane of luminance [y] cut by the sRGB cube whose hues enclose [targetHue]. */
private fun bisectToSegment(
    y: Double,
    targetHue: Double,
): Segment {
    var left = doubleArrayOf(-1.0, -1.0, -1.0)
    var right = left
    var leftHue = 0.0
    var rightHue = 0.0
    var initialized = false
    var uncut = true
    for (n in 0 until 12) {
        val mid = nthVertex(y, n) ?: continue
        val midHue = hueOf(mid)
        if (!initialized) {
            left = mid
            right = mid
            leftHue = midHue
            rightHue = midHue
            initialized = true
        } else if (uncut || areInCyclicOrder(leftHue, midHue, rightHue)) {
            uncut = false
            if (areInCyclicOrder(leftHue, targetHue, midHue)) {
                right = mid
                rightHue = midHue
            } else {
                left = mid
                leftHue = midHue
            }
        }
    }
    return Segment(left, right)
}

/** Bisects along [axis] over the critical planes between the two points, keeping [targetHue] between them. */
private fun Segment.narrow(
    axis: Int,
    targetHue: Double,
) {
    val increasing = left[axis] < right[axis]
    val leftDelinearized = trueDelinearized(left[axis])
    val rightDelinearized = trueDelinearized(right[axis])
    var lPlane = if (increasing) criticalPlaneBelow(leftDelinearized) else criticalPlaneAbove(leftDelinearized)
    var rPlane = if (increasing) criticalPlaneAbove(rightDelinearized) else criticalPlaneBelow(rightDelinearized)
    var round = 0
    while (round < 8 && abs(rPlane - lPlane) > 1) {
        val mPlane = floor((lPlane + rPlane) / 2.0).toInt()
        val t = (CRITICAL_PLANES[mPlane] - left[axis]) / (right[axis] - left[axis])
        val mid = DoubleArray(3) { left[it] + (right[it] - left[it]) * t }
        val midHue = hueOf(mid)
        if (areInCyclicOrder(leftHue, targetHue, midHue)) {
            right = mid
            rPlane = mPlane
        } else {
            left = mid
            leftHue = midHue
            lPlane = mPlane
        }
        round++
    }
}

/**
 * The [n]th of the twelve edges of the sRGB cube cut by the plane of luminance [y], as linear RGB; null where that
 * edge does not reach it.
 */
private fun nthVertex(
    y: Double,
    n: Int,
): DoubleArray? {
    val coordA = if (n % 4 <= 1) 0.0 else 100.0
    val coordB = if (n % 2 == 0) 0.0 else 100.0
    val (vertex, solved) =
        when {
            n < 4 -> doubleArrayOf((y - coordA * Y_FROM_LING - coordB * Y_FROM_LINB) / Y_FROM_LINR, coordA, coordB) to 0
            n < 8 -> doubleArrayOf(coordB, (y - coordB * Y_FROM_LINR - coordA * Y_FROM_LINB) / Y_FROM_LING, coordA) to 1
            else -> doubleArrayOf(coordA, coordB, (y - coordA * Y_FROM_LINR - coordB * Y_FROM_LING) / Y_FROM_LINB) to 2
        }
    return vertex.takeIf { vertex[solved] in 0.0..100.0 }
}

private fun areInCyclicOrder(
    a: Double,
    b: Double,
    c: Double,
): Boolean = sanitizeRadians(b - a) < sanitizeRadians(c - a)

private fun sanitizeRadians(angle: Double): Double = (angle + PI * 8) % (PI * 2)

/** A linear channel as an unrounded 0–255 sRGB value. */
private fun trueDelinearized(rgbComponent: Double): Double {
    val normalized = rgbComponent / 100.0
    val delinearized =
        if (normalized <= 0.0031308) normalized * 12.92 else 1.055 * normalized.pow(1.0 / 2.4) - 0.055
    return delinearized * 255.0
}

private fun criticalPlaneBelow(x: Double): Int = floor(x - 0.5).toInt()

private fun criticalPlaneAbove(x: Double): Int = ceil(x - 0.5).toInt()
