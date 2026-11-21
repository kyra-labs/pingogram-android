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
 * (https://github.com/material-foundation/material-color-utilities): utils/color_utils, the L*a*b* part.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.pow

// CIE L*a*b* and L* (Material's tone), as upstream's ColorUtils.

private const val LAB_E = 216.0 / 24389.0
private const val LAB_KAPPA = 24389.0 / 27.0

/** An opaque color from L*a*b*. */
internal fun argbFromLab(
    l: Double,
    a: Double,
    b: Double,
): Int {
    val fy = (l + 16.0) / 116.0
    val fx = a / 500.0 + fy
    val fz = fy - b / 200.0
    return argbFromXyz(
        labInvf(fx) * WHITE_POINT_D65[0],
        labInvf(fy) * WHITE_POINT_D65[1],
        labInvf(fz) * WHITE_POINT_D65[2],
    )
}

/** [argb] in L*a*b*. */
internal fun labFromArgb(argb: Int): DoubleArray {
    val xyz = xyzFromArgb(argb)
    val fx = labF(xyz[0] / WHITE_POINT_D65[0])
    val fy = labF(xyz[1] / WHITE_POINT_D65[1])
    val fz = labF(xyz[2] / WHITE_POINT_D65[2])
    return doubleArrayOf(116.0 * fy - 16, 500.0 * (fx - fy), 200.0 * (fy - fz))
}

/** The grey of L* [lstar]. */
internal fun argbFromLstar(lstar: Double): Int {
    val component = delinearized(yFromLstar(lstar))
    return argbFromRgb(component, component, component)
}

/** The L* (Material's tone) of [argb]. */
internal fun lstarFromArgb(argb: Int): Double = 116.0 * labF(xyzFromArgb(argb)[1] / 100.0) - 16.0

/** The relative luminance Y, 0–100, of L* [lstar]. */
internal fun yFromLstar(lstar: Double): Double = 100.0 * labInvf((lstar + 16.0) / 116.0)

/** The L* of relative luminance [y], 0–100. */
internal fun lstarFromY(y: Double): Double = labF(y / 100.0) * 116.0 - 16.0

private fun labF(t: Double): Double = if (t > LAB_E) t.pow(1.0 / 3.0) else (LAB_KAPPA * t + 16) / 116

private fun labInvf(ft: Double): Double {
    val ft3 = ft * ft * ft
    return if (ft3 > LAB_E) ft3 else (116 * ft - 16) / LAB_KAPPA
}
