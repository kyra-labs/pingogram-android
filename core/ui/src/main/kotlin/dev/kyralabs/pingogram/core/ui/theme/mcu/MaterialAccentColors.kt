/*
 * Copyright 2025 Google LLC
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
 * (https://github.com/material-foundation/material-color-utilities): dynamiccolor/color_spec_2021,
 * the primary, secondary, tertiary and error roles.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.abs
import kotlin.math.max

/**
 * The accent roles of the 2021 color spec, as upstream's ColorSpecDelegateImpl2021 defines them for SchemeContent
 * (a fidelity variant, not monochrome): the primary container keeps the source color's tone, the secondary and
 * tertiary containers stay as colorful as their palettes allow, and the text on each container keeps 4.5:1.
 */
internal object MaterialAccentColors {
    /** The secondary container's tone: the tone nearest 90 (light) or 30 (dark) that reaches the palette's chroma. */
    private fun secondaryContainerTone(scheme: ContentScheme): Double =
        findDesiredChromaByTone(
            scheme.secondaryPalette.hue,
            scheme.secondaryPalette.chroma,
            if (scheme.isDark) 30.0 else 90.0,
            byDecreasingTone = !scheme.isDark,
        )

    /**
     * From [tone], the tone that brings [hue] nearest [chroma], moving toward darker ([byDecreasingTone]) or lighter
     * tones until the chroma stops rising or is within 0.4.
     */
    private fun findDesiredChromaByTone(
        hue: Double,
        chroma: Double,
        tone: Double,
        byDecreasingTone: Boolean,
    ): Double {
        var answer = tone
        var closestToChroma = Hct.from(hue, chroma, tone)
        var chromaPeak = closestToChroma.chroma
        var searching = closestToChroma.chroma < chroma
        while (searching && closestToChroma.chroma < chroma) {
            answer += if (byDecreasingTone) -1.0 else 1.0
            val potentialSolution = Hct.from(hue, chroma, answer)
            searching = chromaPeak <= potentialSolution.chroma && abs(potentialSolution.chroma - chroma) >= 0.4
            if (searching && abs(potentialSolution.chroma - chroma) < abs(closestToChroma.chroma - chroma)) {
                closestToChroma = potentialSolution
            }
            chromaPeak = max(chromaPeak, potentialSolution.chroma)
        }
        return answer
    }

    val primary: DynamicColor =
        DynamicColor(
            name = "primary",
            palette = { it.primaryPalette },
            tone = { if (it.isDark) 80.0 else 40.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 7.0),
            toneDeltaPair = { ToneDeltaPair(primaryContainer, primary, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onPrimary: DynamicColor =
        DynamicColor(
            name = "on_primary",
            palette = { it.primaryPalette },
            tone = { if (it.isDark) 20.0 else 100.0 },
            background = { primary },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val primaryContainer: DynamicColor =
        DynamicColor(
            name = "primary_container",
            palette = { it.primaryPalette },
            tone = { it.sourceColorHct.tone },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(primaryContainer, primary, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onPrimaryContainer: DynamicColor =
        DynamicColor(
            name = "on_primary_container",
            palette = { it.primaryPalette },
            tone = { foregroundTone(primaryContainer.tone(it), 4.5) },
            background = { primaryContainer },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )

    val inversePrimary: DynamicColor =
        DynamicColor(
            name = "inverse_primary",
            palette = { it.primaryPalette },
            tone = { if (it.isDark) 40.0 else 80.0 },
            background = { MaterialSurfaceColors.inverseSurface },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 7.0),
        )

    val secondary: DynamicColor =
        DynamicColor(
            name = "secondary",
            palette = { it.secondaryPalette },
            tone = { if (it.isDark) 80.0 else 40.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 7.0),
            toneDeltaPair = { ToneDeltaPair(secondaryContainer, secondary, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onSecondary: DynamicColor =
        DynamicColor(
            name = "on_secondary",
            palette = { it.secondaryPalette },
            tone = { if (it.isDark) 20.0 else 100.0 },
            background = { secondary },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val secondaryContainer: DynamicColor =
        DynamicColor(
            name = "secondary_container",
            palette = { it.secondaryPalette },
            tone = ::secondaryContainerTone,
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(secondaryContainer, secondary, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onSecondaryContainer: DynamicColor =
        DynamicColor(
            name = "on_secondary_container",
            palette = { it.secondaryPalette },
            tone = { foregroundTone(secondaryContainer.tone(it), 4.5) },
            background = { secondaryContainer },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )

    val tertiary: DynamicColor =
        DynamicColor(
            name = "tertiary",
            palette = { it.tertiaryPalette },
            tone = { if (it.isDark) 80.0 else 40.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 7.0),
            toneDeltaPair = { ToneDeltaPair(tertiaryContainer, tertiary, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onTertiary: DynamicColor =
        DynamicColor(
            name = "on_tertiary",
            palette = { it.tertiaryPalette },
            tone = { if (it.isDark) 20.0 else 100.0 },
            background = { tertiary },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val tertiaryContainer: DynamicColor =
        DynamicColor(
            name = "tertiary_container",
            palette = { it.tertiaryPalette },
            tone = { DislikeAnalyzer.fixIfDisliked(it.tertiaryPalette.getHct(it.sourceColorHct.tone)).tone },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(tertiaryContainer, tertiary, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onTertiaryContainer: DynamicColor =
        DynamicColor(
            name = "on_tertiary_container",
            palette = { it.tertiaryPalette },
            tone = { foregroundTone(tertiaryContainer.tone(it), 4.5) },
            background = { tertiaryContainer },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )

    val error: DynamicColor =
        DynamicColor(
            name = "error",
            palette = { it.errorPalette },
            tone = { if (it.isDark) 80.0 else 40.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 7.0),
            toneDeltaPair = { ToneDeltaPair(errorContainer, error, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onError: DynamicColor =
        DynamicColor(
            name = "on_error",
            palette = { it.errorPalette },
            tone = { if (it.isDark) 20.0 else 100.0 },
            background = { error },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val errorContainer: DynamicColor =
        DynamicColor(
            name = "error_container",
            palette = { it.errorPalette },
            tone = { if (it.isDark) 30.0 else 90.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(errorContainer, error, 10.0, TonePolarity.Nearer, stayTogether = false) },
        )

    val onErrorContainer: DynamicColor =
        DynamicColor(
            name = "on_error_container",
            palette = { it.errorPalette },
            tone = { if (it.isDark) 90.0 else 30.0 },
            background = { errorContainer },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )
}
