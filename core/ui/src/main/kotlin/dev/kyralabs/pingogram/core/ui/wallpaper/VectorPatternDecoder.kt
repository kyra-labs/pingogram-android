package dev.kyralabs.pingogram.core.ui.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import androidx.core.graphics.PathParser
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DecodeResult
import coil3.decode.Decoder
import coil3.decode.ImageSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.pxOrElse
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes a vector wallpaper pattern (`docs/TDLIB.md` "Backgrounds": MIME type `application/x-tgwallpattern`, a
 * gzipped SVG of filled shapes) into a mask at the requested size, covering it as `ContentScale.Crop` would: the
 * shapes opaque, the rest transparent, for the wallpaper to tint with the theme's colors. An `ALPHA_8` bitmap, a
 * quarter of an ARGB one. It reads `path`, `rect`, `circle`, `ellipse`, `polygon` and `polyline`, their `transform`
 * and their groups'; shapes with `fill="none"` and anything else are left out.
 */
internal class VectorPatternDecoder(
    private val source: ImageSource,
    private val options: Options,
) : Decoder {
    override suspend fun decode(): DecodeResult {
        val pattern = source.source().inputStream().use { readPattern(it) }
        val width =
            options.size.width
                .pxOrElse { pattern.width.roundToInt() }
                .coerceAtLeast(1)
        val height =
            options.size.height
                .pxOrElse { pattern.height.roundToInt() }
                .coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(bitmap)
        val scale = max(width / pattern.width, height / pattern.height)
        canvas.translate((width - pattern.width * scale) / 2, (height - pattern.height * scale) / 2)
        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        pattern.shapes.forEach { canvas.drawPath(it, paint) }
        return DecodeResult(bitmap.asImage(), isSampled = false)
    }

    /** Decodes every request it is given: a request passes it (`decoderFactory`) only for vector patterns. */
    class Factory : Decoder.Factory {
        override fun create(
            result: SourceFetchResult,
            options: Options,
            imageLoader: ImageLoader,
        ): Decoder = VectorPatternDecoder(result.source, options)
    }
}

/** A pattern's shapes in its own units, and the size of its view box. */
internal class VectorPattern(
    val width: Float,
    val height: Float,
    val shapes: List<Path>,
)

/** Reads the pattern in [input], gzipped as the files are, or plain. */
internal fun readPattern(input: InputStream): VectorPattern {
    val buffered = BufferedInputStream(input)
    buffered.mark(2)
    val gzipped = buffered.read() == GZIP_FIRST && buffered.read() == GZIP_SECOND
    buffered.reset()
    val svg = if (gzipped) GZIPInputStream(buffered) else buffered
    val parser = Xml.newPullParser().apply { setInput(svg, null) }
    return PatternReader(parser).read()
}

