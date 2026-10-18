package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.Animation
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.Thumbnail
import dev.kyralabs.pingogram.core.domain.model.ThumbnailFormat
import dev.kyralabs.pingogram.core.domain.model.Video
import dev.kyralabs.pingogram.core.domain.model.VideoNote
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

internal fun TdApi.Photo.toPhoto(): Photo =
    Photo(sizes.map { it.toPhotoSize() }.toImmutableList(), minithumbnail?.toMinithumbnail())

/** A chat's photo as a [Photo], for chat photo changes and link previews of users and chats. */
internal fun TdApi.ChatPhoto.toPhoto(): Photo =
    Photo(sizes.map { it.toPhotoSize() }.toImmutableList(), minithumbnail?.toMinithumbnail())

private fun TdApi.PhotoSize.toPhotoSize() = PhotoSize(type, width, height, photo.toFileRef())

/** Null for a format added by a newer TDLib, which could not be decoded. */
internal fun TdApi.Thumbnail.toThumbnail(): Thumbnail? {
    val thumbnailFormat =
        when (format) {
            is TdApi.ThumbnailFormatJpeg -> ThumbnailFormat.Jpeg
            is TdApi.ThumbnailFormatPng -> ThumbnailFormat.Png
            is TdApi.ThumbnailFormatWebp -> ThumbnailFormat.Webp
            is TdApi.ThumbnailFormatGif -> ThumbnailFormat.Gif
            is TdApi.ThumbnailFormatMpeg4 -> ThumbnailFormat.Mpeg4
            is TdApi.ThumbnailFormatWebm -> ThumbnailFormat.Webm
            is TdApi.ThumbnailFormatTgs -> ThumbnailFormat.Tgs
            else -> return null
        }
    return Thumbnail(thumbnailFormat, width, height, file.toFileRef())
}

internal fun TdApi.Video.toVideo() =
    Video(
        duration = duration,
        width = width,
        height = height,
        minithumbnail = minithumbnail?.toMinithumbnail(),
        thumbnail = thumbnail?.toThumbnail(),
        supportsStreaming = supportsStreaming,
        file = video.toFileRef(),
    )

internal fun TdApi.Animation.toAnimation() =
    Animation(
        duration = duration,
        width = width,
        height = height,
        mimeType = mimeType,
        minithumbnail = minithumbnail?.toMinithumbnail(),
        thumbnail = thumbnail?.toThumbnail(),
        file = animation.toFileRef(),
    )

/** Null for a format added by a newer TDLib, which could not be shown. */
internal fun TdApi.Sticker.toSticker(): Sticker? {
    val stickerFormat =
        when (format) {
            is TdApi.StickerFormatWebp -> StickerFormat.Webp
            is TdApi.StickerFormatTgs -> StickerFormat.Tgs
            is TdApi.StickerFormatWebm -> StickerFormat.Webm
            else -> return null
        }
    return Sticker(
        setId = setId,
        width = width,
        height = height,
        emoji = emoji,
        format = stickerFormat,
        thumbnail = thumbnail?.toThumbnail(),
        file = sticker.toFileRef(),
    )
}

internal fun TdApi.VideoNote.toVideoNote() =
    VideoNote(
        duration = duration,
        length = length,
        minithumbnail = minithumbnail?.toMinithumbnail(),
        thumbnail = thumbnail?.toThumbnail(),
        file = video.toFileRef(),
    )
