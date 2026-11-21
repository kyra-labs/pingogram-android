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
 * (https://github.com/material-foundation/material-color-utilities): score/score.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Ranks an image's colors for use as a theme's seed, as upstream's Score: colors of the hues that cover much of the
 * image and of a chroma near 48 score best; with [rank]'s filter on, greys and hues that cover under 1% are left out.
 * The chosen colors are as far apart in hue as their number allows.
 */
internal object Score {
    private const val TARGET_CHROMA = 48.0
    private const val WEIGHT_PROPORTION = 0.7
    private const val WEIGHT_CHROMA_ABOVE = 0.3
    private const val WEIGHT_CHROMA_BELOW = 0.1
    private const val CUTOFF_CHROMA = 5.0
    private const val CUTOFF_EXCITED_PROPORTION = 0.01
    private const val FALLBACK_COLOR = 0xFF4285F4.toInt()

    /**
     * Up to [desired] colors of [colorsToPopulation] (ARGB to pixel count, Celebi's result), best first; Google Blue
     * ([fallbackColorArgb]) when none is suitable.
     */
    fun rank(
        colorsToPopulation: Map<Int, Int>,
        desired: Int = 4,
        fallbackColorArgb: Int = FALLBACK_COLOR,
        filter: Boolean = true,
    ): List<Int> {
        val colorsHct = colorsToPopulation.keys.map(Hct::fromInt)
        val excited = hueExcitedProportions(colorsToPopulation.values.toList(), colorsHct)
        val scored =
            colorsHct
                .mapNotNull { hct -> scoreOf(hct, excited, filter)?.let { hct to it } }
                .sortedByDescending { it.second }
                .map { it.first }
        val chosen = choose(scored, desired)
        return if (chosen.isEmpty()) listOf(fallbackColorArgb) else chosen.map { it.toInt() }
    }

    /** How much of the image each whole hue covers, each pixel counting for the 30 hues around its own. */
    private fun hueExcitedProportions(
        populations: List<Int>,
        colorsHct: List<Hct>,
    ): DoubleArray {
        val huePopulation = DoubleArray(360)
        var populationSum = 0.0
        colorsHct.forEachIndexed { index, hct ->
            huePopulation[floor(hct.hue).toInt()] += populations[index].toDouble()
            populationSum += populations[index]
        }
        val hueExcitedProportions = DoubleArray(360)
        for (hue in 0 until 360) {
            val proportion = huePopulation[hue] / populationSum
            for (i in hue - 14 until hue + 16) hueExcitedProportions[sanitizeDegreesInt(i)] += proportion
        }
        return hueExcitedProportions
    }

    /** [hct]'s score, or null when the filter leaves it out. */
    private fun scoreOf(
        hct: Hct,
        hueExcitedProportions: DoubleArray,
        filter: Boolean,
    ): Double? {
        val proportion = hueExcitedProportions[sanitizeDegreesInt(hct.hue.roundToInt())]
        val filteredOut = filter && (hct.chroma < CUTOFF_CHROMA || proportion <= CUTOFF_EXCITED_PROPORTION)
        val chromaWeight = if (hct.chroma < TARGET_CHROMA) WEIGHT_CHROMA_BELOW else WEIGHT_CHROMA_ABOVE
        return if (filteredOut) {
            null
        } else {
            proportion * 100.0 * WEIGHT_PROPORTION +
                (hct.chroma - TARGET_CHROMA) * chromaWeight
        }
    }

    /** The best [desired] of [scored] that are at least some degrees apart, starting at 90° and settling for 15°. */
    private fun choose(
        scored: List<Hct>,
        desired: Int,
    ): List<Hct> {
        var chosen = emptyList<Hct>()
        var differenceDegrees = 90
        while (differenceDegrees >= 15 && chosen.size < desired) {
            chosen = pick(scored, desired, differenceDegrees)
            differenceDegrees--
        }
        return chosen
    }

    private fun pick(
        scored: List<Hct>,
        desired: Int,
        minDifference: Int,
    ): List<Hct> {
        val chosen = mutableListOf<Hct>()
        for (hct in scored) {
            if (chosen.size >= desired) break
            if (chosen.none { differenceDegrees(hct.hue, it.hue) < minDifference }) chosen += hct
        }
        return chosen
    }
}
