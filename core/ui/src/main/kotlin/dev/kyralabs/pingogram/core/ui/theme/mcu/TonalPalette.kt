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
 * (https://github.com/material-foundation/material-color-utilities): palettes/tonal_palette.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The colors of one [hue] and [chroma] at every tone, as upstream's TonalPalette: a color role picks its tone from it.
 * [keyColor] is the palette's most representative color: for a palette made from a hue and chroma, the color of that
 * chroma nearest tone 50. Not thread-safe: tones are cached as they are asked for.
 */
internal class TonalPalette private constructor(
    val hue: Double,
    val chroma: Double,
    val keyColor: Hct,
) {
    private val cache = HashMap<Double, Int>()

    /** The palette's color at [tone], 0–100. */
    fun tone(tone: Double): Int =
        cache.getOrPut(tone) {
            if (tone == 99.0 && hue >= 105 && hue < 125) {
                averageArgb(tone(98.0), tone(100.0))
            } else {
                Hct.from(hue, chroma, tone).toInt()
            }
        }

    /** The palette's color at [tone], in HCT. */
    fun getHct(tone: Double): Hct = Hct.fromInt(tone(tone))

    companion object {
        /** The palette of [argb]'s hue and chroma, keyed by [argb] itself. */
        fun fromInt(argb: Int): TonalPalette = fromHct(Hct.fromInt(argb))

        /** The palette of [hct]'s hue and chroma, keyed by [hct] itself. */
        fun fromHct(hct: Hct): TonalPalette = TonalPalette(hct.hue, hct.chroma, hct)

        /** The palette of [hue] and [chroma], keyed by the color of that chroma nearest tone 50. */
        fun fromHueAndChroma(
            hue: Double,
            chroma: Double,
        ): TonalPalette = TonalPalette(hue, chroma, KeyColor(hue, chroma).create())

        /** The mean of two colors, channel by channel, as upstream averages yellow's tones 98 and 100 for tone 99. */
        private fun averageArgb(
            argb1: Int,
            argb2: Int,
        ): Int {
            val red = ((redFromArgb(argb1) + redFromArgb(argb2)) / 2.0).roundToInt()
            val green = ((greenFromArgb(argb1) + greenFromArgb(argb2)) / 2.0).roundToInt()
            val blue = ((blueFromArgb(argb1) + blueFromArgb(argb2)) / 2.0).roundToInt()
            return argbFromRgb(red, green, blue)
        }
    }
}

/** Finds a palette's key color: the requested chroma at the tone nearest 50 that reaches it, as upstream's KeyColor. */
private class KeyColor(
    private val hue: Double,
    private val requestedChroma: Double,
) {
    private val chromaCache = HashMap<Int, Double>()

    fun create(): Hct {
        var lowerTone = 0
        var upperTone = 100
        var found: Hct? = null
        while (found == null && lowerTone < upperTone) {
            val midTone = (lowerTone + upperTone) / 2
            val isAscending = maxChroma(midTone) < maxChroma(midTone + TONE_STEP)
            val sufficientChroma = maxChroma(midTone) >= requestedChroma - EPSILON
            when {
                sufficientChroma && abs(lowerTone - PIVOT_TONE) < abs(upperTone - PIVOT_TONE) -> upperTone = midTone
                sufficientChroma && lowerTone == midTone -> found = Hct.from(hue, requestedChroma, lowerTone.toDouble())
                sufficientChroma -> lowerTone = midTone
                isAscending -> lowerTone = midTone + TONE_STEP
                else -> upperTone = midTone
            }
        }
        return found ?: Hct.from(hue, requestedChroma, lowerTone.toDouble())
    }

    /** The most chroma the hue reaches at [tone]. */
    private fun maxChroma(tone: Int): Double =
        chromaCache.getOrPut(tone) { Hct.from(hue, MAX_CHROMA_VALUE, tone.toDouble()).chroma }

    private companion object {
        const val PIVOT_TONE = 50
        const val TONE_STEP = 1
        const val EPSILON = 0.01
        const val MAX_CHROMA_VALUE = 200.0
    }
}
