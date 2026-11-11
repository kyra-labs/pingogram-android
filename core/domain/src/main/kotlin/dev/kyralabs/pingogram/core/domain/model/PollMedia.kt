package dev.kyralabs.pingogram.core.domain.model

/**
 * What a poll, or one of its answers, carries beside its text (td_api.tl `PollMedia`): a photo or a video for the poll,
 * a link for an answer. Telegram allows stickers, GIFs, places and files too, which Pingogram shows as [Other].
 */
sealed interface PollMedia {
    data class Photo(
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
    ) : PollMedia

    data class Video(
        val video: dev.kyralabs.pingogram.core.domain.model.Video,
    ) : PollMedia

    /** A link: its [url], and the title of its preview or its site's name ([title]), empty while Telegram has none. */
    data class Link(
        val url: String,
        val title: String,
    ) : PollMedia

    /** A sticker, a GIF, a song, a file or a place, which Pingogram does not show in polls yet. */
    data object Other : PollMedia
}
