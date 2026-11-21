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
 * (https://github.com/material-foundation/material-color-utilities): quantize/quantizer_map.
 */

package dev.kyralabs.pingogram.core.ui.theme.mcu

/** Counts the opaque colors of an image, as upstream's QuantizerMap: each color once, in the order first seen. */
internal object QuantizerMap {
    /** How many times each opaque color appears in [pixels] (ARGB); translucent pixels are left out. */
    fun quantize(pixels: IntArray): Map<Int, Int> {
        val countByColor = LinkedHashMap<Int, Int>()
        for (pixel in pixels) {
            if (alphaFromArgb(pixel) >= 255) countByColor[pixel] = (countByColor[pixel] ?: 0) + 1
        }
        return countByColor
    }
}
