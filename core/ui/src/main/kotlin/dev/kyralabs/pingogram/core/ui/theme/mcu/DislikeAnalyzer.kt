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
 * (https://github.com/material-foundation/material-color-utilities): dislike/dislike_analyzer.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

import kotlin.math.roundToInt

/**
 * Finds and fixes colors that people commonly dislike, as upstream's DislikeAnalyzer: dark yellow-greens, which
 * evoke biological waste. Such a color is lightened to tone 70.
 */
internal object DislikeAnalyzer {
    /** Whether [hct] is a dark yellow-green: hue 90–111, chroma over 16, tone under 65 (each rounded). */
    fun isDisliked(hct: Hct): Boolean {
        val huePasses = hct.hue.roundToInt() in 90..111
        val chromaPasses = hct.chroma.roundToInt() > 16
        val tonePasses = hct.tone.roundToInt() < 65
        return huePasses && chromaPasses && tonePasses
    }

    /** [hct] at tone 70 if it is disliked, else [hct]. */
    fun fixIfDisliked(hct: Hct): Hct = if (isDisliked(hct)) Hct.from(hct.hue, hct.chroma, 70.0) else hct
}
