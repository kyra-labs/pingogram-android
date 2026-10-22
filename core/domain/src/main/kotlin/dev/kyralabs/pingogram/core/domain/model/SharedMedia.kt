package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * The kinds of message a profile's shared media lists (`REQUIREMENTS.md` FR-P2, `SharedMediaRepository`), each one of
 * TDLib's search filters: photos and videos together (an album mixes them), files, links, voice messages, music and
 * GIFs.
 */
enum class SharedMediaKind {
    PhotosAndVideos,
    Files,
    Links,
    Voice,
    Music,
    Gifs,
}

/**
 * A page of a chat's messages of one [SharedMediaKind], newest first.
 *
 * @property messages the messages of the kind; self-destructing media is never among them.
 * @property nextFromMessageId where the next, older page starts; 0 once there is none.
 * @property totalCount about how many messages of the chat are of the kind, as TDLib counts them; null while it does
 *   not know.
 */
data class SharedMediaPage(
    val messages: ImmutableList<Message>,
    val nextFromMessageId: Long,
    val totalCount: Int?,
)
