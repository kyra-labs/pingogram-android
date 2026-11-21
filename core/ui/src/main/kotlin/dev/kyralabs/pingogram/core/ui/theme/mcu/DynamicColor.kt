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
 * (https://github.com/material-foundation/material-color-utilities): dynamiccolor/dynamic_color,
 * dynamiccolor/contrast_curve and dynamiccolor/tone_delta_pair, for the 2021 color spec.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

/**
 * A color role whose color depends on the scheme, as upstream's DynamicColor: a [palette] and a [tone], moved where
 * needed to keep a contrast ratio ([contrastCurve]) with its [background] (and [secondBackground]), or a tone
 * difference with its partner ([toneDeltaPair]). [isBackground] roles keep out of tones 50–59, where neither black nor
 * white text reads well.
 */
internal class DynamicColor(
    val name: String,
    val palette: (ContentScheme) -> TonalPalette,
    val tone: (ContentScheme) -> Double,
    val isBackground: Boolean = false,
    val background: ((ContentScheme) -> DynamicColor)? = null,
    val secondBackground: ((ContentScheme) -> DynamicColor)? = null,
    val contrastCurve: ContrastCurve? = null,
    val toneDeltaPair: ((ContentScheme) -> ToneDeltaPair)? = null,
) {
    /** This role's color in [scheme], as ARGB. */
    fun getArgb(scheme: ContentScheme): Int = palette(scheme).tone(getTone(scheme))

    /** This role's tone in [scheme], after its contrast and tone-difference constraints. */
    fun getTone(scheme: ContentScheme): Double =
        toneDeltaPair?.invoke(scheme)?.let { toneInPair(scheme, it) } ?: toneAgainstBackground(scheme)
}

/**
 * The contrast ratio a role needs with its background at each contrast level: [low] at -1, [normal] at 0, [medium]
 * at 0.5 and [high] at 1, linear in between, as upstream's ContrastCurve.
 */
internal class ContrastCurve(
    private val low: Double,
    private val normal: Double,
    private val medium: Double,
    private val high: Double,
) {
    /** The ratio at [contrastLevel], -1 to 1. */
    fun get(contrastLevel: Double): Double =
        when {
            contrastLevel <= -1.0 -> low
            contrastLevel < 0.0 -> lerp(low, normal, (contrastLevel - (-1)) / 1)
            contrastLevel < 0.5 -> lerp(normal, medium, (contrastLevel - 0) / 0.5)
            contrastLevel < 1.0 -> lerp(medium, high, (contrastLevel - 0.5) / 0.5)
            else -> high
        }
}

/** Which of two paired roles stays nearer its background, as upstream's TonePolarity. */
internal enum class TonePolarity { Darker, Lighter, Nearer, Farther }

/**
 * Two roles kept at least [delta] tones apart, as upstream's ToneDeltaPair: [roleA] is the nearer one to the
 * background with [TonePolarity.Nearer]; [stayTogether] moves both out of tones 50–59 rather than only the farther.
 */
internal class ToneDeltaPair(
    val roleA: DynamicColor,
    val roleB: DynamicColor,
    val delta: Double,
    val polarity: TonePolarity,
    val stayTogether: Boolean,
)
