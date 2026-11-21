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
 * (https://github.com/material-foundation/material-color-utilities): dynamiccolor/dynamic_color, the
 * 2021 spec's tone calculation. Restructured into smaller functions; the arithmetic and its order are upstream's.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// How a dynamic color's tone is found in the 2021 color spec, as upstream's ColorCalculationDelegateImpl2021.

/** The tone of a light or dark foreground that reaches [ratio] against [bgTone], or comes closest. */
internal fun foregroundTone(
    bgTone: Double,
    ratio: Double,
): Double {
    val lighterTone = Contrast.lighterUnsafe(bgTone, ratio)
    val darkerTone = Contrast.darkerUnsafe(bgTone, ratio)
    val lighterRatio = Contrast.ratioOfTones(lighterTone, bgTone)
    val darkerRatio = Contrast.ratioOfTones(darkerTone, bgTone)
    return if (tonePrefersLightForeground(bgTone)) {
        val negligibleDifference =
            abs(lighterRatio - darkerRatio) < 0.1 && lighterRatio < ratio && darkerRatio < ratio
        if (lighterRatio >= ratio || lighterRatio >= darkerRatio || negligibleDifference) lighterTone else darkerTone
    } else {
        if (darkerRatio >= ratio || darkerRatio >= lighterRatio) darkerTone else lighterTone
    }
}

/** Whether light text reads better than dark on [tone]: below 60 once rounded. */
internal fun tonePrefersLightForeground(tone: Double): Boolean = tone.roundToInt() < 60

/** The tone of a role of a [pair]: both kept the pair's delta apart, each reaching its contrast with the background. */
internal fun DynamicColor.toneInPair(
    scheme: ContentScheme,
    pair: ToneDeltaPair,
): Double {
    val aIsNearer =
        pair.polarity == TonePolarity.Nearer ||
            (pair.polarity == TonePolarity.Lighter && !scheme.isDark) ||
            (pair.polarity == TonePolarity.Darker && scheme.isDark)
    val nearer = if (aIsNearer) pair.roleA else pair.roleB
    val farther = if (aIsNearer) pair.roleB else pair.roleA
    val expansionDir = if (scheme.isDark) 1.0 else -1.0
    val contrasted = contrastedTones(scheme, nearer, farther)
    val expanded = expanded(contrasted[0], contrasted[1], pair.delta, expansionDir)
    val tones = outOfAwkwardZone(expanded[0], expanded[1], pair, expansionDir)
    return if (name == nearer.name) tones[0] else tones[1]
}

/** The nearer and the farther role's tones, each moved to reach its contrast with this role's background. */
private fun DynamicColor.contrastedTones(
    scheme: ContentScheme,
    nearer: DynamicColor,
    farther: DynamicColor,
): DoubleArray {
    var nTone = nearer.tone(scheme)
    var fTone = farther.tone(scheme)
    val bg = background?.invoke(scheme)
    val nContrastCurve = nearer.contrastCurve
    val fContrastCurve = farther.contrastCurve
    if (bg != null && nContrastCurve != null && fContrastCurve != null) {
        val bgTone = bg.getTone(scheme)
        val nContrast = nContrastCurve.get(scheme.contrastLevel)
        val fContrast = fContrastCurve.get(scheme.contrastLevel)
        val decreasingContrast = scheme.contrastLevel < 0
        if (Contrast.ratioOfTones(bgTone, nTone) < nContrast || decreasingContrast) nTone = foregroundTone(bgTone, nContrast)
        if (Contrast.ratioOfTones(bgTone, fTone) < fContrast || decreasingContrast) fTone = foregroundTone(bgTone, fContrast)
    }
    return doubleArrayOf(nTone, fTone)
}

/** The two tones moved apart until they differ by [delta], the farther first. */
private fun expanded(
    nearerTone: Double,
    fartherTone: Double,
    delta: Double,
    expansionDir: Double,
): DoubleArray {
    var nTone = nearerTone
    var fTone = fartherTone
    if ((fTone - nTone) * expansionDir < delta) {
        fTone = (nTone + delta * expansionDir).coerceIn(0.0, 100.0)
        if ((fTone - nTone) * expansionDir < delta) nTone = (fTone - delta * expansionDir).coerceIn(0.0, 100.0)
    }
    return doubleArrayOf(nTone, fTone)
}

/** The two tones moved out of 50–59: the nearer (and the farther with it), or only the farther unless they stay together. */
private fun outOfAwkwardZone(
    nearerTone: Double,
    fartherTone: Double,
    pair: ToneDeltaPair,
    expansionDir: Double,
): DoubleArray {
    val nearerAwkward = nearerTone >= 50 && nearerTone < 60
    val fartherAwkward = fartherTone >= 50 && fartherTone < 60
    return when {
        nearerAwkward || (fartherAwkward && pair.stayTogether) -> {
            val nTone = if (expansionDir > 0) 60.0 else 49.0
            val reach = nTone + pair.delta * expansionDir
            doubleArrayOf(nTone, if (expansionDir > 0) max(fartherTone, reach) else min(fartherTone, reach))
        }
        fartherAwkward -> doubleArrayOf(nearerTone, if (expansionDir > 0) 60.0 else 49.0)
        else -> doubleArrayOf(nearerTone, fartherTone)
    }
}

/** The tone of a role without a pair: its own, moved to reach its contrast with its background(s), if it has one. */
internal fun DynamicColor.toneAgainstBackground(scheme: ContentScheme): Double {
    val bg = background?.invoke(scheme)
    val curve = contrastCurve
    return if (bg == null || curve == null) tone(scheme) else toneOn(scheme, bg, curve.get(scheme.contrastLevel))
}

private fun DynamicColor.toneOn(
    scheme: ContentScheme,
    bg: DynamicColor,
    desiredRatio: Double,
): Double {
    val bgTone = bg.getTone(scheme)
    var answer = tone(scheme)
    if (Contrast.ratioOfTones(bgTone, answer) < desiredRatio || scheme.contrastLevel < 0) {
        answer = foregroundTone(bgTone, desiredRatio)
    }
    if (isBackground && answer >= 50 && answer < 60) {
        answer = if (Contrast.ratioOfTones(49.0, bgTone) >= desiredRatio) 49.0 else 60.0
    }
    val second = secondBackground?.invoke(scheme)
    return if (second == null) answer else toneBetween(scheme, answer, bg, second, desiredRatio)
}

/** [answer], or a tone that reaches [desiredRatio] against both backgrounds, as near as there is one. */
private fun toneBetween(
    scheme: ContentScheme,
    answer: Double,
    bg1: DynamicColor,
    bg2: DynamicColor,
    desiredRatio: Double,
): Double {
    val bgTone1 = bg1.getTone(scheme)
    val bgTone2 = bg2.getTone(scheme)
    val upper = max(bgTone1, bgTone2)
    val lower = min(bgTone1, bgTone2)
    val lightOption = Contrast.lighter(upper, desiredRatio)
    val darkOption = Contrast.darker(lower, desiredRatio)
    val availables = listOf(lightOption, darkOption).filter { it != -1.0 }
    val prefersLight = tonePrefersLightForeground(bgTone1) || tonePrefersLightForeground(bgTone2)
    return when {
        Contrast.ratioOfTones(upper, answer) >= desiredRatio && Contrast.ratioOfTones(lower, answer) >= desiredRatio -> answer
        prefersLight -> if (lightOption < 0) 100.0 else lightOption
        availables.size == 1 -> availables[0]
        else -> if (darkOption < 0) 0.0 else darkOption
    }
}
