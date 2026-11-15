package dev.kyralabs.pingogram.feature.chat.ui.drop

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.View
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlin.math.ceil

/**
 * How a dragged-out file's shadow looks, in pixels and ARGB: the board's tilted card, on `primaryContainer` with
 * `large` corners, the label in `labelLarge` (Google Sans Flex) and `onPrimaryContainer`, at most a bubble wide.
 */
@Immutable
internal data class DragShadowStyle(
    val container: Int,
    val content: Int,
    val corner: Float,
    val padding: Float,
    val textSize: Float,
    val maxTextWidth: Float,
    val typeface: Typeface?,
)

/** The [DragShadowStyle] of the current theme. */
@Composable
internal fun rememberDragShadowStyle(): DragShadowStyle {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    val corner = MaterialTheme.shapes.large.topStart
    val padding = MaterialTheme.spacing.medium
    val maxWidth = MaterialTheme.sizes.mediaMaxWidth
    val density = LocalDensity.current
    val fonts = LocalFontFamilyResolver.current
    return remember(colors, style, corner, padding, maxWidth, density, fonts) {
        with(density) {
            DragShadowStyle(
                container = colors.primaryContainer.toArgb(),
                content = colors.onPrimaryContainer.toArgb(),
                corner = corner.toPx(Size(maxWidth.toPx(), maxWidth.toPx()), density),
                padding = padding.toPx(),
                textSize = style.fontSize.toPx(),
                maxTextWidth = maxWidth.toPx(),
                typeface =
                    fonts.resolve(style.fontFamily, style.fontWeight ?: FontWeight.Medium, FontStyle.Normal).value
                        as? Typeface,
            )
        }
    }
}

/**
 * The shadow of a file dragged out of a bubble: a card tilted by [TILT_DEGREES] with [label] on it, ellipsized at the
 * style's width, the finger at its middle. Drawn once by Android into the drag's surface.
 */
internal class FileDragShadow(
    label: String,
    private val style: DragShadowStyle,
) : View.DragShadowBuilder() {
    private val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = style.content
            textSize = style.textSize
            typeface = style.typeface
        }
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.container }
    private val text = TextUtils.ellipsize(label, paint, style.maxTextWidth, TextUtils.TruncateAt.END).toString()
    private val cardWidth = paint.measureText(text) + style.padding * 2
    private val cardHeight = paint.fontMetrics.run { descent - ascent } + style.padding * 2

    // Room around the card for its tilt.
    private val width = ceil(cardWidth + style.padding * 2).toInt()
    private val height = ceil(cardHeight + style.padding * 2).toInt()

    override fun onProvideShadowMetrics(
        outShadowSize: Point,
        outShadowTouchPoint: Point,
    ) {
        outShadowSize.set(width, height)
        outShadowTouchPoint.set(width / 2, height / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        canvas.rotate(TILT_DEGREES, width / 2f, height / 2f)
        val left = (width - cardWidth) / 2
        val top = (height - cardHeight) / 2
        canvas.drawRoundRect(RectF(left, top, left + cardWidth, top + cardHeight), style.corner, style.corner, background)
        canvas.drawText(text, left + style.padding, top + style.padding - paint.fontMetrics.ascent, paint)
    }
}

/** The board's tilt of the dragged copy. */
private const val TILT_DEGREES = -4f
