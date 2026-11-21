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
 * (https://github.com/material-foundation/material-color-utilities): hct/hct_solver, solving by J.
 * Restructured into smaller functions; the arithmetic and its order are upstream's.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Finds the sRGB color of an HCT hue, chroma and tone, as upstream's HctSolver: Newton's method on CAM16's J for the
 * tone's luminance, and where the chroma is out of gamut, the color of that hue and tone at the gamut's edge.
 */
internal object HctSolver {
    /** The color of [hueDegrees], [chroma] and [lstar] (the tone), or the closest in gamut. */
    fun solveToInt(
        hueDegrees: Double,
        chroma: Double,
        lstar: Double,
    ): Int {
        if (chroma < 0.0001 || lstar < 0.0001 || lstar > 99.9999) return argbFromLstar(lstar)
        val hueRadians = sanitizeDegreesDouble(hueDegrees) / 180 * PI
        val y = yFromLstar(lstar)
        val exactAnswer = findResultByJ(hueRadians, chroma, y)
        return if (exactAnswer != 0) exactAnswer else argbFromLinrgb(bisectToLimit(y, hueRadians))
    }

    /** The color of the hue, [chroma] and luminance [y] by Newton's method on J; 0 when none is in gamut. */
    private fun findResultByJ(
        hueRadians: Double,
        chroma: Double,
        y: Double,
    ): Int {
        val hue = HueTerms(hueRadians)
        var j = sqrt(y) * 11.0
        var result: Int? = null
        var round = 0
        while (result == null) {
            val linrgb = linrgbAtJ(j, chroma, hue)
            val fnj = Y_FROM_LINR * linrgb[0] + Y_FROM_LING * linrgb[1] + Y_FROM_LINB * linrgb[2]
            result =
                when {
                    linrgb.any { it < 0 } || fnj <= 0 -> 0
                    round == 4 || abs(fnj - y) < 0.002 -> if (linrgb.any { it > 100.01 }) 0 else argbFromLinrgb(linrgb)
                    else -> null
                }
            j -= (fnj - y) * j / (2 * fnj)
            round++
        }
        return result
    }

    /** The linear RGB color of lightness [j] and [chroma] at the hue of [hue], in the default viewing conditions. */
    private fun linrgbAtJ(
        j: Double,
        chroma: Double,
        hue: HueTerms,
    ): DoubleArray {
        val viewingConditions = ViewingConditions.DEFAULT
        val jNormalized = j / 100.0
        val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(jNormalized)
        val t = (alpha * T_INNER_COEFF).pow(1.0 / 0.9)
        val ac = viewingConditions.aw * jNormalized.pow(1.0 / viewingConditions.c / viewingConditions.z)
        val p2 = ac / viewingConditions.nbb
        val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * hue.p1 + 11 * t * hue.cos + 108.0 * t * hue.sin)
        val a = gamma * hue.cos
        val b = gamma * hue.sin
        val rA = (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0
        val gA = (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0
        val bA = (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0
        val scaled =
            doubleArrayOf(inverseChromaticAdaptation(rA), inverseChromaticAdaptation(gA), inverseChromaticAdaptation(bA))
        return matrixMultiply(scaled, LINRGB_FROM_SCALED_DISCOUNT)
    }

    private fun inverseChromaticAdaptation(adapted: Double): Double {
        val adaptedAbs = abs(adapted)
        val base = maxOf(0.0, 27.13 * adaptedAbs / (400.0 - adaptedAbs))
        return signum(adapted) * base.pow(1.0 / 0.42)
    }

    private val T_INNER_COEFF =
        1 / (1.64 - 0.29.pow(ViewingConditions.DEFAULT.n)).pow(0.73)
}

/** What a hue contributes to the inverse CAM16 transform, computed once per solve. */
private class HueTerms(
    hueRadians: Double,
) {
    val sin: Double = sin(hueRadians)
    val cos: Double = cos(hueRadians)
    val p1: Double =
        0.25 * (cos(hueRadians + 2.0) + 3.8) * (50000.0 / 13.0) * ViewingConditions.DEFAULT.nc *
            ViewingConditions.DEFAULT.ncb
}
