package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

// The texts of rich messages (td_api.tl RichText), each one string with its formatting and links as entities.

/**
 * This rich text as a [FormattedText]: its parts one after another, their formatting and links as entities over the
 * parts they cover. Empty for null.
 */
internal fun TdApi.RichText?.toFormattedText(): FormattedText {
    if (this == null) return FormattedText.Empty
    val builder = RichTextBuilder()
    builder.append(this)
    return builder.build()
}

/** The text inside a formatting, with the entity the formatting is; null for other rich texts. */
private fun TdApi.RichText.formatted(): Pair<TdApi.RichText?, TextEntityType>? =
    when (this) {
        is TdApi.RichTextBold -> text to TextEntityType.Bold
        is TdApi.RichTextItalic -> text to TextEntityType.Italic
        is TdApi.RichTextUnderline -> text to TextEntityType.Underline
        is TdApi.RichTextStrikethrough -> text to TextEntityType.Strikethrough
        is TdApi.RichTextFixed -> text to TextEntityType.Code
        is TdApi.RichTextSpoiler -> text to TextEntityType.Spoiler
        is TdApi.RichTextMarked -> text to TextEntityType.Marked
        is TdApi.RichTextSubscript -> text to TextEntityType.Subscript
        is TdApi.RichTextSuperscript -> text to TextEntityType.Superscript
        else -> null
    }

/**
 * The text of a part that is what it says (a mention by name, a hashtag, a date, …), with its entity; null for other
 * rich texts.
 */
private fun TdApi.RichText.kind(): Pair<TdApi.RichText?, TextEntityType>? =
    when (this) {
        is TdApi.RichTextMentionName -> text to TextEntityType.MentionName(userId)
        is TdApi.RichTextHashtag -> text to TextEntityType.Hashtag
        is TdApi.RichTextCashtag -> text to TextEntityType.Cashtag
        is TdApi.RichTextBotCommand -> text to TextEntityType.BotCommand
        is TdApi.RichTextBankCardNumber -> text to TextEntityType.BankCardNumber
        is TdApi.RichTextTonAddress -> text to TextEntityType.TonAddress
        is TdApi.RichTextDateTime -> text to TextEntityType.DateTime(unixTime.toLong(), formattingType?.toFormat())
        else -> null
    }

/** Walks a rich text, appending its characters and recording an entity over each formatted part. */
private class RichTextBuilder {
    private val text = StringBuilder()
    private val entities = mutableListOf<TextEntity>()

    fun append(rich: TdApi.RichText) {
        when (rich) {
            is TdApi.RichTextPlain -> {
                text.append(rich.text.orEmpty())
            }

            is TdApi.RichTexts -> {
                rich.texts.orEmpty().forEach { part -> part?.let(::append) }
            }

            else -> {
                (rich.formatted() ?: rich.kind())?.let { (inner, type) -> around(inner) { type } }
                    ?: appendOther(rich)
            }
        }
    }

    /** Links, mentions, addresses, custom emoji, formulas and buttons; nothing for parts that show no text. */
    private fun appendOther(rich: TdApi.RichText) {
        when (rich) {
            is TdApi.RichTextUrl -> {
                link(rich.text, rich.url)
            }

            // A link to an anchor of this message or a reference opens its URL where it has one.
            is TdApi.RichTextAnchorLink -> {
                link(rich.text, rich.url)
            }

            is TdApi.RichTextReferenceLink -> {
                link(rich.text, rich.url)
            }

            is TdApi.RichTextReference -> {
                rich.text?.let(::append)
            }

            is TdApi.RichTextMention -> {
                mention(rich)
            }

            is TdApi.RichTextEmailAddress -> {
                addressed(
                    rich.text,
                    rich.emailAddress,
                    TextEntityType.EmailAddress,
                    "mailto:",
                )
            }

            is TdApi.RichTextPhoneNumber -> {
                addressed(rich.text, rich.phoneNumber, TextEntityType.PhoneNumber, "tel:")
            }

            is TdApi.RichTextCustomEmoji -> {
                customEmoji(rich)
            }

            // The text as it is now; what it was is not shown.
            is TdApi.RichTextDiff -> {
                rich.text?.let(::append)
            }

            is TdApi.RichTextMathematicalExpression -> {
                code(rich.expression.orEmpty())
            }

            is TdApi.RichTextButton -> {
                button(rich.button)
            }

            // Anchors mark a place, icons are small pictures Pingogram does not draw, and a newer TDLib may add more.
            else -> {
                Unit
            }
        }
    }

    /**
     * [inner], then an entity over it of the type [typeOf] its written text gives, put before the entities inside
     * it, as TDLib orders a text's entities; nothing for an empty part.
     */
    private inline fun around(
        inner: TdApi.RichText?,
        typeOf: (written: String) -> TextEntityType,
    ) {
        val start = text.length
        val nested = entities.size
        inner?.let(::append)
        if (text.length >
            start
        ) {
            entities.add(nested, TextEntity(start, text.length - start, typeOf(text.substring(start))))
        }
    }

    private fun link(
        inner: TdApi.RichText?,
        url: String?,
    ) = if (url.isNullOrBlank()) inner?.let(::append) else around(inner) { TextEntityType.TextUrl(url) }

    /** "@username" written out is a mention; other words for the same username link to it. */
    private fun mention(rich: TdApi.RichTextMention) {
        val username = rich.username.orEmpty()
        around(rich.text) { written ->
            if (username.isEmpty() || written.removePrefix("@").equals(username, ignoreCase = true)) {
                TextEntityType.Mention
            } else {
                TextEntityType.TextUrl("https://t.me/$username")
            }
        }
    }

    /** The [address] written out is an entity of [type]; other words for it link to it with [scheme]. */
    private fun addressed(
        inner: TdApi.RichText?,
        address: String?,
        type: TextEntityType,
        scheme: String,
    ) = around(inner) { written ->
        if (address.isNullOrBlank() || written == address) type else TextEntityType.TextUrl(scheme + address)
    }

    /** The emoji it stands for, under which the custom emoji is drawn; nothing without one. */
    private fun customEmoji(rich: TdApi.RichTextCustomEmoji) {
        val alternative = rich.alternativeText.orEmpty()
        if (alternative.isEmpty()) return
        val start = text.length
        text.append(alternative)
        entities += TextEntity(start, alternative.length, TextEntityType.CustomEmoji(rich.customEmojiId))
    }

    private fun code(written: String) {
        if (written.isEmpty()) return
        val start = text.length
        text.append(written)
        entities += TextEntity(start, written.length, TextEntityType.Code)
    }

    /** A button inside the text: its words, a link where it opens one. */
    private fun button(button: TdApi.InlineButton?) {
        button ?: return
        val type = button.type.toButtonType()
        if (type is InlineKeyboardButtonType.Url) link(button.text, type.url) else button.text?.let(::append)
    }

    /** By where they start, longer first; an outer entity stays before those nested in it where both are as long. */
    fun build(): FormattedText =
        FormattedText(
            text.toString(),
            entities.sortedWith(compareBy<TextEntity> { it.offset }.thenByDescending { it.length }).toImmutableList(),
        )
}
