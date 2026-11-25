package dev.kyralabs.pingogram.core.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import androidx.core.graphics.createBitmap
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.min

/**
 * An avatar's photo cut to its kind of chat's [polygon] (`Cookie9Sided` for a group, …), with [kind] naming it: the
 * centre square of the photo, outside the polygon transparent. Coil runs it once, off the main thread, and keeps the
 * result in its memory cache under the photo's key and [cacheKey]; the avatar then draws it with no clip. Clipping
 * the photo to the polygon's path instead made the GPU rasterise a mask on every draw of a scrolling list
 * (`docs/PERFORMANCE.md` §5). The polygon is placed as Material's `toShape` places it: scaled to the square, centred.
 */
internal class AvatarMask(
    kind: AvatarShape,
    private val polygon: RoundedPolygon,
) : Transformation() {
    override val cacheKey: String = "avatar-mask-${kind.name}"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val side = min(input.width, input.height).coerceAtLeast(1)
        val path = polygon.toPath()
        path.transform(Matrix().apply { setScale(side.toFloat(), side.toFloat()) })
        val bounds = RectF().also { path.computeBounds(it, true) }
        path.offset(side / 2f - bounds.centerX(), side / 2f - bounds.centerY())
        val output = createBitmap(side, side)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        Canvas(output).apply {
            drawPath(path, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            drawBitmap(input, (side - input.width) / 2f, (side - input.height) / 2f, paint)
        }
        return output
    }

    override fun equals(other: Any?): Boolean = other is AvatarMask && other.cacheKey == cacheKey

    override fun hashCode(): Int = cacheKey.hashCode()
}
