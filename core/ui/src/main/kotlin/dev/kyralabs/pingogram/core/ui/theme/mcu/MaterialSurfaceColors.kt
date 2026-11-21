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
 * (https://github.com/material-foundation/material-color-utilities): dynamiccolor/color_spec_2021,
 * the neutral and surface roles.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

/**
 * The neutral roles of the 2021 color spec, as upstream's ColorSpecDelegateImpl2021 defines them for a scheme that is
 * neither monochrome nor of another variant than content: backgrounds, surfaces and their containers, the text on
 * them, outlines, the inverse surface, shadow, scrim and the surface tint.
 */
internal object MaterialSurfaceColors {
    /** The surface text is checked against: the brightest in dark theme, the dimmest in light. */
    fun highestSurface(scheme: ContentScheme): DynamicColor = if (scheme.isDark) surfaceBright else surfaceDim

    /** [dark]'s tone in dark theme, [light]'s in light, at the scheme's contrast level. */
    private fun curveFor(
        scheme: ContentScheme,
        dark: ContrastCurve,
        light: ContrastCurve,
    ): Double = (if (scheme.isDark) dark else light).get(scheme.contrastLevel)

    val background: DynamicColor =
        DynamicColor(
            name = "background",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 6.0 else 98.0 },
            isBackground = true,
        )

    val onBackground: DynamicColor =
        DynamicColor(
            name = "on_background",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 90.0 else 10.0 },
            background = { background },
            contrastCurve = ContrastCurve(3.0, 3.0, 4.5, 7.0),
        )

    val surface: DynamicColor =
        DynamicColor(
            name = "surface",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 6.0 else 98.0 },
            isBackground = true,
        )

    val surfaceDim: DynamicColor =
        DynamicColor(
            name = "surface_dim",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 6.0 else ContrastCurve(87.0, 87.0, 80.0, 75.0).get(it.contrastLevel) },
            isBackground = true,
        )

    val surfaceBright: DynamicColor =
        DynamicColor(
            name = "surface_bright",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) ContrastCurve(24.0, 24.0, 29.0, 34.0).get(it.contrastLevel) else 98.0 },
            isBackground = true,
        )

    val surfaceContainerLowest: DynamicColor =
        DynamicColor(
            name = "surface_container_lowest",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) ContrastCurve(4.0, 4.0, 2.0, 0.0).get(it.contrastLevel) else 100.0 },
            isBackground = true,
        )

    val surfaceContainerLow: DynamicColor =
        DynamicColor(
            name = "surface_container_low",
            palette = { it.neutralPalette },
            tone = { curveFor(it, ContrastCurve(10.0, 10.0, 11.0, 12.0), ContrastCurve(96.0, 96.0, 96.0, 95.0)) },
            isBackground = true,
        )

    val surfaceContainer: DynamicColor =
        DynamicColor(
            name = "surface_container",
            palette = { it.neutralPalette },
            tone = { curveFor(it, ContrastCurve(12.0, 12.0, 16.0, 20.0), ContrastCurve(94.0, 94.0, 92.0, 90.0)) },
            isBackground = true,
        )

    val surfaceContainerHigh: DynamicColor =
        DynamicColor(
            name = "surface_container_high",
            palette = { it.neutralPalette },
            tone = { curveFor(it, ContrastCurve(17.0, 17.0, 21.0, 25.0), ContrastCurve(92.0, 92.0, 88.0, 85.0)) },
            isBackground = true,
        )

    val surfaceContainerHighest: DynamicColor =
        DynamicColor(
            name = "surface_container_highest",
            palette = { it.neutralPalette },
            tone = { curveFor(it, ContrastCurve(22.0, 22.0, 26.0, 30.0), ContrastCurve(90.0, 90.0, 84.0, 80.0)) },
            isBackground = true,
        )

    val onSurface: DynamicColor =
        DynamicColor(
            name = "on_surface",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 90.0 else 10.0 },
            background = ::highestSurface,
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val surfaceVariant: DynamicColor =
        DynamicColor(
            name = "surface_variant",
            palette = { it.neutralVariantPalette },
            tone = { if (it.isDark) 30.0 else 90.0 },
            isBackground = true,
        )

    val onSurfaceVariant: DynamicColor =
        DynamicColor(
            name = "on_surface_variant",
            palette = { it.neutralVariantPalette },
            tone = { if (it.isDark) 80.0 else 30.0 },
            background = ::highestSurface,
            contrastCurve = ContrastCurve(3.0, 4.5, 7.0, 11.0),
        )

    val inverseSurface: DynamicColor =
        DynamicColor(
            name = "inverse_surface",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 90.0 else 20.0 },
            isBackground = true,
        )

    val inverseOnSurface: DynamicColor =
        DynamicColor(
            name = "inverse_on_surface",
            palette = { it.neutralPalette },
            tone = { if (it.isDark) 20.0 else 95.0 },
            background = { inverseSurface },
            contrastCurve = ContrastCurve(4.5, 7.0, 11.0, 21.0),
        )

    val outline: DynamicColor =
        DynamicColor(
            name = "outline",
            palette = { it.neutralVariantPalette },
            tone = { if (it.isDark) 60.0 else 50.0 },
            background = ::highestSurface,
            contrastCurve = ContrastCurve(1.5, 3.0, 4.5, 7.0),
        )

    val outlineVariant: DynamicColor =
        DynamicColor(
            name = "outline_variant",
            palette = { it.neutralVariantPalette },
            tone = { if (it.isDark) 30.0 else 80.0 },
            background = ::highestSurface,
            contrastCurve = ContrastCurve(1.0, 1.0, 3.0, 4.5),
        )

    val shadow: DynamicColor =
        DynamicColor(
            name = "shadow",
            palette = { it.neutralPalette },
            tone = { 0.0 },
        )

    val scrim: DynamicColor =
        DynamicColor(
            name = "scrim",
            palette = { it.neutralPalette },
            tone = { 0.0 },
        )

    val surfaceTint: DynamicColor =
        DynamicColor(
            name = "surface_tint",
            palette = { it.primaryPalette },
            tone = { if (it.isDark) 80.0 else 40.0 },
            isBackground = true,
        )
}
