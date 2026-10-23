package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType

// The entities of an exported text: their names in the JSON schema (and the temporary records), and the text as a
// tree of nested parts for the HTML page.

/**
 * An entity type as the JSON schema names it, with its one value where it has one: the link of a `text_url`, the
 * user of a `mention_name`, the language of a `pre_code`, the emoji of a `custom_emoji`, the second of a
 * `media_timestamp` and the time of a `date_time`.
 */
internal data class EntityName(
    val name: String,
    val text: String? = null,
    val number: Long? = null,
)

internal fun TextEntityType.toEntityName(): EntityName =
    when (this) {
        TextEntityType.Bold -> EntityName("bold")
        TextEntityType.Italic -> EntityName("italic")
        TextEntityType.Underline -> EntityName("underline")
        TextEntityType.Strikethrough -> EntityName("strikethrough")
        TextEntityType.Spoiler -> EntityName("spoiler")
        TextEntityType.Code -> EntityName("code")
        TextEntityType.Pre -> EntityName("pre")
        is TextEntityType.PreCode -> EntityName("pre_code", text = language)
        TextEntityType.BlockQuote -> EntityName("blockquote")
        TextEntityType.ExpandableBlockQuote -> EntityName("expandable_blockquote")
        TextEntityType.Url -> EntityName("url")
        is TextEntityType.TextUrl -> EntityName("text_url", text = url)
        TextEntityType.Mention -> EntityName("mention")
        is TextEntityType.MentionName -> EntityName("mention_name", number = userId)
        TextEntityType.Hashtag -> EntityName("hashtag")
        TextEntityType.Cashtag -> EntityName("cashtag")
        TextEntityType.BotCommand -> EntityName("bot_command")
        TextEntityType.EmailAddress -> EntityName("email_address")
        TextEntityType.PhoneNumber -> EntityName("phone_number")
        TextEntityType.BankCardNumber -> EntityName("bank_card_number")
        TextEntityType.TonAddress -> EntityName("ton_address")
        is TextEntityType.CustomEmoji -> EntityName("custom_emoji", number = customEmojiId)
        is TextEntityType.MediaTimestamp -> EntityName("media_timestamp", number = seconds.toLong())
        is TextEntityType.DateTime -> EntityName("date_time", number = unixTime)
        TextEntityType.Unknown -> EntityName("unknown")
    }

/** The type [toEntityName] named; a `date_time` comes back without its format, which exports do not keep. */
internal fun EntityName.toEntityType(): TextEntityType =
    when (name) {
        "bold" -> TextEntityType.Bold
        "italic" -> TextEntityType.Italic
        "underline" -> TextEntityType.Underline
        "strikethrough" -> TextEntityType.Strikethrough
        "spoiler" -> TextEntityType.Spoiler
        "code" -> TextEntityType.Code
        "pre" -> TextEntityType.Pre
        "pre_code" -> TextEntityType.PreCode(text.orEmpty())
        "blockquote" -> TextEntityType.BlockQuote
        "expandable_blockquote" -> TextEntityType.ExpandableBlockQuote
        "url" -> TextEntityType.Url
        "text_url" -> TextEntityType.TextUrl(text.orEmpty())
        "mention" -> TextEntityType.Mention
        "mention_name" -> TextEntityType.MentionName(number ?: 0)
        "hashtag" -> TextEntityType.Hashtag
        "cashtag" -> TextEntityType.Cashtag
        "bot_command" -> TextEntityType.BotCommand
        "email_address" -> TextEntityType.EmailAddress
        "phone_number" -> TextEntityType.PhoneNumber
        "bank_card_number" -> TextEntityType.BankCardNumber
        "ton_address" -> TextEntityType.TonAddress
        "custom_emoji" -> TextEntityType.CustomEmoji(number ?: 0)
        "media_timestamp" -> TextEntityType.MediaTimestamp((number ?: 0).toInt())
        "date_time" -> TextEntityType.DateTime(number ?: 0, null)
        else -> TextEntityType.Unknown
    }

/** A part of a text: plain characters, or an [entity] around more parts. */
internal sealed interface TextPart {
    data class Plain(
        val text: String,
    ) : TextPart

    data class Marked(
        val entity: TextEntityType,
        /** The characters the entity covers, as they are, e.g. for a URL written out. */
        val text: String,
        val parts: List<TextPart>,
    ) : TextPart
}

/**
 * [text] as a tree of [TextPart]s. Entities nest but never partly overlap (td_api.tl); one that does anyway, or that
 * reaches past the text or its parent, is cut at that end, so every character shows exactly once.
 */
internal fun FormattedText.toParts(): List<TextPart> {
    val sorted =
        entities
            .filter { it.length > 0 && it.offset >= 0 && it.offset < text.length }
            .sortedWith(compareBy<TextEntity> { it.offset }.thenByDescending { it.length })
    return parts(text, sorted, 0, text.length)
}

/** The parts of [text] from [start] to [end], with [entities] (sorted by start, longest first) inside that range. */
private fun parts(
    text: String,
    entities: List<TextEntity>,
    start: Int,
    end: Int,
): List<TextPart> {
    val result = mutableListOf<TextPart>()
    var position = start
    var index = 0
    while (index < entities.size) {
        val entity = entities[index]
        val from = maxOf(entity.offset, position)
        val to = minOf(entity.offset + entity.length, end)
        // The entities this one holds: those that start inside it.
        var next = index + 1
        while (next < entities.size && entities[next].offset < to) next++
        if (from < to) {
            if (from > position) result += TextPart.Plain(text.substring(position, from))
            val inner = entities.subList(index + 1, next)
            result += TextPart.Marked(entity.type, text.substring(from, to), parts(text, inner, from, to))
            position = to
        }
        index = next
    }
    if (position < end) result += TextPart.Plain(text.substring(position, end))
    return result
}
