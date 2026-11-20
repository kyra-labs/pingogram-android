package dev.kyralabs.pingogram.core.ui.wallpaper

import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
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
 *   crops it instead of scaling it. The theme-color wallpaper shows until it is loaded, and if it cannot be.
 * - A pattern takes the theme's colors as its fill (the theme-color wallpaper, else primary to tertiary) and shows in
 *   `onSurface` at its intensity; an inverted one shows in `primary` on `surfaceContainerLowest`.
 * - A fill draws its colors; a freeform gradient as a diagonal one through them.
 * - In dark theme a photo or a fill is dimmed by [darkThemeDimming] percent of `scrim`.
 *
 * It draws in its own layer, in the draw phase: scrolling the conversation neither composes nor redraws it, and the
 * loaded image arrives as one redraw.
 */
@Composable
fun ChatWallpaperLayer(
    wallpaper: Wallpaper?,
    darkThemeDimming: Int,
    modifier: Modifier = Modifier,
) {
    WallpaperLayer(wallpaper, darkThemeDimming, wallpaper?.image?.let { TdlibFileImage(it.file.id) }, modifier)
}

/**
 * The photo at [path] on this device, not uploaded yet, as [ChatWallpaperLayer] draws a photo wallpaper: what the
 * wallpaper picker shows before the user applies it. Blurred with [isBlurred], dimmed by [darkThemeDimming] in dark
 * theme.
 */
@Composable
fun PhotoPreviewWallpaperLayer(
    path: String,
    isBlurred: Boolean,
    darkThemeDimming: Int,
    modifier: Modifier = Modifier,
) {
    val photo = remember(isBlurred) { Wallpaper.Photo(PREVIEW_ID, PREVIEW_IMAGE, isBlurred) }
    WallpaperLayer(photo, darkThemeDimming, File(path), modifier)
}

/** [wallpaper] drawn with its image loaded from [data] (a [TdlibFileImage] or a local [File]); null for a fill. */
@Composable
private fun WallpaperLayer(
    wallpaper: Wallpaper?,
    darkThemeDimming: Int,
    data: Any?,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val themeWallpaper = MaterialTheme.chatStyle.wallpaper
    val window = LocalWindowInfo.current.containerSize
    val painter = if (wallpaper != null && data != null) wallpaperPainter(wallpaper, data, window) else null
    val dim = if (isDarkTheme()) darkThemeDimming.coerceIn(0, PERCENT) / PERCENT_F else 0f
    val look =
        remember(wallpaper, colors, themeWallpaper, dim) { WallpaperLook(wallpaper, colors, themeWallpaper, dim) }
    Spacer(
        modifier
            .clipToBounds()
            .graphicsLayer()
            .drawWithCache {
                val fill = look.fill(size)
                onDrawBehind { look.draw(this, fill, painter, window.height.toFloat()) }
            },
    )
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
): Painter {
    val context = LocalPlatformContext.current
    val request = remember(wallpaper, data, window) { wallpaperRequest(context, wallpaper, data, window) }
    return rememberAsyncImagePainter(request)
}

/**
 * Coil's request for [wallpaper]'s image from [data] at [size] (the window's), filling it: a vector pattern through
 * [VectorPatternDecoder], a blurred photo through [WallpaperBlur].
 */
internal fun wallpaperRequest(
    context: PlatformContext,
    wallpaper: Wallpaper,
    data: Any,
    size: IntSize,
): ImageRequest =
    ImageRequest
        .Builder(context)
        .data(data)
        .size(size.width.coerceAtLeast(1), size.height.coerceAtLeast(1))
        .scale(Scale.FILL)
        .apply {
            if (wallpaper.image?.isVector == true) decoderFactory(VectorPatternDecoder.Factory())
            if (wallpaper is Wallpaper.Photo && wallpaper.isBlurred) transformations(WallpaperBlur)
        }.build()

/** How [wallpaper] draws with this theme's [colors], the [themeWallpaper] under it and its [dim] of `scrim`. */
private class WallpaperLook(
    private val wallpaper: Wallpaper?,
    private val colors: ColorScheme,
    private val themeWallpaper: Brush?,
    private val dim: Float,
) {
    /** What the box is filled with before any image, sized to it. */
    fun fill(size: Size): Brush? =
        when (wallpaper) {
            null, is Wallpaper.Photo -> {
                themeWallpaper
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
        windowHeight: Float,
    ): Unit =
        with(scope) {
            fill?.let { drawRect(it) }
            if (painter != null) drawImage(painter, windowHeight)
            if (dim > 0f && (wallpaper is Wallpaper.Photo || wallpaper is Wallpaper.Fill)) {
                drawRect(colors.scrim, alpha = dim)
            }
        }

    /** A photo as it is; a pattern tinted at its intensity, `onSurface` on the fill or `primary` when inverted. */
    private fun DrawScope.drawImage(
        painter: Painter,
        windowHeight: Float,
    ) {
        val filter =
            (wallpaper as? Wallpaper.Pattern)?.let { pattern ->
                val ink = if (pattern.isInverted) colors.primary else colors.onSurface
                ColorFilter.tint(ink.copy(alpha = pattern.intensity.coerceIn(0, PERCENT) / PERCENT_F))
            }
        drawCover(painter, windowHeight, filter)
    }
}

/** [painter] covering this box's width and the [windowHeight] from the top, centered across; nothing until loaded. */
private fun DrawScope.drawCover(
    painter: Painter,
    windowHeight: Float,
    colorFilter: ColorFilter?,
) {
    val intrinsic = painter.intrinsicSize
    if (intrinsic.isUnspecified || intrinsic.width <= 0f || intrinsic.height <= 0f) return
    val height = max(size.height, windowHeight)
    val scale = max(size.width / intrinsic.width, height / intrinsic.height)
    val drawn = Size(intrinsic.width * scale, intrinsic.height * scale)
    translate((size.width - drawn.width) / 2, (height - drawn.height) / 2) {
        with(painter) { draw(drawn, colorFilter = colorFilter) }
    }
}

/** The photo a [PhotoPreviewWallpaperLayer] stands for: not on Telegram yet, so it has no id or file there. */
private const val PREVIEW_ID = 0L
private val PREVIEW_IMAGE = WallpaperImage(FileRef(0, 0))
private const val PERCENT = 100
private const val PERCENT_F = 100f
