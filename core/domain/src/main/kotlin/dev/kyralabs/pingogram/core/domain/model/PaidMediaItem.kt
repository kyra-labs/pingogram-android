package dev.kyralabs.pingogram.core.domain.model

/**
 * One photo or video of a paid post ([MessageContent.PaidMedia], td_api.tl `PaidMedia`): a [Preview] of its size
 * while the post is not paid for, the [Photo] or [Video] itself once it is, or [Unsupported] for a kind a newer TDLib
 * added.
 */
sealed interface PaidMediaItem {
    /** The size and blurred minithumbnail of media that is not paid for; [duration] is 0 for a photo. */
    data class Preview(
        val width: Int,
        val height: Int,
        val duration: Int,
        val minithumbnail: Minithumbnail?,
    ) : PaidMediaItem {
        val isVideo: Boolean get() = duration > 0
    }

    data class Photo(
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
    ) : PaidMediaItem

    data class Video(
        val video: dev.kyralabs.pingogram.core.domain.model.Video,
        val cover: dev.kyralabs.pingogram.core.domain.model.Photo?,
        /** Where playback starts, in seconds. */
        val startTimestamp: Int,
    ) : PaidMediaItem

    data object Unsupported : PaidMediaItem
}
