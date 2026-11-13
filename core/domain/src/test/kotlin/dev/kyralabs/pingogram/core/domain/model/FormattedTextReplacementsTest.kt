package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Writing the parts of some entities anew, the other entities following them. */
class FormattedTextReplacementsTest {
    private val date = TextEntityType.DateTime(1_796_580_000, DateTimeFormat.Relative)

    private fun text(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, entities.toList().toImmutableList())

    private fun FormattedText.parts(): List<Pair<String, TextEntityType>> =
        entities.map { text.substring(it.offset, it.offset + it.length) to it.type }

    @Test
    fun `a replaced part takes its new text, and the entities after it follow`() {
        val source =
            text(
                "Meet at 6 pm, @ada",
                TextEntity(8, 4, date),
                TextEntity(14, 4, TextEntityType.Mention),
            )

        val replaced = source.replacingEntities { if (it.type is TextEntityType.DateTime) "Sunday, 18:00" else null }

        assertEquals("Meet at Sunday, 18:00, @ada", replaced.text)
        assertEquals(listOf("Sunday, 18:00" to date, "@ada" to TextEntityType.Mention), replaced.parts())
    }

    @Test
    fun `an entity around a replaced part grows with it, and one inside it covers the new part`() {
        val source =
            text(
                "Due 6 pm sharp",
                TextEntity(0, 14, TextEntityType.Bold),
                TextEntity(4, 4, date),
                TextEntity(5, 2, TextEntityType.Italic),
            )

        val replaced = source.replacingEntities { if (it.type is TextEntityType.DateTime) "in 2 hours" else null }

        assertEquals("Due in 2 hours sharp", replaced.text)
        assertEquals(
            listOf(
                "Due in 2 hours sharp" to TextEntityType.Bold,
                "in 2 hours" to date,
                "in 2 hours" to TextEntityType.Italic,
            ),
            replaced.parts(),
        )
    }

    @Test
    fun `several parts are replaced in order, and one overlapping a part before it is kept`() {
        val source =
            text(
                "a 1 b 2 c",
                TextEntity(2, 1, date),
                TextEntity(2, 3, date),
                TextEntity(6, 1, date),
            )

        val replaced = source.replacingEntities { "[${it.offset}]" }

        assertEquals("a [2] b [6] c", replaced.text)
        assertEquals(listOf("[2]", "[2] b", "[6]"), replaced.parts().map { it.first })
    }

    @Test
    fun `replacing nothing returns the text itself`() {
        val source = text("Hello", TextEntity(0, 5, TextEntityType.Bold))

        assertSame(source, source.replacingEntities { null })
    }
}
