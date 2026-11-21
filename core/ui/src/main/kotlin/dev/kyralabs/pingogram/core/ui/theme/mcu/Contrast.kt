/*
 * Copyright 2022 Google LLC
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
 * (https://github.com/material-foundation/material-color-utilities): contrast/contrast.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.abs

/**
 * WCAG contrast between tones, as upstream's Contrast: the tones a color needs to reach a contrast ratio with another,
 * lighter or darker. Dynamic colors use it to keep text readable at every contrast level.
 */
internal object Contrast {
    /** The contrast ratio of two tones, 1 to 21. */
    fun ratioOfTones(
        toneA: Double,
        toneB: Double,
    ): Double = ratioOfYs(yFromLstar(toneA.coerceIn(0.0, 100.0)), yFromLstar(toneB.coerceIn(0.0, 100.0)))

    /** A tone of at least [ratio] against [tone] and lighter, or -1 when there is none. */
    fun lighter(
        tone: Double,
        ratio: Double,
    ): Double {
        val darkY = yFromLstar(tone)
        val lightY = ratio * (darkY + 5.0) - 5.0
        return checked(tone, lightY, darkY, ratio, lstarFromY(lightY) + 0.4)
    }

    /** A tone of at least [ratio] against [tone] and darker, or -1 when there is none. */
    fun darker(
        tone: Double,
        ratio: Double,
    ): Double {
        val lightY = yFromLstar(tone)
        val darkY = ((lightY + 5.0) / ratio) - 5.0
        return checked(tone, lightY, darkY, ratio, lstarFromY(darkY) - 0.4)
    }

    /** As [lighter], but white where no lighter tone reaches [ratio]. */
    fun lighterUnsafe(
        tone: Double,
        ratio: Double,
    ): Double = lighter(tone, ratio).let { if (it < 0.0) 100.0 else it }

    /** As [darker], but black where no darker tone reaches [ratio]. */
    fun darkerUnsafe(
        tone: Double,
        ratio: Double,
    ): Double = darker(tone, ratio).let { if (it < 0.0) 0.0 else it }

    private fun ratioOfYs(
        y1: Double,
        y2: Double,
    ): Double {
        val lighter = if (y1 > y2) y1 else y2
        val darker = if (lighter == y2) y1 else y2
        return (lighter + 5.0) / (darker + 5.0)
    }

    /** [returnValue], or -1 when [tone] is out of range, the pair misses [ratio] by more than 0.04, or it is. */
    private fun checked(
        tone: Double,
        lightY: Double,
        darkY: Double,
        ratio: Double,
        returnValue: Double,
    ): Double {
        val realContrast = ratioOfYs(lightY, darkY)
        val missed = realContrast < ratio && abs(realContrast - ratio) > 0.04
        val valid = tone in 0.0..100.0 && !missed && returnValue in 0.0..100.0
        return if (valid) returnValue else -1.0
    }
}
