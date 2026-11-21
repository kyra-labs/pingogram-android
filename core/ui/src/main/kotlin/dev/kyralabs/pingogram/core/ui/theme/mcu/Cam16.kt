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
 * (https://github.com/material-foundation/material-color-utilities): hct/cam16, the part that finds a
 * color's hue and chroma.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * A color in CAM16, the color appearance model HCT takes its hue and chroma from: [hue] in degrees, [chroma], and [j],
 * its lightness. Only what the port uses is kept: upstream's UCS coordinates, distance and inverse are left out.
 */
internal class Cam16 private constructor(
    val hue: Double,
    val chroma: Double,
    val j: Double,
) {
    companion object {
        /** [argb] seen in [ViewingConditions.DEFAULT]. */
        fun fromInt(argb: Int): Cam16 = fromIntInViewingConditions(argb, ViewingConditions.DEFAULT)

        /** [argb] seen in [viewingConditions]. */
        fun fromIntInViewingConditions(
            argb: Int,
            viewingConditions: ViewingConditions,
        ): Cam16 {
            val xyz = xyzFromArgb(argb)
            val cone = matrixMultiply(xyz, XYZ_TO_CAM16RGB)
            val rgbA = DoubleArray(3) { adapted(viewingConditions.rgbD[it] * cone[it], viewingConditions) }
            val a = (11.0 * rgbA[0] + -12.0 * rgbA[1] + rgbA[2]) / 11.0
            val b = (rgbA[0] + rgbA[1] - 2.0 * rgbA[2]) / 9.0
            val u = (20.0 * rgbA[0] + 20.0 * rgbA[1] + 21.0 * rgbA[2]) / 20.0
            val p2 = (40.0 * rgbA[0] + 20.0 * rgbA[1] + rgbA[2]) / 20.0
            val hue = sanitizeDegreesDouble((atan2(b, a) * 180.0) / PI)
            val ac = p2 * viewingConditions.nbb
            val j = 100.0 * (ac / viewingConditions.aw).pow(viewingConditions.c * viewingConditions.z)
            val huePrime = if (hue < 20.14) hue + 360 else hue
            val eHue = 0.25 * (cos((huePrime * PI) / 180.0 + 2.0) + 3.8)
            val p1 = (50000.0 / 13.0) * eHue * viewingConditions.nc * viewingConditions.ncb
            val t = (p1 * sqrt(a * a + b * b)) / (u + 0.305)
            val alpha = t.pow(0.9) * (1.64 - 0.29.pow(viewingConditions.n)).pow(0.73)
            return Cam16(hue = hue, chroma = alpha * sqrt(j / 100.0), j = j)
        }

        /** A cone response [discounted] by the illuminant, after the post-adaptation compression. */
        private fun adapted(
            discounted: Double,
            viewingConditions: ViewingConditions,
        ): Double {
            val factor = ((viewingConditions.fl * abs(discounted)) / 100.0).pow(0.42)
            return (signum(discounted) * 400.0 * factor) / (factor + 27.13)
        }
    }
}
