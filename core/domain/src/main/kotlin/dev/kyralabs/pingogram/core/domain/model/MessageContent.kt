package dev.kyralabs.pingogram.core.domain.model

/**
 * What a message shows (`REQUIREMENTS.md` FR-C2–C4). Captions are empty when there are none. Content Pingogram does
 * not show yet is [Unsupported], so the message keeps its place in the history.
 *
 * Media types are written with their package where a case has the same name (`MessageContent.Photo` holds a
 * [dev.kyralabs.pingogram.core.domain.model.Photo]).
 */
sealed interface MessageContent {
    data class Text(
        val text: FormattedText,
        val linkPreview: LinkPreview?,
    ) : MessageContent

    /**
     * [hasSpoiler]: covered until tapped. [isSecret]: blurred, and shown only while held (self-destructing media).
     * [showCaptionAboveMedia]: the caption goes above instead of below.
     */
    data class Photo(
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
        val caption: FormattedText,
        val hasSpoiler: Boolean,
        val isSecret: Boolean,
        val showCaptionAboveMedia: Boolean,
    ) : MessageContent

    /** Flags as for [Photo]. */
    data class Video(
        val video: dev.kyralabs.pingogram.core.domain.model.Video,
        val caption: FormattedText,
        val hasSpoiler: Boolean,
        val isSecret: Boolean,
        val showCaptionAboveMedia: Boolean,
    ) : MessageContent

    /** A GIF; flags as for [Photo]. */
    data class Animation(
        val animation: dev.kyralabs.pingogram.core.domain.model.Animation,
        val caption: FormattedText,
        val hasSpoiler: Boolean,
        val isSecret: Boolean,
        val showCaptionAboveMedia: Boolean,
    ) : MessageContent

    data class Sticker(
        val sticker: dev.kyralabs.pingogram.core.domain.model.Sticker,
    ) : MessageContent

    /** [isListened]: a recipient has played it. */
    data class VoiceNote(
        val voiceNote: dev.kyralabs.pingogram.core.domain.model.VoiceNote,
        val caption: FormattedText,
        val isListened: Boolean,
    ) : MessageContent

    /** [isViewed]: a recipient has played it. [isSecret] as for [Photo]. */
    data class VideoNote(
        val videoNote: dev.kyralabs.pingogram.core.domain.model.VideoNote,
        val isViewed: Boolean,
        val isSecret: Boolean,
    ) : MessageContent

    data class Audio(
        val audio: dev.kyralabs.pingogram.core.domain.model.Audio,
        val caption: FormattedText,
    ) : MessageContent

    data class Document(
        val document: dev.kyralabs.pingogram.core.domain.model.Document,
        val caption: FormattedText,
    ) : MessageContent

    /**
     * A location; a live one if [livePeriod] is not 0: it is updated for [livePeriod] seconds from the message date,
     * or forever if it is [LIVE_PERIOD_FOREVER].
     */
    data class Location(
        val location: dev.kyralabs.pingogram.core.domain.model.Location,
        val livePeriod: Int,
    ) : MessageContent {
        companion object {
            const val LIVE_PERIOD_FOREVER = Int.MAX_VALUE
        }
    }

    data class Venue(
        val venue: dev.kyralabs.pingogram.core.domain.model.Venue,
    ) : MessageContent

    data class Contact(
        val contact: dev.kyralabs.pingogram.core.domain.model.Contact,
    ) : MessageContent

    /** [description] may be empty. */
    data class Poll(
        val poll: dev.kyralabs.pingogram.core.domain.model.Poll,
        val description: FormattedText,
    ) : MessageContent

    /** A die thrown by Telegram: [value] is the result for [emoji] (🎲, 🎯, 🏀, …), 0 while it is still rolling. */
    data class Dice(
        val emoji: String,
        val value: Int,
    ) : MessageContent

    /** A message of just one emoji, shown big; animated by [sticker] if there is one. */
    data class AnimatedEmoji(
        val emoji: String,
        val sticker: dev.kyralabs.pingogram.core.domain.model.Sticker?,
    ) : MessageContent

    /**
     * A game a bot sent, played in a web view, which Pingogram does not open yet; only its [title] is kept, for
     * previews. `toString()` leaves it out.
     */
    data class Game(
        val title: String,
    ) : MessageContent {
        override fun toString() = "Game(…)"
    }

    /**
     * An invoice from a bot or a business, which Pingogram does not pay (`REQUIREMENTS.md` §1.2); only its [title] is
     * kept, for previews. `toString()` leaves it out.
     */
    data class Invoice(
        val title: String,
    ) : MessageContent {
        override fun toString() = "Invoice(…)"
    }

    /** A forwarded story. */
    data class Story(
        val storyPosterChatId: Long,
        val storyId: Int,
    ) : MessageContent

    /** Self-destructing media that was opened and is gone. */
    data class Expired(
        val media: ExpiredMedia,
    ) : MessageContent

    /** An event in the chat, shown centred instead of in a bubble. */
    data class Service(
        val action: ServiceAction,
    ) : MessageContent

    /** Content Pingogram cannot show (yet): paid media, giveaways, checklists, …. */
    data object Unsupported : MessageContent
}

/** A link preview under a text message. `toString()` leaves out what it shows. */
data class LinkPreview(
    val url: String,
    /** The URL to show, shortened. */
    val displayUrl: String,
    /** Each of these may be empty. */
    val siteName: String,
    val title: String,
    val description: FormattedText,
    /** The page's photo, a video's cover, or a chat's or user's photo; null if none. */
    val photo: Photo?,
    /** The [photo] is shown large rather than as a small thumbnail. */
    val showLargeMedia: Boolean,
    /** The preview goes above the text instead of below. */
    val showAboveText: Boolean,
) {
    override fun toString() = "LinkPreview(…)"
}

enum class ExpiredMedia {
    Photo,
    Video,
    VideoNote,
    VoiceNote,
}
