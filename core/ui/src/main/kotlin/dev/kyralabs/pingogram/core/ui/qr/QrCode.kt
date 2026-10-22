package dev.kyralabs.pingogram.core.ui.qr

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * The QR code of [text] (a link), drawn on a canvas: dark modules on a light quiet zone of [QUIET_ZONE_MODULES], in
 * color scheme roles that keep it dark on light in every theme ([qrColors]), as scanners expect. It is encoded once per
 * text and its path built once per size, so it stays sharp at any size; drawing reads the colors only. One renderer for
 * every QR code Pingogram shows: the login's, a group's or channel's link, the user's own.
 */
@Composable
fun QrCode(
    text: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(text) { encodeQrCode(text) }
    val (moduleColor, backgroundColor) = qrColors(MaterialTheme.colorScheme)
    Box(
        modifier =
            modifier
                .drawWithCache {
                    val modules = matrix?.let { modulesPath(it, size) }
                    onDrawBehind {
                        drawRect(backgroundColor)
                        if (modules != null) drawPath(modules, moduleColor)
                    }
                }.semantics {
                    this.contentDescription = contentDescription
                    role = Role.Image
                },
    )
}

/**
 * The module and quiet zone colors: `onSurface` on `surfaceContainerLowest` in a light theme, and the inverse roles
 * (`inverseOnSurface` on `inverseSurface`) in a dark one, so modules stay dark on a light ground with a contrast well
 * above what scanners need, whatever the theme, contrast level or dynamic palette (a scanner reads light-on-dark
 * codes poorly).
 */
fun qrColors(colors: ColorScheme): Pair<Color, Color> =
    if (colors.surfaceContainerLowest.luminance() > colors.onSurface.luminance()) {
        colors.onSurface to colors.surfaceContainerLowest
    } else {
        colors.inverseOnSurface to colors.inverseSurface
    }

/** The dark modules as one path, a rectangle per horizontal run, scaled to [size] with the quiet zone around them. */
private fun modulesPath(
    matrix: QrMatrix,
    size: Size,
): Path {
    val module = minOf(size.width, size.height) / (matrix.size + 2 * QUIET_ZONE_MODULES)
    val origin =
        Offset(
            x = (size.width - module * matrix.size) / 2,
            y = (size.height - module * matrix.size) / 2,
        )
    val path = Path()
    for (y in 0 until matrix.size) {
        var x = 0
        while (x < matrix.size) {
            if (!matrix.isDark(x, y)) {
                x++
                continue
            }
            val start = x
            while (x < matrix.size && matrix.isDark(x, y)) x++
            path.addRect(
                Rect(
                    left = origin.x + start * module,
                    top = origin.y + y * module,
                    right = origin.x + x * module,
                    bottom = origin.y + (y + 1) * module,
                ),
            )
        }
    }
    return path
}

/** The quiet zone around the modules, in modules, as the QR code standard requires. */
internal const val QUIET_ZONE_MODULES = 4
