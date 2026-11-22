package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import coil3.PlatformContext
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Scale
import dev.kyralabs.pingogram.core.domain.model.ChatWallpaper
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import dev.kyralabs.pingogram.core.ui.image.TdlibFileImage
import dev.kyralabs.pingogram.core.ui.theme.brush
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import java.io.File
import kotlin.math.max

/**
 * The wallpaper behind a conversation (`docs/DESIGN_SYSTEM.md` §6 "Chat appearance"; owner decision 2026-11-20),
 * filling this box: [wallpaper], the account's or the chat's own, or with none the theme-color one chosen in Chat
 * appearance (`MaterialTheme.chatStyle.wallpaper`).
 *
 * - A photo is decoded once at the window's size, off the main thread, and kept in Coil's memory cache (blurred first
 *   when it asks to be). It covers the box's width and the window's height from the top, so the keyboard opening
 *   crops it instead of scaling it. The theme-color wallpaper (else [placeholder]) shows until it is drawn, and if it
 *   cannot be, and nothing under it once it is: it is opaque.
 * - A pattern takes the theme's colors as its fill (the theme-color wallpaper, else primary to tertiary) and shows in
 *   `onSurface` at its intensity; an inverted one shows in `primary` on `surfaceContainerLowest`.
 * - A fill draws its colors; a freeform gradient as a diagonal one through them.
 * - In dark theme a photo or a fill is dimmed by [darkThemeDimming] percent of `scrim`.
 * - [placeholder] is what the box would show without this wallpaper, e.g. a conversation's sheet's `surface`, so the
 *   window's color never shows through it; unspecified, nothing.
 * - In a [frame] it covers the frame's box, and this box shows its part of it ([WallpaperFrame]): a conversation's
 *   sheet below its top bar shows the part of the wallpaper the window has there. [blurred] draws a photo blurred
 *   ([WallpaperBlur]) and a pattern's fill without its motif: what shows behind a conversation's top bar.
 *
 * It draws in its own layer, in the draw phase: scrolling the conversation neither composes nor redraws it, and the
 * loaded image arrives as one redraw.
 */
@Composable
fun ChatWallpaperLayer(
    wallpaper: Wallpaper?,
    darkThemeDimming: Int,
    modifier: Modifier = Modifier,
    frame: WallpaperFrame? = null,
    blurred: Boolean = false,
    placeholder: Color = Color.Unspecified,
) {
    // Blurred, a photo takes the bar's stronger blur and a pattern its fill alone: what shows behind a top bar.
    val motif = !(blurred && wallpaper is Wallpaper.Pattern)
    val data = wallpaper?.image?.takeIf { motif }?.let { TdlibFileImage(it.file.id) }
    WallpaperLayer(wallpaper, darkThemeDimming, data, modifier, frame, placeholder, barBlur = blurred)
}

/**
 * The photo at [path] on this device, not uploaded yet, as [ChatWallpaperLayer] draws a photo wallpaper: what the
 * wallpaper picker shows before the user applies it. Blurred with [isBlurred], dimmed by [darkThemeDimming] in dark
 * theme; [blurred], with the stronger blur a top bar shows ([ChatWallpaperLayer]).
 */
@Composable
fun PhotoPreviewWallpaperLayer(
    path: String,
    isBlurred: Boolean,
    darkThemeDimming: Int,
    modifier: Modifier = Modifier,
    frame: WallpaperFrame? = null,
    blurred: Boolean = false,
) {
    val photo = remember(isBlurred) { Wallpaper.Photo(PREVIEW_ID, PREVIEW_IMAGE, isBlurred) }
    WallpaperLayer(photo, darkThemeDimming, File(path), modifier, frame, barBlur = blurred)
}

/**
 * [wallpaper] drawn with its image loaded from [data] (a [TdlibFileImage] or a local [File]); null for a fill. A photo
 * takes the top bar's stronger blur with [barBlur].
 */
@Composable
private fun WallpaperLayer(
    wallpaper: Wallpaper?,
    darkThemeDimming: Int,
    data: Any?,
    modifier: Modifier,
    frame: WallpaperFrame? = null,
    placeholder: Color = Color.Unspecified,
    barBlur: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val themeWallpaper = MaterialTheme.chatStyle.wallpaper
    val window = LocalWindowInfo.current.containerSize
    val painter = if (wallpaper != null && data != null) wallpaperPainter(wallpaper, data, window, barBlur) else null
    val dim = if (isDarkTheme()) darkThemeDimming.coerceIn(0, PERCENT) / PERCENT_F else 0f
    val look =
        remember(wallpaper, colors, themeWallpaper, dim, placeholder) {
            WallpaperLook(wallpaper, colors, themeWallpaper, dim, placeholder)
        }
    // In a frame, the wallpaper covers the frame and this box shows its part of it: where the box sits in the frame
    // is read after placement and in the draw phase only. Without one, it covers this box's width and the window's
    // height from the box's top.
    val placement = remember { mutableStateOf<FramePlacement?>(null) }
    Spacer(
        modifier
            .then(frame?.let { Modifier.onGloballyPositioned { placement.value = it.placementIn(frame) } } ?: Modifier)
            .clipToBounds()
            .graphicsLayer()
            .drawWithCache {
                val placed = placement.value
                val area = placed?.size ?: size
                val coverHeight = placed?.size?.height ?: window.height.toFloat()
                val fill = look.fill(area)
                val offset = placed?.offset ?: Offset.Zero
                onDrawBehind { translate(-offset.x, -offset.y) { look.draw(this, fill, painter, area, coverHeight) } }
            },
    )
}

/** Where a layer's box sits in its frame ([offset]) and the frame's [size]. */
private data class FramePlacement(
    val offset: Offset,
    val size: Size,
)

