package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Wallpaper
import dev.kyralabs.pingogram.core.domain.model.WallpaperChoice
import dev.kyralabs.pingogram.core.domain.model.WallpaperImage
import org.drinkless.tdlib.TdApi

/** The MIME type of a vector pattern (td_api.tl `backgroundTypePattern`: a gzipped subset of SVG). */
internal const val VECTOR_PATTERN_MIME_TYPE = "application/x-tgwallpattern"

/** This background as a [Wallpaper]; null for a chat theme (channels only) or a photo or pattern without its file. */
internal fun TdApi.Background.toWallpaper(): Wallpaper? =
    when (val type = type) {
        is TdApi.BackgroundTypeWallpaper -> {
            document?.toWallpaperImage()?.let { Wallpaper.Photo(id, it, type.isBlurred) }
        }

        is TdApi.BackgroundTypePattern -> {
            document?.toWallpaperImage()?.let { Wallpaper.Pattern(id, it, type.intensity, type.isInverted) }
        }

        is TdApi.BackgroundTypeFill -> {
            type.fill.toFill(id)
        }

        else -> {
            null
        }
    }

private fun TdApi.Document.toWallpaperImage() =
    WallpaperImage(
        file = document.toFileRef(),
        isVector = mimeType == VECTOR_PATTERN_MIME_TYPE,
        thumbnail = thumbnail?.file?.toFileRef(),
    )

private fun TdApi.BackgroundFill.toFill(id: Long): Wallpaper.Fill? =
    when (this) {
        is TdApi.BackgroundFillSolid -> Wallpaper.Fill(id, listOf(color))
        is TdApi.BackgroundFillGradient -> Wallpaper.Fill(id, listOf(topColor, bottomColor), rotationAngle)
        is TdApi.BackgroundFillFreeformGradient -> Wallpaper.Fill(id, colors.toList())
        else -> null
    }

/**
 * What TDLib takes to set [this] choice: the background (null to create a new fill) and its type. A pattern's fill is
 * its `patternFill`, else white, as TDLib requires one.
 */
internal fun WallpaperChoice.toInput(): Pair<TdApi.InputBackground?, TdApi.BackgroundType> =
    when (this) {
        is WallpaperChoice.NewPhoto -> {
            TdApi.InputBackgroundLocal(TdApi.InputFileLocal(path)) to TdApi.BackgroundTypeWallpaper(isBlurred, false)
        }

        is WallpaperChoice.Existing -> {
            when (val wallpaper = wallpaper) {
                is Wallpaper.Photo -> {
                    TdApi.InputBackgroundRemote(wallpaper.id) to TdApi.BackgroundTypeWallpaper(isBlurred, false)
                }

                is Wallpaper.Pattern -> {
                    TdApi.InputBackgroundRemote(wallpaper.id) to
                        TdApi.BackgroundTypePattern(
                            fillOf(patternFill.ifEmpty { listOf(WHITE) }, 0),
                            wallpaper.intensity,
                            wallpaper.isInverted,
                            false,
                        )
                }

                is Wallpaper.Fill -> {
                    null to
                        TdApi.BackgroundTypeFill(fillOf(wallpaper.colors, wallpaper.rotationDegrees))
                }
            }
        }
    }

/** One color is solid, two a gradient turned by [rotation] degrees, three or four a freeform gradient. */
private fun fillOf(
    colors: List<Int>,
    rotation: Int,
): TdApi.BackgroundFill =
    when (colors.size) {
        1 -> TdApi.BackgroundFillSolid(colors[0])
        2 -> TdApi.BackgroundFillGradient(colors[0], colors[1], rotation)
        else -> TdApi.BackgroundFillFreeformGradient(colors.take(MAX_FREEFORM_COLORS).toIntArray())
    }

private const val WHITE = 0xFFFFFF
private const val MAX_FREEFORM_COLORS = 4
