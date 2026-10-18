package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.DateTimeFormat
import dev.kyralabs.pingogram.core.domain.model.DateTimePrecision
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.lang.reflect.Modifier

class TextMappersTest {
    /** One instance of every `TextEntityType` of the pinned TDLib version, in td_api.tl order, and its mapping. */
    private val allEntityTypes: List<Pair<TdApi.TextEntityType, TextEntityType>> =
        listOf(
            TdApi.TextEntityTypeMention() to TextEntityType.Mention,
            TdApi.TextEntityTypeHashtag() to TextEntityType.Hashtag,
            TdApi.TextEntityTypeCashtag() to TextEntityType.Cashtag,
            TdApi.TextEntityTypeBotCommand() to TextEntityType.BotCommand,
            TdApi.TextEntityTypeUrl() to TextEntityType.Url,
            TdApi.TextEntityTypeEmailAddress() to TextEntityType.EmailAddress,
            TdApi.TextEntityTypePhoneNumber() to TextEntityType.PhoneNumber,
            TdApi.TextEntityTypeBankCardNumber() to TextEntityType.BankCardNumber,
            TdApi.TextEntityTypeTonAddress() to TextEntityType.TonAddress,
            TdApi.TextEntityTypeBold() to TextEntityType.Bold,
            TdApi.TextEntityTypeItalic() to TextEntityType.Italic,
            TdApi.TextEntityTypeUnderline() to TextEntityType.Underline,
            TdApi.TextEntityTypeStrikethrough() to TextEntityType.Strikethrough,
            TdApi.TextEntityTypeSpoiler() to TextEntityType.Spoiler,
            TdApi.TextEntityTypeCode() to TextEntityType.Code,
            TdApi.TextEntityTypePre() to TextEntityType.Pre,
            TdApi.TextEntityTypePreCode("kotlin") to TextEntityType.PreCode("kotlin"),
            TdApi.TextEntityTypeBlockQuote() to TextEntityType.BlockQuote,
            TdApi.TextEntityTypeExpandableBlockQuote() to TextEntityType.ExpandableBlockQuote,
            TdApi.TextEntityTypeTextUrl("https://example.org") to TextEntityType.TextUrl("https://example.org"),
            TdApi.TextEntityTypeMentionName(42) to TextEntityType.MentionName(42),
            TdApi.TextEntityTypeCustomEmoji(5_000_000_001) to TextEntityType.CustomEmoji(5_000_000_001),
            TdApi.TextEntityTypeMediaTimestamp(95) to TextEntityType.MediaTimestamp(95),
            TdApi.TextEntityTypeDateTime(1_700_000_000, TdApi.DateTimeFormattingTypeRelative()) to
                TextEntityType.DateTime(1_700_000_000, DateTimeFormat.Relative),
        )

    @Test
    fun `every entity type maps to its own type`() {
        assertEquals(allEntityTypes.map { it.second }, allEntityTypes.map { it.first.toTextEntityType() })
    }

    /** Guards TDLib bumps: a new entity type needs a case in TextEntityType and an entry above. */
    @Test
    fun `the entity types above cover the pinned TDLib version`() {
        val covered = allEntityTypes.map { it.first.javaClass.simpleName }.toSet()

        assertEquals(concreteTdApiSubclassesOf(TdApi.TextEntityType::class.java), covered)
    }

    @Test
    fun `entities keep their offsets and lengths in UTF-16 code units`() {
        val text =
            TdApi.FormattedText(
                "👋 Hi @bob",
                arrayOf(
                    TdApiFixtures.textEntity(3, 2, TdApi.TextEntityTypeBold()),
                    TdApiFixtures.textEntity(6, 4, TdApi.TextEntityTypeMention()),
                ),
            )

        assertEquals(
            FormattedText(
                "👋 Hi @bob",
                persistentListOf(TextEntity(3, 2, TextEntityType.Bold), TextEntity(6, 4, TextEntityType.Mention)),
            ),
            text.toFormattedText(),
        )
    }

    @Test
    fun `a text without entities maps to an empty entity list`() {
        assertEquals(FormattedText("plain", persistentListOf()), TdApiFixtures.formattedText("plain").toFormattedText())
    }

    @Test
    fun `date time entities keep their absolute format, or none`() {
        val absolute =
            TdApi.DateTimeFormattingTypeAbsolute(
                TdApi.DateTimePartPrecisionNone(),
                TdApi.DateTimePartPrecisionLong(),
                true,
            )
        val types =
            listOf(
                TdApi.TextEntityTypeDateTime(1_700_000_000, absolute),
                TdApi.TextEntityTypeDateTime(
                    1_700_000_000,
                    TdApi.DateTimeFormattingTypeAbsolute(
                        TdApi.DateTimePartPrecisionShort(),
                        TdApi.DateTimePartPrecisionShort(),
                        false,
                    ),
                ),
                TdApi.TextEntityTypeDateTime(1_700_000_000, null),
            ).map { it.toTextEntityType() }

        assertEquals(
            listOf(
                TextEntityType.DateTime(
                    1_700_000_000,
                    DateTimeFormat.Absolute(DateTimePrecision.None, DateTimePrecision.Long, showDayOfWeek = true),
                ),
                TextEntityType.DateTime(
                    1_700_000_000,
                    DateTimeFormat.Absolute(DateTimePrecision.Short, DateTimePrecision.Short, showDayOfWeek = false),
                ),
                TextEntityType.DateTime(1_700_000_000, null),
            ),
            types,
        )
    }

    @Test
    fun `an entity type added by a newer TDLib maps to Unknown`() {
        val newType =
            object : TdApi.TextEntityType() {
                override fun getConstructor() = 0
            }

        assertEquals(TextEntityType.Unknown, newType.toTextEntityType())
    }

    private fun concreteTdApiSubclassesOf(type: Class<*>): Set<String> =
        TdApi::class.java.declaredClasses
            .filter { type.isAssignableFrom(it) && !Modifier.isAbstract(it.modifiers) }
            .map { it.simpleName }
            .toSet()
}
