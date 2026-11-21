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
 * (https://github.com/material-foundation/material-color-utilities): utils/color_utils, the ARGB and XYZ part.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.pow
import kotlin.math.roundToInt

// Conversions between ARGB, linear RGB and XYZ, as upstream's ColorUtils. Colors are ARGB in an Int.

internal val SRGB_TO_XYZ: Array<DoubleArray> =
    arrayOf(
        doubleArrayOf(0.41233895, 0.35762064, 0.18051042),
        doubleArrayOf(0.2126, 0.7152, 0.0722),
        doubleArrayOf(0.01932141, 0.11916382, 0.95034478),
    )

private val XYZ_TO_SRGB: Array<DoubleArray> =
    arrayOf(
        doubleArrayOf(3.2413774792388685, -1.5376652402851851, -0.49885366846268053),
        doubleArrayOf(-0.9691452513005321, 1.8758853451067872, 0.04156585616912061),
        doubleArrayOf(0.05562093689691305, -0.20395524564742123, 1.0571799111220335),
    )

/** The D65 white point in XYZ, Y at 100. */
internal val WHITE_POINT_D65: DoubleArray = doubleArrayOf(95.047, 100.0, 108.883)

/** An opaque color from its 0–255 channels. */
internal fun argbFromRgb(
    red: Int,
    green: Int,
    blue: Int,
): Int = (255 shl 24) or ((red and 255) shl 16) or ((green and 255) shl 8) or (blue and 255)

/** An opaque color from linear RGB, 0–100 per channel. */
internal fun argbFromLinrgb(linrgb: DoubleArray): Int =
    argbFromRgb(delinearized(linrgb[0]), delinearized(linrgb[1]), delinearized(linrgb[2]))

internal fun alphaFromArgb(argb: Int): Int = (argb shr 24) and 255

internal fun redFromArgb(argb: Int): Int = (argb shr 16) and 255

internal fun greenFromArgb(argb: Int): Int = (argb shr 8) and 255

internal fun blueFromArgb(argb: Int): Int = argb and 255

/** An opaque color from XYZ, Y at 100 for white. */
internal fun argbFromXyz(
    x: Double,
    y: Double,
    z: Double,
): Int = argbFromLinrgb(matrixMultiply(doubleArrayOf(x, y, z), XYZ_TO_SRGB))

/** [argb] in XYZ, Y at 100 for white. */
internal fun xyzFromArgb(argb: Int): DoubleArray =
    matrixMultiply(
        doubleArrayOf(linearized(redFromArgb(argb)), linearized(greenFromArgb(argb)), linearized(blueFromArgb(argb))),
        SRGB_TO_XYZ,
    )

/** A 0–255 sRGB channel linearized, 0–100. */
internal fun linearized(rgbComponent: Int): Double {
    val normalized = rgbComponent / 255.0
    return if (normalized <= 0.040449936) {
        normalized / 12.92 * 100.0
    } else {
        ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
    }
}

/** A 0–100 linear channel as a 0–255 sRGB one, rounded and clamped. */
internal fun delinearized(rgbComponent: Double): Int {
    val normalized = rgbComponent / 100.0
    val delinearized =
        if (normalized <= 0.0031308) normalized * 12.92 else 1.055 * normalized.pow(1.0 / 2.4) - 0.055
    return (delinearized * 255.0).roundToInt().coerceIn(0, 255)
}
