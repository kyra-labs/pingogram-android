package dev.kyralabs.pingogram.core.ui.theme.mcu

/**
 * The quantizer tests' image, built the same way by the script that printed the expected results from
 * material-color-utilities 0.4.0: 64 × 64 pixels of crossing gradients, every 17th one half transparent.
 */
internal fun quantizerTestImage(): IntArray =
    IntArray(64 * 64) { i ->
        val x = i % 64
        val y = i / 64
        val r = (x * 37 + y * 11) % 256
        val g = (x * 7 + y * 53) % 256
        val b = (x * x + y * 3) % 256
        val alpha = if (i % 17 == 0) 0x80 else 0xFF
        (alpha shl 24) or (r shl 16) or (g shl 8) or b
    }
