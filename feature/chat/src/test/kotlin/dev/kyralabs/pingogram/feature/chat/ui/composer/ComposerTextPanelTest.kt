package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.ui.text.TextRange
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What the emoji panel does to the composer's text: emoji go in at the cursor, the backspace deletes whole emoji
 * sequences. On Robolectric for Android's ICU, which knows the grapheme clusters of emoji.
 */
@RunWith(RobolectricTestRunner::class)
class ComposerTextPanelTest {
    private fun formatted(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, persistentListOf(*entities))

    @Test
    fun `an emoji goes in at the cursor, which moves past it, and formatting after it moves along`() {
        val text = ComposerText(formatted("Hi there", TextEntity(3, 5, TextEntityType.Bold)))
        text.state.edit { selection = TextRange(3) }

        text.insert("👋 ")

        assertEquals(formatted("Hi 👋 there", TextEntity(6, 5, TextEntityType.Bold)), text.content)
        assertEquals(TextRange(6), text.state.selection)
    }

    @Test
    fun `an emoji replaces the selection`() {
        val text = ComposerText(formatted("I like cats"))
        text.state.edit { selection = TextRange(7, 11) }

        text.insert("🐱")

        assertEquals("I like 🐱", text.content.text)
        assertEquals(TextRange(9), text.state.selection)
    }

    @Test
    fun `backspace deletes a whole emoji sequence before the cursor`() {
        val family = "👩‍👩‍👧"
        val text = ComposerText(formatted("Hi $family"))

        text.deleteBackward()

        assertEquals("Hi ", text.content.text)
        assertEquals(TextRange(3), text.state.selection)
    }

    @Test
    fun `backspace deletes a flag and a skin tone as one`() {
        val text = ComposerText(formatted("🇺🇦👍🏽"))

        text.deleteBackward()
        val afterThumb = text.content.text
        text.deleteBackward()

        assertEquals("🇺🇦", afterThumb)
        assertEquals("", text.content.text)
    }

    @Test
    fun `backspace deletes the selection, and nothing at the start`() {
        val text = ComposerText(formatted("Say word now", TextEntity(9, 3, TextEntityType.Italic)))
        text.state.edit { selection = TextRange(4, 9) }

        text.deleteBackward()
        text.state.edit { selection = TextRange(0) }
        text.deleteBackward()

        assertEquals(formatted("Say now", TextEntity(4, 3, TextEntityType.Italic)), text.content)
    }
}
