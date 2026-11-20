package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A fill's colors over [size]: one solid; two from top to bottom, turned clockwise by its rotation; three or four
 * diagonally from the top start, where other apps blend them freely.
 */
internal fun fillBrush(
    fill: Wallpaper.Fill,
    size: Size,
): Brush {
    val colors = fill.colors.map(::opaque)
    return when {
        colors.size == 1 -> SolidColor(colors.single())
        colors.size > 2 -> Brush.linearGradient(colors, Offset.Zero, Offset(size.width, size.height))
        else -> rotatedGradient(colors, fill.rotationDegrees, size)
    }
}

/** [colors] from top to bottom of [size], turned clockwise by [degrees]. */
private fun rotatedGradient(
    colors: List<Color>,
    degrees: Int,
    size: Size,
): Brush {
    val radians = Math.toRadians(degrees.toDouble())
    // Down, turned clockwise on the screen (y grows downwards).
    val direction = Offset(-sin(radians).toFloat(), cos(radians).toFloat())
    val half = (abs(direction.x) * size.width + abs(direction.y) * size.height) / 2
    val center = Offset(size.width / 2, size.height / 2)
    return Brush.linearGradient(colors, center - direction * half, center + direction * half)
}

/** A 24-bit RGB color, opaque. */
private fun opaque(rgb: Int): Color =
    Color(red = rgb shr RED_SHIFT and CHANNEL, green = rgb shr GREEN_SHIFT and CHANNEL, blue = rgb and CHANNEL)

private const val CHANNEL = 0xFF
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
