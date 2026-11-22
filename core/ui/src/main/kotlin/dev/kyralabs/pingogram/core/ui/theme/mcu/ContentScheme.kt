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
 * (https://github.com/material-foundation/material-color-utilities): scheme/scheme_content and the
 * part of dynamiccolor/dynamic_scheme that picks its palettes in the 2021 color spec.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.max

/**
 * A scheme that keeps the source color, as upstream's SchemeContent: its palettes are [sourceColorHct]'s hue and
 * chroma, its tertiary an analogous color of the same temperature step, so the colors stay close to an image's.
 * [contrastLevel] is 0 (standard), 0.5 (medium) or 1 (high). With [fidelity] (upstream's SchemeContent) the primary
 * container is the source color itself, adjusted for light or dark ([isDark]); without it the containers take the
 * standard tones, 30 in dark and 90 in light, as upstream's non-fidelity variants do. Its roles' colors come from
 * `MaterialDynamicColors`.
 */
internal class ContentScheme(
    val sourceColorHct: Hct,
    val isDark: Boolean,
    val contrastLevel: Double,
    val fidelity: Boolean = true,
) {
    val primaryPalette: TonalPalette = TonalPalette.fromHueAndChroma(sourceColorHct.hue, sourceColorHct.chroma)

    val secondaryPalette: TonalPalette =
        TonalPalette.fromHueAndChroma(
            sourceColorHct.hue,
            max(sourceColorHct.chroma - 32.0, sourceColorHct.chroma * 0.5),
        )

    val tertiaryPalette: TonalPalette =
        TonalPalette.fromHct(DislikeAnalyzer.fixIfDisliked(TemperatureCache(sourceColorHct).analogous(3, 6)[2]))

    val neutralPalette: TonalPalette = TonalPalette.fromHueAndChroma(sourceColorHct.hue, sourceColorHct.chroma / 8.0)

    val neutralVariantPalette: TonalPalette =
        TonalPalette.fromHueAndChroma(sourceColorHct.hue, (sourceColorHct.chroma / 8.0) + 4.0)

    val errorPalette: TonalPalette = TonalPalette.fromHueAndChroma(25.0, 84.0)
}
