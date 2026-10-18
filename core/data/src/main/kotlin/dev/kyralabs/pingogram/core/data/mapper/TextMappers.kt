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
