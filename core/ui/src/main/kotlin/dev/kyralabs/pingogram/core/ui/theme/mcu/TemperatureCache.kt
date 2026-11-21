/*
 * Copyright 2023 Google LLC
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
 * (https://github.com/material-foundation/material-color-utilities): temperature/temperature_cache,
 * the analogous colors SchemeContent's tertiary palette takes; the complement is left out.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Colors of [input]'s chroma and tone arranged by how warm they look, as upstream's TemperatureCache: [analogous]
 * finds colors spaced evenly in temperature rather than in hue. Not thread-safe: what it computes is cached.
 */
internal class TemperatureCache(
    private val input: Hct,
) {
    /** One color for every whole hue, 0 to 360, at [input]'s chroma and tone. */
    private val hctsByHue: List<Hct> by lazy {
        (0..360).map { hue -> Hct.from(hue.toDouble(), input.chroma, input.tone) }
    }

    /** Each color of [hctsByHue] and [input], by identity, with its raw temperature. */
    private val tempsByHct: Map<Hct, Double> by lazy {
        (hctsByHue + input).associateWith(::rawTemperature)
    }

    /** [hctsByHue] and [input], coldest first. */
    private val hctsByTemp: List<Hct> by lazy {
        (hctsByHue + input).sortedBy { tempsByHct.getValue(it) }
    }

    private val warmest: Hct get() = hctsByTemp.last()

    private val coldest: Hct get() = hctsByTemp.first()

    /**
     * [count] colors around [input], [input] in the middle, taken from [divisions] colors spaced evenly in
     * temperature around the hue circle.
     */
    fun analogous(
        count: Int = 5,
        divisions: Int = 12,
    ): List<Hct> {
        val allColors = evenlyInTemperature(divisions)
        val answers = ArrayDeque<Hct>().apply { add(input) }
        val increaseHueCount = (count - 1) / 2
        for (i in 1..increaseHueCount) answers.addFirst(allColors[Math.floorMod(-i, allColors.size)])
        val decreaseHueCount = count - increaseHueCount - 1
        for (i in 1..decreaseHueCount) answers.addLast(allColors[i % allColors.size])
        return answers.toList()
    }

    /** [divisions] colors from [input]'s hue around the circle, as far apart in temperature as each other. */
    private fun evenlyInTemperature(divisions: Int): List<Hct> {
        val startHue = input.hue.roundToInt()
        val startHct = hctsByHue[startHue]
        val tempStep = totalTemperatureChange(startHue, startHct) / divisions
        val allColors = mutableListOf(startHct)
        var totalTempDelta = 0.0
        var lastTemp = relativeTemperature(startHct)
        var hueAddend = 1
        while (allColors.size < divisions) {
            val hct = hctsByHue[sanitizeDegreesInt(startHue + hueAddend)]
            val temp = relativeTemperature(hct)
            totalTempDelta += abs(temp - lastTemp)
            var indexSatisfied = totalTempDelta >= allColors.size * tempStep
            var indexAddend = 1
            while (indexSatisfied && allColors.size < divisions) {
                allColors += hct
                indexSatisfied = totalTempDelta >= (allColors.size + indexAddend) * tempStep
                indexAddend++
            }
            lastTemp = temp
            hueAddend++
            if (hueAddend > 360) {
                while (allColors.size < divisions) allColors += hct
            }
        }
        return allColors
    }

    /** How much the relative temperature changes, in all, going once around the circle from [startHue]. */
    private fun totalTemperatureChange(
        startHue: Int,
        startHct: Hct,
    ): Double {
        var lastTemp = relativeTemperature(startHct)
        var absoluteTotalTempDelta = 0.0
        for (i in 0 until 360) {
            val temp = relativeTemperature(hctsByHue[sanitizeDegreesInt(startHue + i)])
            absoluteTotalTempDelta += abs(temp - lastTemp)
            lastTemp = temp
        }
        return absoluteTotalTempDelta
    }

    /** [hct]'s temperature between the coldest (0) and the warmest (1) of these colors; 0.5 when they are all equal. */
    private fun relativeTemperature(hct: Hct): Double {
        val coldestTemp = tempsByHct.getValue(coldest)
        val range = tempsByHct.getValue(warmest) - coldestTemp
        val differenceFromColdest = tempsByHct.getValue(hct) - coldestTemp
        return if (range == 0.0) 0.5 else differenceFromColdest / range
    }

    private companion object {
        /** Ou, Woodcock and Wright's warmth of a color, from its L*a*b* hue and chroma: about -0.5 to 1.5. */
        fun rawTemperature(color: Hct): Double {
            val lab = labFromArgb(color.toInt())
            val hue = sanitizeDegreesDouble(atan2(lab[2], lab[1]) * 180.0 / PI)
            val chroma = sqrt((lab[1] * lab[1]) + (lab[2] * lab[2]))
            return -0.5 + 0.02 * chroma.pow(1.07) * cos(sanitizeDegreesDouble(hue - 50.0) * PI / 180.0)
        }
    }
}
