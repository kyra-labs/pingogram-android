package dev.kyralabs.pingogram.feature.chat.ui.composer

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ComposerSavingTest {
    @Test
    fun `every format a sender may set comes back as it was saved`() {
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
                // The separator in an address survives.
                TextEntityType.TextUrl("https://example.org/a|b"),
                TextEntityType.MentionName(42),
                TextEntityType.CustomEmoji(9),
            )
        val text = oneCharacterEach(types)

        assertEquals(text, restoredText(text.text, text.savedEntities()))
    }

    /** A text with an entity of each of [types] on a character of its own. */
    private fun oneCharacterEach(types: List<TextEntityType>): FormattedText {
        val entities = types.mapIndexed { index, type -> TextEntity(index, 1, type) }
        return FormattedText("x".repeat(types.size), entities.toPersistentList())
    }

    @Test
    fun `formats found in the text are not saved`() {
        val text = FormattedText("#tag", persistentListOf(TextEntity(0, 4, TextEntityType.Hashtag)))

        assertEquals(emptyList<String>(), text.savedEntities())
    }

    @Test
    fun `entries that do not fit the text or cannot be read are dropped`() {
        val restored = restoredText("Hello", listOf("0|5|bold", "3|9|italic", "x|1|bold", "0|1|unknown", "0|2|link"))

        assertEquals(FormattedText("Hello", persistentListOf(TextEntity(0, 5, TextEntityType.Bold))), restored)
    }

    @Test
    fun `nothing saved restores nothing`() {
        assertNull(restoredText(null, null))
    }
}
