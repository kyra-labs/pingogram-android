package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.isSpecified

/**
 * [dim] of [scrim], then [wash], drawn over an opaque image, as one color matrix on the image's own draw: each channel
 * c becomes `(c · (1 − d) + s · d) · (1 − w) + wash · w`, where d is the scrim's share and w the wash's alpha, which is
 * what source-over drawing of the two rects over it gives. Null where there is neither.
 */
internal fun dimAndWashMatrix(
    scrim: Color,
    dim: Float,
    wash: Color,
): ColorMatrix? {
    val s = scrim.convert(ColorSpaces.Srgb)
    val w = if (wash.isSpecified) wash.convert(ColorSpaces.Srgb) else Color.Transparent
    val d = (s.alpha * dim).coerceIn(0f, 1f)
    val washShare = w.alpha
    if (d == 0f && washShare == 0f) return null
    val keep = (1 - d) * (1 - washShare)

    fun offset(
        scrimChannel: Float,
        washChannel: Float,
    ) = (scrimChannel * d * (1 - washShare) + washChannel * washShare) * CHANNEL_MAX
    return ColorMatrix(
        floatArrayOf(
            keep,
            0f,
            0f,
            0f,
            offset(s.red, w.red),
            0f,
            keep,
            0f,
            0f,
            offset(s.green, w.green),
            0f,
            0f,
            keep,
            0f,
            offset(s.blue, w.blue),
            0f,
            0f,
            0f,
            1f,
            0f,
        ),
    )
}

/** A color matrix's offsets are in channel units, 0 to 255. */
private const val CHANNEL_MAX = 255f
