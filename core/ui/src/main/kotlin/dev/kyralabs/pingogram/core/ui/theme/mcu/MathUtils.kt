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
 * (https://github.com/material-foundation/material-color-utilities): utils/math_utils.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.abs

// The arithmetic the rest of the port shares, as upstream's MathUtils.

/** -1, 0 or 1 by the sign of [num]. */
internal fun signum(num: Double): Int =
    when {
        num < 0 -> -1
        num == 0.0 -> 0
        else -> 1
    }

/** [start] at 0, [stop] at 1, linear in between. */
internal fun lerp(
    start: Double,
    stop: Double,
    amount: Double,
): Double = (1.0 - amount) * start + amount * stop

/** [degrees] in [0, 360). */
internal fun sanitizeDegreesInt(degrees: Int): Int {
    val remainder = degrees % 360
    return if (remainder < 0) remainder + 360 else remainder
}

/** [degrees] in [0, 360). */
internal fun sanitizeDegreesDouble(degrees: Double): Double {
    val remainder = degrees % 360.0
    return if (remainder < 0) remainder + 360.0 else remainder
}

/** 1 when the shortest way from hue [from] to hue [to] increases the hue, else -1. */
internal fun rotationDirection(
    from: Double,
    to: Double,
): Double = if (sanitizeDegreesDouble(to - from) <= 180.0) 1.0 else -1.0

/** The shortest distance between hues [a] and [b], in degrees. */
internal fun differenceDegrees(
    a: Double,
    b: Double,
): Double = 180.0 - abs(abs(a - b) - 180.0)

/** [row] multiplied by the 3 × 3 [matrix]. */
internal fun matrixMultiply(
    row: DoubleArray,
    matrix: Array<DoubleArray>,
): DoubleArray = DoubleArray(3) { i -> row[0] * matrix[i][0] + row[1] * matrix[i][1] + row[2] * matrix[i][2] }
