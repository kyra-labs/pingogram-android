package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.PaidMediaItem
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** A paid post: its price, its media (previews until paid for) and its caption. */
internal fun TdApi.MessagePaidMedia.toPaidMedia(): MessageContent.PaidMedia =
    MessageContent.PaidMedia(
        starCount = starCount,
        media = media.map { it.toPaidMediaItem() }.toImmutableList(),
        caption = caption.toFormattedText(),
        showCaptionAboveMedia = showCaptionAboveMedia,
    )

/** One media of a paid post; a kind a newer TDLib added is [PaidMediaItem.Unsupported]. */
internal fun TdApi.PaidMedia.toPaidMediaItem(): PaidMediaItem =
    when (this) {
        is TdApi.PaidMediaPreview -> PaidMediaItem.Preview(width, height, duration, minithumbnail?.toMinithumbnail())
        is TdApi.PaidMediaPhoto -> PaidMediaItem.Photo(photo.toPhoto())
        is TdApi.PaidMediaVideo -> PaidMediaItem.Video(video.toVideo(), cover?.toPhoto(), startTimestamp)
        else -> PaidMediaItem.Unsupported
    }