/** Where this layer's box sits in [frame], or null while the frame is not placed. */
private fun LayoutCoordinates.placementIn(frame: WallpaperFrame): FramePlacement? {
    val frameBox = frame.coordinates?.takeIf { it.isAttached && isAttached } ?: return null
    return FramePlacement(frameBox.localPositionOf(this, Offset.Zero), frameBox.size.toSize())
}

/** The image a photo or pattern draws; null for a fill. */
private val Wallpaper.image: WallpaperImage?
    get() =
        when (this) {
            is Wallpaper.Photo -> image
            is Wallpaper.Pattern -> image
            is Wallpaper.Fill -> null
        }

@Composable
private fun wallpaperPainter(
    wallpaper: Wallpaper,
    data: Any,
    window: IntSize,
    barBlur: Boolean,
): Painter {
    val context = LocalPlatformContext.current
    val request =
        remember(wallpaper, data, window, barBlur) { wallpaperRequest(context, wallpaper, data, window, barBlur) }
    return rememberAsyncImagePainter(request)
}

/**
 * Coil's request for [wallpaper]'s image from [data] at [size] (the window's), filling it: a vector pattern through
 * [VectorPatternDecoder], a blurred photo through [WallpaperBlur], and with [barBlur] through the top bar's stronger
 * [WallpaperBlur.Bar].
 */
internal fun wallpaperRequest(
    context: PlatformContext,
    wallpaper: Wallpaper,
    data: Any,
    size: IntSize,
    barBlur: Boolean = false,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(data)
        .size(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        .scale(Scale.FILL)
        .apply {
            if (wallpaper.image?.isVector == true) decoderFactory(VectorPatternDecoder.Factory())
            if (wallpaper is Wallpaper.Photo && barBlur) {
                transformations(WallpaperBlur.Bar)
            } else if (wallpaper is Wallpaper.Photo && wallpaper.isBlurred) {
                transformations(WallpaperBlur.Photo)
            }
        }.build()

/**
 * How [wallpaper] draws with this theme's [colors], the [themeWallpaper] (else [placeholder]) under a photo until it is
 * drawn, and its [dim] of `scrim`.
 */
private class WallpaperLook(
    private val wallpaper: Wallpaper?,
    private val colors: ColorScheme,
    private val themeWallpaper: Brush?,
    private val dim: Float,
    private val placeholder: Color,
) {
    /** What the box is filled with before any image, sized to it. */
    fun fill(size: Size): Brush? =
        when (wallpaper) {
            null -> {
                themeWallpaper
            }

            is Wallpaper.Photo -> {
                themeWallpaper ?: placeholder.takeIf { it.isSpecified }?.let(::SolidColor)
            }

            is Wallpaper.Fill -> {
                fillBrush(wallpaper, size)
            }

            is Wallpaper.Pattern -> {
                if (wallpaper.isInverted) {
                    SolidColor(colors.surfaceContainerLowest)
                } else {
                    themeWallpaper ?: ChatWallpaper.PrimaryToTertiary.brush(colors)
                }
            }
        }

    fun draw(
        scope: DrawScope,
        fill: Brush?,
        painter: Painter?,
        area: Size,
        coverHeight: Float,
    ): Unit =
        with(scope) {
            // A photo is opaque and covers the area once drawn: its fill shows only until then.
            val photoShows = wallpaper is Wallpaper.Photo && painter?.hasImage() == true
            if (!photoShows) fill?.let { drawRect(it, size = area) }
            if (painter != null) drawImage(painter, area, coverHeight)
            if (dim > 0f && (wallpaper is Wallpaper.Photo || wallpaper is Wallpaper.Fill)) {
                drawRect(colors.scrim, size = area, alpha = dim)
            }
        }

    /** A photo as it is; a pattern tinted at its intensity, `onSurface` on the fill or `primary` when inverted. */
    private fun DrawScope.drawImage(
        painter: Painter,
        area: Size,
        coverHeight: Float,
    ) {
        val filter =
            (wallpaper as? Wallpaper.Pattern)?.let { pattern ->
                val ink = if (pattern.isInverted) colors.primary else colors.onSurface
                ColorFilter.tint(ink.copy(alpha = pattern.intensity.coerceIn(0, PERCENT) / PERCENT_F))
            }
        drawCover(painter, area, coverHeight, filter)
    }
}

/** [painter] covering the [area]'s width and [coverHeight] from the top, centered across; nothing until loaded. */
private fun DrawScope.drawCover(
    painter: Painter,
    area: Size,
    coverHeight: Float,
    colorFilter: ColorFilter?,
) {
    if (!painter.hasImage()) return
    val intrinsic = painter.intrinsicSize
    val height = max(area.height, coverHeight)
    val scale = max(area.width / intrinsic.width, height / intrinsic.height)
    val drawn = Size(intrinsic.width * scale, intrinsic.height * scale)
    translate((area.width - drawn.width) / 2, (height - drawn.height) / 2) {
        with(painter) { draw(drawn, colorFilter = colorFilter) }
    }
}

/** Whether [this] has an image to draw: Coil's painter has none while loading, nor where it failed. */
private fun Painter.hasImage(): Boolean {
    val intrinsic = intrinsicSize
    return !intrinsic.isUnspecified && intrinsic.width > 0f && intrinsic.height > 0f
}

/** The photo a [PhotoPreviewWallpaperLayer] stands for: not on Telegram yet, so it has no id or file there. */
private const val PREVIEW_ID = 0L
private val PREVIEW_IMAGE = WallpaperImage(FileRef(0, 0))
private const val PERCENT = 100
private const val PERCENT_F = 100f
