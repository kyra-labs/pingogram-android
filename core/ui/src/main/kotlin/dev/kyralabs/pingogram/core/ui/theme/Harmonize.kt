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
 * Ported to Kotlin by the Pingogram authors from material-color-utilities
 * (https://github.com/material-foundation/material-color-utilities): Blend.harmonize, Cam16,
 * ViewingConditions.DEFAULT and the ColorUtils they need. Instead of the analytic HctSolver, the
 * target HCT color is found by bisection (CAM16 J for the tone, chroma for the sRGB gamut), which
 * matches the reference implementation to within one 8-bit channel step.
 */

// Kept structurally close to the reference implementation so it can be diffed against upstream:
// one small function per formula and the bisection's early exits.
@file:Suppress("TooManyFunctions", "ReturnCount")

package dev.kyralabs.pingogram.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Shifts the hue of [designColor] toward [sourceColor] (by half the difference, at most 15°) while
 * keeping its HCT chroma and tone, like `MaterialColors.harmonize` in Material Components.
 */
internal fun harmonize(
    designColor: Color,
    sourceColor: Color,
): Color {
    val design = designColor.toArgb()
    val from = cam16FromArgb(design)
    val to = cam16FromArgb(sourceColor.toArgb())
    val difference = 180.0 - abs(abs(from.hue - to.hue) - 180.0)
    val rotation = min(difference * 0.5, 15.0)
    val direction = if (sanitizeDegrees(to.hue - from.hue) <= 180.0) 1.0 else -1.0
    val hue = sanitizeDegrees(from.hue + rotation * direction)
    return Color(solveToArgb(hue, from.chroma, lstarFromArgb(design)))
}

private class Cam16(
    val hue: Double,
    val chroma: Double,
)

/** The sRGB color with this CAM16 [hue], the highest in-gamut chroma up to [chroma], and L* = [tone]. */
private fun solveToArgb(
    hue: Double,
    chroma: Double,
    tone: Double,
): Int {
    if (chroma < 1e-4 || tone <= 0.0 || tone >= 100.0) {
        val gray = delinearized(yFromLstar(tone))
        return argbFromRgb(gray, gray, gray)
    }
    val requested = linearRgbAtTone(hue, chroma, tone)
    if (requested.isInGamut()) return requested.toArgb()

    var low = 0.0
    var high = chroma
    var best = linearRgbAtTone(hue, 0.0, tone)
    repeat(CHROMA_ITERATIONS) {
        val mid = (low + high) / 2
        val candidate = linearRgbAtTone(hue, mid, tone)
        if (candidate.isInGamut()) {
            low = mid
            best = candidate
        } else {
            high = mid
        }
    }
    return best.toArgb()
}

/** Linear sRGB (0..100, unclamped) at this CAM16 hue and chroma whose luminance matches [tone]. */
private fun linearRgbAtTone(
    hue: Double,
    chroma: Double,
    tone: Double,
): DoubleArray {
    val targetY = yFromLstar(tone)
    var low = 0.0
    var high = 100.0
    var rgb = DoubleArray(3)
    repeat(LIGHTNESS_ITERATIONS) {
        val j = (low + high) / 2
        rgb = linearRgbFromCam16(j, chroma, hue)
        if (rgb.luminance() < targetY) low = j else high = j
    }
    return rgb
}

private fun cam16FromArgb(argb: Int): Cam16 {
    val rgbA = XYZ_TO_CAM16RGB * (SRGB_TO_XYZ * linearRgb(argb))
    for (i in rgbA.indices) {
        val discounted = ViewingConditions.rgbD[i] * rgbA[i]
        val adapted = (ViewingConditions.fl * abs(discounted) / 100.0).pow(0.42)
        rgbA[i] = sign(discounted) * 400.0 * adapted / (adapted + 27.13)
    }
    val a = (11.0 * rgbA[0] - 12.0 * rgbA[1] + rgbA[2]) / 11.0
    val b = (rgbA[0] + rgbA[1] - 2.0 * rgbA[2]) / 9.0
    val u = (20.0 * rgbA[0] + 20.0 * rgbA[1] + 21.0 * rgbA[2]) / 20.0
    val p2 = (40.0 * rgbA[0] + 20.0 * rgbA[1] + rgbA[2]) / 20.0
    val hue = sanitizeDegrees(atan2(b, a) * 180.0 / PI)
    val j = 100.0 * (p2 * ViewingConditions.nbb / ViewingConditions.aw).pow(ViewingConditions.c * ViewingConditions.z)
    val huePrime = if (hue < 20.14) hue + 360.0 else hue
    val eccentricity = 0.25 * (cos(huePrime * PI / 180.0 + 2.0) + 3.8)
    val p1 = 50000.0 / 13.0 * eccentricity * ViewingConditions.nc * ViewingConditions.ncb
    val t = p1 * hypot(a, b) / (u + 0.305)
    val alpha = (1.64 - 0.29.pow(ViewingConditions.n)).pow(0.73) * t.pow(0.9)
    return Cam16(hue = hue, chroma = alpha * sqrt(j / 100.0))
}

/** Inverse CAM16: lightness [j], [chroma] and [hue] to linear sRGB (0..100), without clamping. */
private fun linearRgbFromCam16(
    j: Double,
    chroma: Double,
    hue: Double,
): DoubleArray {
    val alpha = if (chroma == 0.0 || j == 0.0) 0.0 else chroma / sqrt(j / 100.0)
    val t = (alpha / (1.64 - 0.29.pow(ViewingConditions.n)).pow(0.73)).pow(1.0 / 0.9)
    val hueRadians = hue * PI / 180.0
    val eccentricity = 0.25 * (cos(hueRadians + 2.0) + 3.8)
    val achromatic = ViewingConditions.aw * (j / 100.0).pow(1.0 / ViewingConditions.c / ViewingConditions.z)
    val p1 = eccentricity * (50000.0 / 13.0) * ViewingConditions.nc * ViewingConditions.ncb
    val p2 = achromatic / ViewingConditions.nbb
    val hueSin = sin(hueRadians)
    val hueCos = cos(hueRadians)
    val gamma = 23.0 * (p2 + 0.305) * t / (23.0 * p1 + 11.0 * t * hueCos + 108.0 * t * hueSin)
    val a = gamma * hueCos
    val b = gamma * hueSin
    val rgbA =
        doubleArrayOf(
            (460.0 * p2 + 451.0 * a + 288.0 * b) / 1403.0,
            (460.0 * p2 - 891.0 * a - 261.0 * b) / 1403.0,
            (460.0 * p2 - 220.0 * a - 6300.0 * b) / 1403.0,
        )
    val rgbF =
        DoubleArray(3) { i ->
            val base = max(0.0, 27.13 * abs(rgbA[i]) / (400.0 - abs(rgbA[i])))
            sign(rgbA[i]) * (100.0 / ViewingConditions.fl) * base.pow(1.0 / 0.42) / ViewingConditions.rgbD[i]
        }
    return XYZ_TO_SRGB * (CAM16RGB_TO_XYZ * rgbF)
}

/** `ViewingConditions.DEFAULT`: D65 white, background L* 50, average surround, no discounting. */
private object ViewingConditions {
    val n: Double
    val aw: Double
    val nbb: Double
    val ncb: Double
    val c: Double
    val nc: Double
    val rgbD: DoubleArray
    val fl: Double
    val z: Double

    init {
        val adaptingLuminance = 200.0 / PI * yFromLstar(50.0) / 100.0
        val surround = 2.0
        val rgbW = XYZ_TO_CAM16RGB * WHITE_POINT_D65
        val f = 0.8 + surround / 10.0
        c = if (f >= 0.9) lerp(0.59, 0.69, (f - 0.9) * 10.0) else lerp(0.525, 0.59, (f - 0.8) * 10.0)
        val d = (f * (1.0 - 1.0 / 3.6 * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)
        nc = f
        rgbD = DoubleArray(3) { i -> d * (100.0 / rgbW[i]) + 1.0 - d }
        val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
        val k4 = k * k * k * k
        val k4F = 1.0 - k4
        fl = k4 * adaptingLuminance + 0.1 * k4F * k4F * cbrt(5.0 * adaptingLuminance)
        n = yFromLstar(50.0) / WHITE_POINT_D65[1]
        z = 1.48 + sqrt(n)
        nbb = 0.725 / n.pow(0.2)
        ncb = nbb
        val rgbA =
            DoubleArray(3) { i ->
                val factor = (fl * rgbD[i] * rgbW[i] / 100.0).pow(0.42)
                400.0 * factor / (factor + 27.13)
            }
        aw = (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb
    }
}

private const val LIGHTNESS_ITERATIONS = 30
private const val CHROMA_ITERATIONS = 20
private const val GAMUT_EPSILON = 0.01
private const val LAB_E = 216.0 / 24389.0
private const val LAB_KAPPA = 24389.0 / 27.0

private val SRGB_TO_XYZ =
    arrayOf(
        doubleArrayOf(0.41233895, 0.35762064, 0.18051042),
        doubleArrayOf(0.2126, 0.7152, 0.0722),
        doubleArrayOf(0.01932141, 0.11916382, 0.95034478),
    )
private val XYZ_TO_SRGB =
    arrayOf(
        doubleArrayOf(3.2413774792388685, -1.5376652402851851, -0.49885366846268053),
        doubleArrayOf(-0.9691452513005321, 1.8758853451067872, 0.04156585616912061),
        doubleArrayOf(0.05562093689691305, -0.20395524564742123, 1.0571799111220335),
    )
private val XYZ_TO_CAM16RGB =
    arrayOf(
        doubleArrayOf(0.401288, 0.650173, -0.051461),
        doubleArrayOf(-0.250268, 1.204414, 0.045854),
        doubleArrayOf(-0.002079, 0.048952, 0.953127),
    )
private val CAM16RGB_TO_XYZ =
    arrayOf(
        doubleArrayOf(1.8620678, -1.0112547, 0.14918678),
        doubleArrayOf(0.38752654, 0.62144744, -0.00897398),
        doubleArrayOf(-0.01584150, -0.03412294, 1.0499644),
    )
private val WHITE_POINT_D65 = doubleArrayOf(95.047, 100.0, 108.883)

private operator fun Array<DoubleArray>.times(vector: DoubleArray): DoubleArray =
    DoubleArray(3) { row -> this[row][0] * vector[0] + this[row][1] * vector[1] + this[row][2] * vector[2] }

private fun DoubleArray.luminance(): Double =
    SRGB_TO_XYZ[1][0] * this[0] + SRGB_TO_XYZ[1][1] * this[1] + SRGB_TO_XYZ[1][2] * this[2]

private fun DoubleArray.isInGamut(): Boolean = all { it >= -GAMUT_EPSILON && it <= 100.0 + GAMUT_EPSILON }

private fun DoubleArray.toArgb(): Int = argbFromRgb(delinearized(this[0]), delinearized(this[1]), delinearized(this[2]))

private fun argbFromRgb(
    red: Int,
    green: Int,
    blue: Int,
): Int = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

private fun linearRgb(argb: Int): DoubleArray =
    doubleArrayOf(linearized(argb shr 16 and 0xFF), linearized(argb shr 8 and 0xFF), linearized(argb and 0xFF))

private fun lstarFromArgb(argb: Int): Double = 116.0 * labF(linearRgb(argb).luminance() / 100.0) - 16.0

private fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

private fun linearized(component: Int): Double {
    val normalized = component / 255.0
    val linear = if (normalized <= 0.040449936) normalized / 12.92 else ((normalized + 0.055) / 1.055).pow(2.4)
    return linear * 100.0
}

private fun delinearized(linear: Double): Int {
    val normalized = linear / 100.0
    val gamma = if (normalized <= 0.0031308) normalized * 12.92 else 1.055 * normalized.pow(1.0 / 2.4) - 0.055
    return (gamma * 255.0).roundToInt().coerceIn(0, 255)
}

private fun labF(t: Double): Double = if (t > LAB_E) cbrt(t) else (LAB_KAPPA * t + 16.0) / 116.0

private fun labInvf(ft: Double): Double {
    val ft3 = ft * ft * ft
    return if (ft3 > LAB_E) ft3 else (116.0 * ft - 16.0) / LAB_KAPPA
}

private fun lerp(
    start: Double,
    stop: Double,
    amount: Double,
): Double = (1.0 - amount) * start + amount * stop

private fun sanitizeDegrees(degrees: Double): Double = (degrees % 360.0 + 360.0) % 360.0
