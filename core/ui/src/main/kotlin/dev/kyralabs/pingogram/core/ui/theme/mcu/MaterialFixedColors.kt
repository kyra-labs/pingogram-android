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
 * the fixed roles.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

/**
 * The fixed roles of the 2021 color spec, as upstream's ColorSpecDelegateImpl2021 defines them for SchemeContent: the
 * same tone in light and dark theme (90 and 80), with text on them that reads on both.
 */
internal object MaterialFixedColors {
    val primaryFixed: DynamicColor =
        DynamicColor(
            name = "primary_fixed",
            palette = { it.primaryPalette },
            tone = { 90.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(primaryFixed, primaryFixedDim, 10.0, TonePolarity.Lighter, stayTogether = true) },
        )

    val primaryFixedDim: DynamicColor =
        DynamicColor(
            name = "primary_fixed_dim",
            palette = { it.primaryPalette },
            tone = { 80.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(primaryFixed, primaryFixedDim, 10.0, TonePolarity.Lighter, stayTogether = true) },
        )

    val onPrimaryFixed: DynamicColor =
        DynamicColor(
            name = "on_primary_fixed",
            palette = { it.primaryPalette },
            tone = { 10.0 },
            background = { primaryFixedDim },
            secondBackground = { primaryFixed },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val onPrimaryFixedVariant: DynamicColor =
        DynamicColor(
            name = "on_primary_fixed_variant",
            palette = { it.primaryPalette },
            tone = { 30.0 },
            background = { primaryFixedDim },
            secondBackground = { primaryFixed },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )

    val secondaryFixed: DynamicColor =
        DynamicColor(
            name = "secondary_fixed",
            palette = { it.secondaryPalette },
            tone = { 90.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(secondaryFixed, secondaryFixedDim, 10.0, TonePolarity.Lighter, stayTogether = true) },
        )

    val secondaryFixedDim: DynamicColor =
        DynamicColor(
            name = "secondary_fixed_dim",
            palette = { it.secondaryPalette },
            tone = { 80.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(secondaryFixed, secondaryFixedDim, 10.0, TonePolarity.Lighter, stayTogether = true) },
        )

    val onSecondaryFixed: DynamicColor =
        DynamicColor(
            name = "on_secondary_fixed",
            palette = { it.secondaryPalette },
            tone = { 10.0 },
            background = { secondaryFixedDim },
            secondBackground = { secondaryFixed },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val onSecondaryFixedVariant: DynamicColor =
        DynamicColor(
            name = "on_secondary_fixed_variant",
            palette = { it.secondaryPalette },
            tone = { 30.0 },
            background = { secondaryFixedDim },
            secondBackground = { secondaryFixed },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )

    val tertiaryFixed: DynamicColor =
        DynamicColor(
            name = "tertiary_fixed",
            palette = { it.tertiaryPalette },
            tone = { 90.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(tertiaryFixed, tertiaryFixedDim, 10.0, TonePolarity.Lighter, stayTogether = true) },
        )

    val tertiaryFixedDim: DynamicColor =
        DynamicColor(
            name = "tertiary_fixed_dim",
            palette = { it.tertiaryPalette },
            tone = { 80.0 },
            isBackground = true,
            background = MaterialSurfaceColors::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
            toneDeltaPair = { ToneDeltaPair(tertiaryFixed, tertiaryFixedDim, 10.0, TonePolarity.Lighter, stayTogether = true) },
        )

    val onTertiaryFixed: DynamicColor =
        DynamicColor(
            name = "on_tertiary_fixed",
            palette = { it.tertiaryPalette },
            tone = { 10.0 },
            background = { tertiaryFixedDim },
            secondBackground = { tertiaryFixed },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val onTertiaryFixedVariant: DynamicColor =
        DynamicColor(
            name = "on_tertiary_fixed_variant",
            palette = { it.tertiaryPalette },
            tone = { 30.0 },
            background = { tertiaryFixedDim },
            secondBackground = { tertiaryFixed },
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )
}
