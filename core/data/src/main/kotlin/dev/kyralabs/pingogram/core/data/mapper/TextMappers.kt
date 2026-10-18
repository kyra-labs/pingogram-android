package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.DateTimeFormat
import dev.kyralabs.pingogram.core.domain.model.DateTimePrecision
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.toImmutableList
import org.drinkless.tdlib.TdApi

/** The text with its entities; offsets and lengths stay in UTF-16 code units, as TDLib sends them. */
internal fun TdApi.FormattedText.toFormattedText(): FormattedText =
    FormattedText(
        text = text,
        entities = entities.map { TextEntity(it.offset, it.length, it.type.toTextEntityType()) }.toImmutableList(),
    )

internal fun TdApi.TextEntityType.toTextEntityType(): TextEntityType =
    when (this) {
        is TdApi.TextEntityTypeBold -> TextEntityType.Bold
        is TdApi.TextEntityTypeItalic -> TextEntityType.Italic
        is TdApi.TextEntityTypeUnderline -> TextEntityType.Underline
        is TdApi.TextEntityTypeStrikethrough -> TextEntityType.Strikethrough
        is TdApi.TextEntityTypeSpoiler -> TextEntityType.Spoiler
        is TdApi.TextEntityTypeCode -> TextEntityType.Code
        is TdApi.TextEntityTypePre -> TextEntityType.Pre
        is TdApi.TextEntityTypePreCode -> TextEntityType.PreCode(language)
        is TdApi.TextEntityTypeBlockQuote -> TextEntityType.BlockQuote
        is TdApi.TextEntityTypeExpandableBlockQuote -> TextEntityType.ExpandableBlockQuote
        is TdApi.TextEntityTypeUrl -> TextEntityType.Url
        is TdApi.TextEntityTypeTextUrl -> TextEntityType.TextUrl(url)
        is TdApi.TextEntityTypeMention -> TextEntityType.Mention
        is TdApi.TextEntityTypeMentionName -> TextEntityType.MentionName(userId)
        is TdApi.TextEntityTypeHashtag -> TextEntityType.Hashtag
        is TdApi.TextEntityTypeCashtag -> TextEntityType.Cashtag
        is TdApi.TextEntityTypeBotCommand -> TextEntityType.BotCommand
        is TdApi.TextEntityTypeEmailAddress -> TextEntityType.EmailAddress
        is TdApi.TextEntityTypePhoneNumber -> TextEntityType.PhoneNumber
        is TdApi.TextEntityTypeBankCardNumber -> TextEntityType.BankCardNumber
        is TdApi.TextEntityTypeTonAddress -> TextEntityType.TonAddress
        is TdApi.TextEntityTypeCustomEmoji -> TextEntityType.CustomEmoji(customEmojiId)
        is TdApi.TextEntityTypeMediaTimestamp -> TextEntityType.MediaTimestamp(mediaTimestamp)
        is TdApi.TextEntityTypeDateTime -> TextEntityType.DateTime(unixTime.toLong(), formattingType?.toFormat())
        else -> TextEntityType.Unknown
    }

/** Null for a formatting added by a newer TDLib: the text then stays as written. */
private fun TdApi.DateTimeFormattingType.toFormat(): DateTimeFormat? =
    when (this) {
        is TdApi.DateTimeFormattingTypeRelative -> {
            DateTimeFormat.Relative
        }

        is TdApi.DateTimeFormattingTypeAbsolute -> {
            DateTimeFormat.Absolute(
                timePrecision.toPrecision(),
                datePrecision.toPrecision(),
                showDayOfWeek,
            )
        }

        else -> {
            null
        }
    }

private fun TdApi.DateTimePartPrecision.toPrecision(): DateTimePrecision =
    when (this) {
        is TdApi.DateTimePartPrecisionNone -> DateTimePrecision.None

        is TdApi.DateTimePartPrecisionLong -> DateTimePrecision.Long

        // Short, and a precision added by a newer TDLib.
        else -> DateTimePrecision.Short
    }

/**
 * The text with the entities a sender may set, for sending it or keeping it as a draft (td_api.tl `inputMessageText`):
 * bold, italic, underline, strikethrough, spoiler, code, pre, pre-code, block quotes, links behind text, mentions by
 * name, custom emoji and dates. URLs, mentions, hashtags and the like are left out: TDLib finds those in the text.
 */
internal fun FormattedText.toTdFormattedText(): TdApi.FormattedText =
    TdApi.FormattedText(
        text,
        entities
            .mapNotNull { entity ->
                entity.type.toTdTextEntityType()?.let { TdApi.TextEntity(entity.offset, entity.length, it) }
            }.toTypedArray(),
    )

/** Null for the types TDLib finds itself, and for [TextEntityType.Unknown]. */
private fun TextEntityType.toTdTextEntityType(): TdApi.TextEntityType? =
    when (this) {
        TextEntityType.Bold -> TdApi.TextEntityTypeBold()

        TextEntityType.Italic -> TdApi.TextEntityTypeItalic()

        TextEntityType.Underline -> TdApi.TextEntityTypeUnderline()

        TextEntityType.Strikethrough -> TdApi.TextEntityTypeStrikethrough()

        TextEntityType.Spoiler -> TdApi.TextEntityTypeSpoiler()

        TextEntityType.Code -> TdApi.TextEntityTypeCode()

        TextEntityType.Pre -> TdApi.TextEntityTypePre()

        is TextEntityType.PreCode -> TdApi.TextEntityTypePreCode(language)

        TextEntityType.BlockQuote -> TdApi.TextEntityTypeBlockQuote()

        TextEntityType.ExpandableBlockQuote -> TdApi.TextEntityTypeExpandableBlockQuote()

        is TextEntityType.TextUrl -> TdApi.TextEntityTypeTextUrl(url)

        is TextEntityType.MentionName -> TdApi.TextEntityTypeMentionName(userId)

        is TextEntityType.CustomEmoji -> TdApi.TextEntityTypeCustomEmoji(customEmojiId)

        is TextEntityType.DateTime -> TdApi.TextEntityTypeDateTime(unixTime.toInt(), format?.toTdFormat())

        TextEntityType.Url,
        TextEntityType.Mention,
        TextEntityType.Hashtag,
        TextEntityType.Cashtag,
        TextEntityType.BotCommand,
        TextEntityType.EmailAddress,
        TextEntityType.PhoneNumber,
        TextEntityType.BankCardNumber,
        TextEntityType.TonAddress,
        is TextEntityType.MediaTimestamp,
        TextEntityType.Unknown,
        -> null
    }

private fun DateTimeFormat.toTdFormat(): TdApi.DateTimeFormattingType =
    when (this) {
        DateTimeFormat.Relative -> {
            TdApi.DateTimeFormattingTypeRelative()
        }

        is DateTimeFormat.Absolute -> {
            TdApi.DateTimeFormattingTypeAbsolute(time.toTdPrecision(), date.toTdPrecision(), showDayOfWeek)
        }
    }

private fun DateTimePrecision.toTdPrecision(): TdApi.DateTimePartPrecision =
    when (this) {
        DateTimePrecision.None -> TdApi.DateTimePartPrecisionNone()
        DateTimePrecision.Short -> TdApi.DateTimePartPrecisionShort()
        DateTimePrecision.Long -> TdApi.DateTimePartPrecisionLong()
    }
