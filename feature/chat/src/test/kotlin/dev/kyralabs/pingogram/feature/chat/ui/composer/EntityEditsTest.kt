package dev.kyralabs.pingogram.feature.chat.ui.composer

import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EntityEditsTest {
    private fun bold(
        offset: Int,
        length: Int,
    ) = TextEntity(offset, length, TextEntityType.Bold)

    private fun italic(
        offset: Int,
        length: Int,
    ) = TextEntity(offset, length, TextEntityType.Italic)

    private fun link(
        offset: Int,
        length: Int,
        url: String = "https://example.org",
    ) = TextEntity(offset, length, TextEntityType.TextUrl(url))

    /** Bold over characters 4 until 8 of "Say WORD now". */
    private val word = listOf(bold(4, 4))

    /** A custom emoji over the two characters of "🎉" in "Hi 🎉 there", characters 3 and 4. */
    private val emoji = listOf(TextEntity(3, 2, TextEntityType.CustomEmoji(7)))

    @Nested
    inner class CustomEmoji {
        @Test
        fun `text typed before or after it moves it or leaves it`() {
            assertEquals(listOf(TextEntity(5, 2, TextEntityType.CustomEmoji(7))), emoji.afterReplace(3, 3, 2))
            assertEquals(emoji, emoji.afterReplace(5, 5, 1))
        }

        @Test
        fun `text typed into it removes it, as it is no custom emoji any more`() {
            assertEquals(emptyList<TextEntity>(), emoji.afterReplace(4, 4, 1))
        }

        @Test
        fun `deleting a part of it removes it instead of keeping half an emoji`() {
            assertEquals(emptyList<TextEntity>(), emoji.afterReplace(4, 6, 0))
            assertEquals(emptyList<TextEntity>(), emoji.afterReplace(2, 4, 0))
        }

        @Test
        fun `formatting around it keeps it whole`() {
            val formatted = emoji + bold(0, 11)

            assertEquals(
                listOf(bold(0, 13), TextEntity(3, 2, TextEntityType.CustomEmoji(7))),
                formatted.afterReplace(8, 8, 2),
            )
        }
    }

    @Nested
    inner class Typing {
        @Test
        fun `text typed before an entity moves it`() {
            assertEquals(listOf(bold(6, 4)), word.afterReplace(0, 0, 2))
        }

        @Test
        fun `text typed at the start of an entity stays outside it`() {
            assertEquals(listOf(bold(5, 4)), word.afterReplace(4, 4, 1))
        }

        @Test
        fun `text typed inside an entity joins it`() {
            assertEquals(listOf(bold(4, 6)), word.afterReplace(6, 6, 2))
        }

        @Test
        fun `text typed at the end of an entity stays outside it`() {
            assertEquals(word, word.afterReplace(8, 8, 3))
        }

        @Test
        fun `text typed after an entity leaves it`() {
            assertEquals(word, word.afterReplace(10, 10, 1))
        }
    }

    @Nested
    inner class Replacing {
        @Test
        fun `a replaced part before an entity moves it by the difference`() {
            assertEquals(listOf(bold(2, 4)), word.afterReplace(0, 3, 1))
        }

        @Test
        fun `a corrected word inside an entity keeps the formatting`() {
            assertEquals(listOf(bold(4, 5)), word.afterReplace(4, 8, 5))
        }

        @Test
        fun `deleting inside an entity shortens it`() {
            assertEquals(listOf(bold(4, 2)), word.afterReplace(5, 7, 0))
        }

        @Test
        fun `deleting all of an entity removes it`() {
            assertEquals(emptyList<TextEntity>(), word.afterReplace(4, 8, 0))
        }

        @Test
        fun `a replacement over the start of an entity cuts it, the new text outside`() {
            assertEquals(listOf(bold(3, 2)), word.afterReplace(2, 6, 1))
        }

        @Test
        fun `a replacement over the end of an entity cuts it, the new text outside`() {
            assertEquals(listOf(bold(4, 2)), word.afterReplace(6, 10, 1))
        }

        @Test
        fun `a replacement over all of an entity and more removes it`() {
            assertEquals(emptyList<TextEntity>(), word.afterReplace(2, 10, 3))
        }

        @Test
        fun `each entity follows on its own, nested ones too`() {
            val nested = listOf(link(0, 12), bold(4, 4))

            assertEquals(listOf(link(0, 13), bold(4, 5)), nested.afterReplace(6, 6, 1))
        }
    }

    @Nested
    inner class UnseenEdits {
        @Test
        fun `an undone insertion is found between the common start and end`() {
            assertEquals(listOf(bold(4, 4)), listOf(bold(4, 6)).afterChange("Say WOxxRD now", "Say WORD now"))
        }

        @Test
        fun `a text set anew keeps only what lies in the common start and end`() {
            assertEquals(emptyList<TextEntity>(), word.afterChange("Say WORD now", "Something else"))
        }

        @Test
        fun `an unchanged text keeps every entity`() {
            assertEquals(word, word.afterChange("Say WORD now", "Say WORD now"))
        }
    }

    @Nested
    inner class Toggling {
        @Test
        fun `a format is applied to the selection`() {
            assertEquals(listOf(italic(0, 3), bold(4, 4)), word.toggling(TextEntityType.Italic, 0, 3))
        }

        @Test
        fun `a format the whole selection has is removed from it, the rest stays`() {
            assertEquals(listOf(bold(4, 1), bold(7, 1)), word.toggling(TextEntityType.Bold, 5, 7))
        }

        @Test
        fun `a selection partly formatted becomes formatted as a whole, joined with what it overlaps`() {
            assertEquals(listOf(bold(0, 8)), word.toggling(TextEntityType.Bold, 0, 6))
        }

        @Test
        fun `a format applied next to the same one joins it`() {
            assertEquals(listOf(bold(4, 6)), word.toggling(TextEntityType.Bold, 8, 10))
        }

        @Test
        fun `an empty selection changes nothing`() {
            assertEquals(word, word.toggling(TextEntityType.Italic, 3, 3))
        }

        @Test
        fun `a link replaces the links of the selection`() {
            val linked = listOf(link(0, 8, "https://old.example"))

            assertEquals(
                listOf(link(0, 2, "https://old.example"), link(2, 6, "https://new.example")),
                linked.toggling(TextEntityType.TextUrl("https://new.example"), 2, 8),
            )
        }

        @Test
        fun `removing links removes every address`() {
            val linked = listOf(link(0, 4, "https://a.example"), link(4, 4, "https://b.example"))

            assertEquals(emptyList<TextEntity>(), linked.removing(TextEntityType.TextUrl(""), 0, 8))
        }
    }

    @Nested
    inner class Reading {
        @Test
        fun `covers needs the format on every character, across adjacent entities`() {
            val split = listOf(bold(0, 3), bold(3, 3))

            assertTrue(split.covers(TextEntityType.Bold, 1, 5))
            assertFalse(split.covers(TextEntityType.Bold, 1, 7))
            assertFalse(split.covers(TextEntityType.Italic, 1, 2))
            assertFalse(split.covers(TextEntityType.Bold, 2, 2))
        }

        @Test
        fun `linkAt finds the link around the selection`() {
            val linked = listOf(link(2, 6))

            assertEquals("https://example.org", linked.linkAt(3, 5))
            assertEquals("https://example.org", linked.linkAt(4, 4))
            assertNull(linked.linkAt(0, 5))
        }
    }
}
