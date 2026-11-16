package dev.kyralabs.pingogram.core.ui.file

import android.content.ClipData
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
 * How a dragged-out file's shadow looks, in pixels and ARGB (`docs/DESIGN_SYSTEM.md` §6 "Drag and drop", the owner's
 * board 35c): the tilted card, on `primaryContainer` with `large` corners, the label in `labelLarge` (Google Sans Flex)
 * and `onPrimaryContainer`, at most a bubble's media wide.
 */
@Immutable
data class DragShadowStyle(
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
fun rememberDragShadowStyle(): DragShadowStyle {
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
 * The shadow of files dragged out of the app: a card tilted by [TILT_DEGREES] with [label] on it, ellipsized at the
 * style's width, the finger at its middle. Drawn once by Android into the drag's surface.
 */
internal class FileDragShadow(
    label: String,
    private val look: DragShadowStyle,
) : View.DragShadowBuilder() {
    private val paint =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = look.content
            textSize = look.textSize
            typeface = look.typeface
        }
    private val background = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = look.container }
    private val text = TextUtils.ellipsize(label, paint, look.maxTextWidth, TextUtils.TruncateAt.END).toString()
    private val cardWidth = paint.measureText(text) + look.padding * 2
    private val cardHeight = paint.fontMetrics.run { descent - ascent } + look.padding * 2

    // Room around the card for its tilt.
    private val width = ceil(cardWidth + look.padding * 2).toInt()
    private val height = ceil(cardHeight + look.padding * 2).toInt()

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
        canvas.drawRoundRect(
            RectF(left, top, left + cardWidth, top + cardHeight),
            look.corner,
            look.corner,
            background,
        )
        canvas.drawText(text, left + look.padding, top + look.padding - paint.fontMetrics.ascent, paint)
    }
}

/** The board's tilt of the dragged copy. */
private const val TILT_DEGREES = -4f

/** A TDLib file at [path] to drag into another app, as [mimeType]. */
@Immutable
data class DraggedFile(
    val path: String,
    val mimeType: String,
) {
    override fun toString() = "DraggedFile(mimeType=$mimeType)"
}

/**
 * Starts Android's drag between apps with [files] as `content://` URIs of the app's `FileProvider` ([tdlibFileUri]) and
 * a read grant for them alone ([View.DRAG_FLAG_GLOBAL_URI_READ]), its shadow a [FileDragShadow] of [label] in
 * [style]. False where no file could be served or Android refused the drag.
 */
fun View.startFileDrag(
    files: List<DraggedFile>,
    label: String,
    style: DragShadowStyle,
): Boolean {
    val uris = files.mapNotNull { file -> context.tdlibFileUri(file.path)?.let { it to file.mimeType } }
    if (uris.isEmpty()) return false
    val types = uris.map { it.second }.distinct().toTypedArray()
    val clip = ClipData(label, types, ClipData.Item(uris.first().first))
    uris.drop(1).forEach { (uri, _) -> clip.addItem(ClipData.Item(uri)) }
    return startDragAndDrop(clip, FileDragShadow(label, style), null, DRAG_FLAGS)
}

private const val DRAG_FLAGS = View.DRAG_FLAG_GLOBAL or View.DRAG_FLAG_GLOBAL_URI_READ
