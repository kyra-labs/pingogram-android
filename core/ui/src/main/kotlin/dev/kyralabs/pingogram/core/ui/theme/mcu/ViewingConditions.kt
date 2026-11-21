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
 * (https://github.com/material-foundation/material-color-utilities): hct/viewing_conditions.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.PI
import kotlin.math.cbrt
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * The environment a color is seen in, which CAM16 needs: upstream's ViewingConditions, its values computed from the
 * [whitePoint] (XYZ), the [adaptingLuminance] (cd/m²), the L* of the background and the [surround] (0 dark, 1 dim, 2
 * average). [DEFAULT] is sRGB's: a D65 white point, 11.72 cd/m², a background at L* 50 and an average surround, which
 * is what Material's HCT uses.
 */
internal class ViewingConditions private constructor(
    whitePoint: DoubleArray,
    adaptingLuminance: Double,
    backgroundLstar: Double,
    surround: Double,
) {
    private val rgbW = matrixMultiply(whitePoint, XYZ_TO_CAM16RGB)

    /** The surround's factor: upstream's F, also used as N_c. */
    val nc: Double = 0.8 + surround / 10.0

    val c: Double = if (nc >= 0.9) lerp(0.59, 0.69, (nc - 0.9) * 10.0) else lerp(0.525, 0.59, (nc - 0.8) * 10.0)

    private val d = (nc * (1.0 - (1.0 / 3.6) * exp((-adaptingLuminance - 42.0) / 92.0))).coerceIn(0.0, 1.0)

    val rgbD: DoubleArray = DoubleArray(3) { d * (100.0 / rgbW[it]) + 1.0 - d }

    val fl: Double =
        run {
            val k = 1.0 / (5.0 * adaptingLuminance + 1.0)
            val k4 = k * k * k * k
            val k4F = 1.0 - k4
            k4 * adaptingLuminance + 0.1 * k4F * k4F * cbrt(5.0 * adaptingLuminance)
        }

    val fLRoot: Double = fl.pow(0.25)

    val n: Double = yFromLstar(backgroundLstar) / whitePoint[1]

    val z: Double = 1.48 + sqrt(n)

    val nbb: Double = 0.725 / n.pow(0.2)

    val ncb: Double = nbb

    val aw: Double =
        run {
            val rgbA =
                DoubleArray(3) {
                    val factor = ((fl * rgbD[it] * rgbW[it]) / 100.0).pow(0.42)
                    (400.0 * factor) / (factor + 27.13)
                }
            (2.0 * rgbA[0] + rgbA[1] + 0.05 * rgbA[2]) * nbb
        }

    companion object {
        val DEFAULT: ViewingConditions =
            ViewingConditions(
                whitePoint = WHITE_POINT_D65,
                adaptingLuminance = (200.0 / PI) * yFromLstar(50.0) / 100.0,
                backgroundLstar = 50.0,
                surround = 2.0,
            )
    }
}

/** XYZ to CAM16's cone responses (upstream's inline coefficients). */
internal val XYZ_TO_CAM16RGB: Array<DoubleArray> =
    arrayOf(
        doubleArrayOf(0.401288, 0.650173, -0.051461),
        doubleArrayOf(-0.250268, 1.204414, 0.045854),
        doubleArrayOf(-0.002079, 0.048952, 0.953127),
    )
