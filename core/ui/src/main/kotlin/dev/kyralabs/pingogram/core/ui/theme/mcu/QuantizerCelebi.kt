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
 * (https://github.com/material-foundation/material-color-utilities): quantize/quantizer_celebi.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

/**
 * Celebi's quantizer, as upstream's QuantizerCelebi: Wu's quantizer finds the starting clusters, weighted k-means in
 * L*a*b* refines them. This is how Material finds an image's colors before Score ranks them.
 */
internal object QuantizerCelebi {
    /** Up to [maxColors] colors of [pixels] (ARGB) and how many pixels each stands for. */
    fun quantize(
        pixels: IntArray,
        maxColors: Int,
    ): Map<Int, Int> = QuantizerWsmeans.quantize(pixels, QuantizerWu().quantize(pixels, maxColors), maxColors)
}
