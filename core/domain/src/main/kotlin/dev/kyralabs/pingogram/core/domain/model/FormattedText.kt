package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * A text with formatting, links and mentions: a message, a caption, a quote, a poll question (`REQUIREMENTS.md`
 * FR-C3). [entities] mark parts of [text]; they can be nested (bold inside a link) but never partly overlap.
 *
 * `toString()` leaves the text out, so models holding one can be logged (`REQUIREMENTS.md` §6.2).
 */
data class FormattedText(
    val text: String,
    val entities: ImmutableList<TextEntity>,
) {
    override fun toString() = "FormattedText(length=${text.length}, entities=${entities.size})"

    companion object {
        val Empty = FormattedText("", persistentListOf())
    }
}

/**
 * A part of a [FormattedText] that is shown differently: [length] characters from [offset]. Both count UTF-16 code
 * units, as TDLib sends them, which is how a Kotlin `String` is indexed: `text.substring(offset, offset + length)` is
 * the part, also when emoji (two code units each) come before it.
 */
data class TextEntity(
    val offset: Int,
    val length: Int,
    val type: TextEntityType,
)

/** How a [TextEntity] is shown or what it links to (TDLib's `TextEntityType`). */
sealed interface TextEntityType {
    data object Bold : TextEntityType

    data object Italic : TextEntityType

    data object Underline : TextEntityType

    data object Strikethrough : TextEntityType

    /** Hidden until tapped. */
    data object Spoiler : TextEntityType

    /** Inline monospace. */
    data object Code : TextEntityType

    /** A monospace block. */
    data object Pre : TextEntityType

    /** A monospace block of code; [language] is as the sender gave it, possibly empty. */
    data class PreCode(
        val language: String,
    ) : TextEntityType

    data object BlockQuote : TextEntityType

    /** A block quote collapsed to three lines until expanded. */
    data object ExpandableBlockQuote : TextEntityType

    /** An HTTP URL written out in the text. */
    data object Url : TextEntityType

    /** Text that opens [url], an HTTP or `tg://` link, when tapped. */
    data class TextUrl(
        val url: String,
    ) : TextEntityType

    /** `@username` of a user, a supergroup or a channel. */
    data object Mention : TextEntityType

    /** A mention of a user without a username, by name. */
    data class MentionName(
        val userId: Long,
    ) : TextEntityType

    /** `#hashtag`, optionally followed by `@chatusername`. */
    data object Hashtag : TextEntityType

    /** `$USD`, optionally followed by `@chatusername`. */
    data object Cashtag : TextEntityType

    /** `/command`, optionally followed by `@botusername`. */
    data object BotCommand : TextEntityType

    data object EmailAddress : TextEntityType

    data object PhoneNumber : TextEntityType

    data object BankCardNumber : TextEntityType

    /** An address in the TON blockchain. */
    data object TonAddress : TextEntityType

    /** A custom emoji drawn over the text, which is a regular emoji to fall back on. */
    data class CustomEmoji(
        val customEmojiId: Long,
    ) : TextEntityType

    /** A time in the media of this message or of the replied message: playing starts at [seconds]. */
    data class MediaTimestamp(
        val seconds: Int,
    ) : TextEntityType

    /**
     * A point in time, [unixTime] in seconds since the epoch, shown in the reader's time zone as [format] says; with
     * no [format] the text stays as written.
     */
    data class DateTime(
        val unixTime: Long,
        val format: DateTimeFormat?,
    ) : TextEntityType

    /** A type added by a newer TDLib; the text is shown plain. */
    data object Unknown : TextEntityType
}

/** How a [TextEntityType.DateTime] is shown. */
sealed interface DateTimeFormat {
    /** Relative to now: "in 5 minutes", "2 days ago". */
    data object Relative : DateTimeFormat

    data class Absolute(
        val time: DateTimePrecision,
        val date: DateTimePrecision,
        val showDayOfWeek: Boolean,
    ) : DateTimeFormat
}

/** How much of the date or the time an absolute [DateTimeFormat] shows. */
enum class DateTimePrecision {
    /** Not shown. */
    None,

    /** `17.03.22`, `22:45`. */
    Short,

    /** `March 17, 2022`, `22:45:00`. */
    Long,
}
