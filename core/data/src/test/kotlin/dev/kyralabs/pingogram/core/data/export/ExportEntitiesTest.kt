package dev.kyralabs.pingogram.core.data.export

import dev.kyralabs.pingogram.core.domain.model.DateTimeFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** The text of an export as nested parts, and its entities' names in the schema. */
class ExportEntitiesTest {
    private fun plainOf(parts: List<TextPart>): String =
        parts.joinToString("") { part ->
            when (part) {
                is TextPart.Plain -> part.text
                is TextPart.Marked -> plainOf(part.parts)
            }
        }

    @Test
    fun `nests entities inside each other`() {
        val parts =
            text(
                "ab cd ef",
                TextEntity(0, 5, TextEntityType.Bold),
                TextEntity(3, 2, TextEntityType.Italic),
            ).toParts()

        assertEquals(
            listOf(
                TextPart.Marked(
                    TextEntityType.Bold,
                    "ab cd",
                    listOf(
                        TextPart.Plain("ab "),
                        TextPart.Marked(TextEntityType.Italic, "cd", listOf(TextPart.Plain("cd"))),
                    ),
                ),
                TextPart.Plain(" ef"),
            ),
            parts,
        )
    }

    @Test
    fun `entities that overlap or reach past the text are cut, so every character shows once`() {
        val text =
            text(
                "😀 one two",
                TextEntity(0, 6, TextEntityType.Bold),
                TextEntity(4, 5, TextEntityType.Italic),
                TextEntity(8, 40, TextEntityType.Code),
            )

        assertEquals(text.text, plainOf(text.toParts()))
    }

    @Test
    fun `every entity type keeps its name and value through the schema`() {
        val types =
            listOf(
                TextEntityType.Bold,
                TextEntityType.Italic,
                TextEntityType.Underline,
                TextEntityType.Strikethrough,
                TextEntityType.Spoiler,
                TextEntityType.Code,
                TextEntityType.Pre,
                TextEntityType.PreCode("kotlin"),
                TextEntityType.BlockQuote,
                TextEntityType.ExpandableBlockQuote,
                TextEntityType.Url,
                TextEntityType.TextUrl("https://example.org"),
                TextEntityType.Mention,
                TextEntityType.MentionName(42),
                TextEntityType.Hashtag,
                TextEntityType.Cashtag,
                TextEntityType.BotCommand,
                TextEntityType.EmailAddress,
                TextEntityType.PhoneNumber,
                TextEntityType.BankCardNumber,
                TextEntityType.TonAddress,
                TextEntityType.CustomEmoji(7),
                TextEntityType.MediaTimestamp(90),
                TextEntityType.DateTime(1_700_000_000, null),
                TextEntityType.Unknown,
            )

        assertEquals(types, types.map { it.toEntityName().toEntityType() })
        // The format of a date is not kept.
        assertEquals(
            TextEntityType.DateTime(5, null),
            TextEntityType.DateTime(5, DateTimeFormat.Relative).toEntityName().toEntityType(),
        )
    }
}
