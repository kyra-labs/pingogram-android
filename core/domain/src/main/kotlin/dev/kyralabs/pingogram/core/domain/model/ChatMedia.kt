package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * Which media of a chat the media viewer pages through (`REQUIREMENTS.md` FR-C10), chosen by the kind of the message it
 * was opened on: albums mix photos and videos, so those go together; GIFs are a kind of their own.
 */
enum class ChatMediaFilter {
    PhotosAndVideos,
    Gifs,
}

/**
 * A page of the media of a chat that match [filter], from `ChatMediaRepository`. A page loaded towards one side tells
 * only about that side: the other side's flag is false.
 */
data class ChatMediaPage(
    /** Newest first, by id. Photo, video and GIF messages only; self-destructing media is never among them. */
    val messages: ImmutableList<Message>,
    val filter: ChatMediaFilter,
    /** No message older than the oldest of [messages] matches [filter]. */
    val reachedOldest: Boolean,
    /** No message newer than the newest of [messages] matches [filter]. */
    val reachedNewest: Boolean,
    /** About how many messages of the chat match [filter], as TDLib counts them; null while it does not know. */
    val totalCount: Int?,
)
