package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.foundation.text.input.insert
import androidx.compose.ui.text.TextRange
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The composer's text outside a text field: formatting, replacing the text, and edits it did not see. How it follows
 * the user's edits through its input transformation is tested in the text field (`ComposerTest`).
 */
class ComposerTextTest {
    private fun formatted(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, persistentListOf(*entities))

    @Test
    fun `starts with the given text and formatting`() {
        val initial = formatted("Hi there", TextEntity(0, 2, TextEntityType.Bold))

        assertEquals(initial, ComposerText(initial).content)
    }

    @Test
    fun `toggle formats the selection, and toggling again removes it`() {
        val text = ComposerText(formatted("Say word now"))

        text.toggle(TextEntityType.Italic, TextRange(4, 8))
        val italic = text.content
        text.toggle(TextEntityType.Italic, TextRange(4, 8))

        assertEquals(formatted("Say word now", TextEntity(4, 4, TextEntityType.Italic)), italic)
        assertEquals(formatted("Say word now"), text.content)
    }

    @Test
    fun `has tells whether the whole selection has a format`() {
        val text = ComposerText(formatted("Say word now", TextEntity(4, 4, TextEntityType.Bold)))

        assertTrue(text.has(TextEntityType.Bold, TextRange(5, 7)))
        assertFalse(text.has(TextEntityType.Bold, TextRange(2, 7)))
    }

    @Test
    fun `setLink links the selection, which linkAt then finds`() {
        val text = ComposerText(formatted("Read this"))

        text.setLink(" https://example.org ", TextRange(5, 9))

        assertEquals("https://example.org", text.linkAt(TextRange(6, 6)))
        assertEquals(
            formatted("Read this", TextEntity(5, 4, TextEntityType.TextUrl("https://example.org"))),
            text.content,
        )
    }

    @Test
    fun `a blank address removes the link`() {
        val text = ComposerText(formatted("Read this", TextEntity(5, 4, TextEntityType.TextUrl("https://example.org"))))

        text.setLink("", TextRange(5, 9))

        assertNull(text.linkAt(TextRange(6, 6)))
        assertEquals(formatted("Read this"), text.content)
    }

    @Test
    fun `setContent replaces text and formatting, with the cursor at the end`() {
        val text = ComposerText(formatted("Old", TextEntity(0, 3, TextEntityType.Bold)))

        text.setContent(formatted("New text", TextEntity(4, 4, TextEntityType.Code)))

        assertEquals(formatted("New text", TextEntity(4, 4, TextEntityType.Code)), text.content)
        assertEquals(TextRange(8), text.state.selection)
    }

    @Test
    fun `an edit made outside the text field moves the formatting as well as it can`() {
        val text = ComposerText(formatted("Say word now", TextEntity(4, 4, TextEntityType.Bold)))

        text.state.edit { insert(0, "I ") }

        assertEquals(formatted("I Say word now", TextEntity(6, 4, TextEntityType.Bold)), text.content)
    }

    @Test
    fun `white space alone is blank, but not empty`() {
        val text = ComposerText(formatted(" \n "))

        assertTrue(text.isBlank)
        assertFalse(text.isEmpty)
    }
}
