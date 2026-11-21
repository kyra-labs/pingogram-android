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
 * (https://github.com/material-foundation/material-color-utilities): hct/hct_solver, its tables.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.pow

// The HCT solver's constants, as upstream's HctSolver has them.

/** Linear RGB to CAM16's cone responses, discounted by the illuminant and scaled. */
internal val SCALED_DISCOUNT_FROM_LINRGB: Array<DoubleArray> =
    arrayOf(
        doubleArrayOf(0.001200833568784504, 0.002389694492170889, 0.0002795742885861124),
        doubleArrayOf(0.0005891086651375999, 0.0029785502573438758, 0.0003270666104008398),
        doubleArrayOf(0.00010146692491640572, 0.0005364214359186694, 0.0032979401770712076),
    )

/** The inverse of [SCALED_DISCOUNT_FROM_LINRGB]. */
internal val LINRGB_FROM_SCALED_DISCOUNT: Array<DoubleArray> =
    arrayOf(
        doubleArrayOf(1373.2198709594231, -1100.4251190754821, -7.278681089101213),
        doubleArrayOf(-271.815969077903, 559.6580465940733, -32.46047482791194),
        doubleArrayOf(1.9622899599665666, -57.173814538844006, 308.7233197812385),
    )

/** Relative luminance from linear RGB. */
internal const val Y_FROM_LINR = 0.2126
internal const val Y_FROM_LING = 0.7152
internal const val Y_FROM_LINB = 0.0722

/**
 * The linear RGB values halfway between consecutive 8-bit sRGB values: the planes where a channel's rounding changes,
 * which the bisection stops at. Upstream lists them; each is [linearized]'s curve at a half step, and a test checks
 * the two agree exactly.
 */
internal val CRITICAL_PLANES: DoubleArray = DoubleArray(255) { linearizedHalfStep(it + 0.5) }

private fun linearizedHalfStep(rgbComponent: Double): Double {
    val normalized = rgbComponent / 255.0
    return if (normalized <= 0.040449936) {
        normalized / 12.92 * 100.0
    } else {
        ((normalized + 0.055) / 1.055).pow(2.4) * 100.0
    }
}
