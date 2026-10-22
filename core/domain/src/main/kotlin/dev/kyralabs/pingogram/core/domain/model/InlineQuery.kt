package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

/**
 * A bot that answers inline queries (td_api.tl `userTypeBot.is_inline`, `REQUIREMENTS.md` FR-C5): typed as "@[username]
 * query" at the start of the composer, it offers results to send into any chat.
 *
 * @property userId the bot's user id, which the queries go to.
 * @property chatId the bot's private chat, which its results' "Start" button opens.
 * @property username the username the user typed, as Telegram spells it.
 * @property placeholder what the bot says to type (`inline_query_placeholder`, "Search GIFs…"); empty without.
 * @property needsLocation whether the bot asks for the user's location with each query (`need_location`); it is sent
 *   only after the user agreed.
 */
data class InlineBot(
    val userId: Long,
    val chatId: Long,
    val username: String,
    val placeholder: String,
    val needsLocation: Boolean,
)

/**
 * A page of a bot's answer to an inline query (td_api.tl `inlineQueryResults`): [results] to send by their id with
 * [queryId], the bot's [button] above them (null without), and [nextOffset] for the next page, empty after the last.
 */
data class InlineQueryResults(
    val queryId: Long,
    val button: InlineResultsButton?,
    val results: ImmutableList<InlineResult>,
    val nextOffset: String,
)

/**
 * The button a bot shows above its inline results (td_api.tl `inlineQueryResultsButton`), named [text] by the bot:
 * [InlineResultsButtonType.StartBot] opens its chat to start it, [InlineResultsButtonType.WebApp] a Mini App.
 */
data class InlineResultsButton(
    val text: String,
    val type: InlineResultsButtonType,
) {
    override fun toString() = "InlineResultsButton(type=$type, …)"
}

/** What an [InlineResultsButton] does. */
sealed interface InlineResultsButtonType {
    /** Opens the bot's private chat with "Start", which sends [parameter] to the bot once the user taps it. */
    data class StartBot(
        val parameter: String,
    ) : InlineResultsButtonType {
        override fun toString() = "StartBot(…)"
    }

    /** Opens a Mini App (Web App), which Pingogram does not open yet. */
    data object WebApp : InlineResultsButtonType
}

/**
 * One result of an inline query (td_api.tl `InlineQueryResult`), sent by its [id]: what the bot offers, as Telegram
 * describes it. `toString()` leaves out what the bot wrote.
 */
sealed interface InlineResult {
    val id: String

    /** A photo, with an optional [title] and [description]. */
    data class Photo(
        override val id: String,
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo,
        val title: String,
        val description: String,
    ) : InlineResult {
        override fun toString() = "InlineResult.Photo(id=$id, …)"
    }

    /** A GIF-style animation, with an optional [title]. */
    data class Animation(
        override val id: String,
        val animation: dev.kyralabs.pingogram.core.domain.model.Animation,
        val title: String,
    ) : InlineResult {
        override fun toString() = "InlineResult.Animation(id=$id, …)"
    }

    /** A sticker. */
    data class Sticker(
        override val id: String,
        val sticker: dev.kyralabs.pingogram.core.domain.model.Sticker,
    ) : InlineResult

    /** A video, with its [title] and [description]. */
    data class Video(
        override val id: String,
        val video: dev.kyralabs.pingogram.core.domain.model.Video,
        val title: String,
        val description: String,
    ) : InlineResult {
        override fun toString() = "InlineResult.Video(id=$id, …)"
    }

    /** A link to an article ([url], empty without), with its [title], [description] and [thumbnail]. */
    data class Article(
        override val id: String,
        val url: String,
        val title: String,
        val description: String,
        val thumbnail: Thumbnail?,
    ) : InlineResult {
        override fun toString() = "InlineResult.Article(id=$id, …)"
    }

    /** A contact, with its [thumbnail]. */
    data class Contact(
        override val id: String,
        val contact: dev.kyralabs.pingogram.core.domain.model.Contact,
        val thumbnail: Thumbnail?,
    ) : InlineResult

    /** A point on a map, named [title], with its [thumbnail]. */
    data class Location(
        override val id: String,
        val location: dev.kyralabs.pingogram.core.domain.model.Location,
        val title: String,
        val thumbnail: Thumbnail?,
    ) : InlineResult {
        override fun toString() = "InlineResult.Location(id=$id, …)"
    }

    /** A place, with its [thumbnail]. */
    data class Venue(
        override val id: String,
        val venue: dev.kyralabs.pingogram.core.domain.model.Venue,
        val thumbnail: Thumbnail?,
    ) : InlineResult

    /** A song or another audio file. */
    data class Audio(
        override val id: String,
        val audio: dev.kyralabs.pingogram.core.domain.model.Audio,
    ) : InlineResult {
        override fun toString() = "InlineResult.Audio(id=$id, …)"
    }

    /** A file, with its [title] and [description]. */
    data class Document(
        override val id: String,
        val document: dev.kyralabs.pingogram.core.domain.model.Document,
        val title: String,
        val description: String,
    ) : InlineResult {
        override fun toString() = "InlineResult.Document(id=$id, …)"
    }

    /** A voice message, named [title]. */
    data class VoiceNote(
        override val id: String,
        val voiceNote: dev.kyralabs.pingogram.core.domain.model.VoiceNote,
        val title: String,
    ) : InlineResult {
        override fun toString() = "InlineResult.VoiceNote(id=$id, …)"
    }

    /** A game, named [title], described by [description], with its [photo]; Pingogram cannot play games yet. */
    data class Game(
        override val id: String,
        val title: String,
        val description: String,
        val photo: dev.kyralabs.pingogram.core.domain.model.Photo?,
    ) : InlineResult {
        override fun toString() = "InlineResult.Game(id=$id, …)"
    }
}