/** Walks the SVG, keeping the transform of every open group. */
private class PatternReader(
    private val parser: XmlPullParser,
) {
    private val transforms = ArrayDeque<Matrix>().apply { add(Matrix()) }
    private val shapes = mutableListOf<Path>()
    private var viewBox = RectF()

    fun read(): VectorPattern {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> start()
                XmlPullParser.END_TAG -> if (parser.name == "g") transforms.removeLast()
            }
        }
        val width = viewBox.width().takeIf { it > 0 } ?: 1f
        val height = viewBox.height().takeIf { it > 0 } ?: 1f
        val origin = Matrix().apply { setTranslate(-viewBox.left, -viewBox.top) }
        shapes.forEach { it.transform(origin) }
        return VectorPattern(width, height, shapes)
    }

    private fun start() {
        // An element's own transform applies first, then its groups': parent × own.
        val own = Matrix(transforms.last()).apply { preConcat(transformOf(attr("transform"))) }
        when (parser.name) {
            "svg" -> viewBox = viewBoxOf()
            "g" -> transforms.addLast(own)
            else -> filledShape()?.let { path -> shapes += path.apply { transform(own) } }
        }
    }

    /** The element's outline if it is a filled shape. */
    private fun filledShape(): Path? = if (attr("fill") == "none") null else shape()

    private fun viewBoxOf(): RectF {
        val box = numbers(attr("viewBox"))
        if (box.size != VIEW_BOX_VALUES) return RectF(0f, 0f, number("width"), number("height"))
        return RectF(box[LEFT], box[TOP], box[LEFT] + box[WIDTH], box[TOP] + box[HEIGHT])
    }

    /** The element's outline; null for one that is not a shape or cannot be read. */
    private fun shape(): Path? =
        when (parser.name) {
            "path" -> runCatching { PathParser.createPathFromPathData(attr("d")) }.getOrNull()
            "rect" -> rect()
            "circle" -> Path().apply { addCircle(number("cx"), number("cy"), number("r"), Path.Direction.CW) }
            "ellipse" -> ellipse()
            "polygon", "polyline" -> polygon(close = parser.name == "polygon")
            else -> null
        }?.apply { if (attr("fill-rule") == "evenodd") fillType = Path.FillType.EVEN_ODD }

    private fun rect(): Path {
        val x = number("x")
        val y = number("y")
        val box = RectF(x, y, x + number("width"), y + number("height"))
        val rx = number("rx")
        val ry = number("ry").takeIf { it > 0 } ?: rx
        return Path().apply { addRoundRect(box, rx, ry, Path.Direction.CW) }
    }

    private fun ellipse(): Path {
        val cx = number("cx")
        val cy = number("cy")
        val rx = number("rx")
        val ry = number("ry")
        return Path().apply { addOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), Path.Direction.CW) }
    }

    private fun polygon(close: Boolean): Path? {
        val points = numbers(attr("points")).chunked(2).filter { it.size == 2 }
        if (points.isEmpty()) return null
        return Path().apply {
            moveTo(points[0][0], points[0][1])
            points.drop(1).forEach { lineTo(it[0], it[1]) }
            if (close) close()
        }
    }

    private fun attr(name: String): String = parser.getAttributeValue(null, name).orEmpty()

    private fun number(name: String): Float = attr(name).removeSuffix("px").toFloatOrNull() ?: 0f
}

/** The matrix of an SVG `transform` list: `matrix`, `translate`, `scale` and `rotate`, applied in order. */
internal fun transformOf(transform: String): Matrix {
    val matrix = Matrix()
    TRANSFORM.findAll(transform).forEach { match ->
        val values = numbers(match.groupValues[2])
        val step = Matrix()
        when (match.groupValues[1]) {
            "matrix" -> {
                if (values.size == MATRIX_VALUES) {
                    // SVG's a b c d e f is the matrix [a c e; b d f], row by row as Android keeps it.
                    step.setValues(MATRIX_ORDER.map { values[it] }.toFloatArray() + floatArrayOf(0f, 0f, 1f))
                }
            }

            "translate" -> {
                step.setTranslate(values.getOrElse(0) { 0f }, values.getOrElse(1) { 0f })
            }

            "scale" -> {
                values.getOrNull(0)?.let { sx -> step.setScale(sx, values.getOrElse(1) { sx }) }
            }

            "rotate" -> {
                values.getOrNull(0)?.let { degrees ->
                    step.setRotate(degrees, values.getOrElse(1) { 0f }, values.getOrElse(2) { 0f })
                }
            }
        }
        // SVG applies the list's first transform last (outermost), as a product left to right.
        matrix.preConcat(step)
    }
    return matrix
}

private fun numbers(text: String): List<Float> = NUMBER.findAll(text).mapNotNull { it.value.toFloatOrNull() }.toList()

private val TRANSFORM = Regex("""(matrix|translate|scale|rotate)\s*\(([^)]*)\)""")
private val NUMBER = Regex("""[-+]?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?""")
private const val GZIP_FIRST = 0x1F
private const val GZIP_SECOND = 0x8B
private const val VIEW_BOX_VALUES = 4

// The places of a view box's values: min-x, min-y, width, height.
private const val LEFT = 0
private const val TOP = 1
private const val WIDTH = 2
private const val HEIGHT = 3
private const val MATRIX_VALUES = 6

/** Where Android's first six matrix values are in SVG's `matrix(a b c d e f)`: a c e, b d f. */
private val MATRIX_ORDER = intArrayOf(0, 2, 4, 1, 3, 5)
