package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.PREVIEW_MAX_LENGTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The snippet of a search result around the match of a query's words ([MatchHighlight]), and the match's spans. */
class HighlightedTextTest {
    @Test
    fun `a match near the start keeps the whole text, as one line`() {
        assertEquals("Ada wrote the notes", matchSnippet("Ada wrote\nthe notes", MatchHighlight.of("notes")))
    }

    @Test
    fun `a match far in starts at a word shortly before it`() {
        val text = "We spent the whole afternoon in the library talking about everything but the notes of Ada"

        assertEquals("…but the notes of Ada", matchSnippet(text, MatchHighlight.of("ada")))
    }

    @Test
    fun `a snippet is at most as long as a preview, and never cuts an emoji in half`() {
        val text = "Ada " + "x".repeat(PREVIEW_MAX_LENGTH - 5) + "😀 tail"

        val snippet = matchSnippet(text, MatchHighlight.of("ada"))

        assertEquals(PREVIEW_MAX_LENGTH - 1, snippet.length)
        assertTrue(!Character.isHighSurrogate(snippet.last()))
    }

    @Test
    fun `the highlight's spans follow the sender's label`() {
        val match = SpanStyle(color = Color.Red)

        val text = highlighted("Grace:", "We read Ada", MatchHighlight.of("ada"), Color.Black, match)

        assertEquals("Grace: We read Ada", text.text)
        val spans = text.spanStyles.filter { it.item == match }
        assertEquals(listOf(15 to 18), spans.map { it.start to it.end })
    }
}
