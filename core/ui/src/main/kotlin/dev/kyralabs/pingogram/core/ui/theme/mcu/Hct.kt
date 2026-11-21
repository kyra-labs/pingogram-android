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
 * (https://github.com/material-foundation/material-color-utilities): hct/hct.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

/**
 * A color in HCT, Material's color space: [hue] and [chroma] from CAM16, [tone] the L* of L*a*b*, so that a tone
 * difference is a contrast difference. Immutable, unlike upstream's, whose setters solve for a new color: use [from].
 */
internal class Hct private constructor(
    private val argb: Int,
) {
    private val cam = Cam16.fromInt(argb)

    /** Hue in degrees, 0 ≤ hue < 360. */
    val hue: Double get() = cam.hue

    /** Colorfulness, 0 for greys; how high it can go depends on hue and tone. */
    val chroma: Double get() = cam.chroma

    /** Lightness, 0 (black) to 100 (white). */
    val tone: Double = lstarFromArgb(argb)

    /** This color as ARGB. */
    fun toInt(): Int = argb

    override fun toString(): String = "HCT(${hue.toInt()}, ${chroma.toInt()}, ${tone.toInt()})"

    companion object {
        /** [argb] in HCT. */
        fun fromInt(argb: Int): Hct = Hct(argb)

        /** The color of [hue], [chroma] and [tone], or the closest in sRGB's gamut: chroma gives way first. */
        fun from(
            hue: Double,
            chroma: Double,
            tone: Double,
        ): Hct = Hct(HctSolver.solveToInt(hue, chroma, tone))
    }
}
