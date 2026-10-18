package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import dev.kyralabs.pingogram.core.ui.text.PREVIEW_MAX_LENGTH
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The highlight of a query's words in search results ([MatchHighlight]), the snippet around a match, and its spans. */
class MatchHighlightTest {
    @Test
    fun `every word of the query is matched wherever it occurs, whatever its case`() {
        val highlight = MatchHighlight.of("  ada  LOVE ")

        assertEquals(listOf("ada", "LOVE"), highlight.terms)
        assertEquals(listOf(0..2, 4..7, 13..15), highlight.ranges("Ada Lovelace ADA"))
    }

    @Test
    fun `overlapping and adjacent matches are merged`() {
        assertEquals(listOf(0..3), MatchHighlight.of("ab bcd").ranges("abcd e"))
        assertEquals(listOf(0..3), MatchHighlight.of("ab cd").ranges("abcd"))
    }

    @Test
    fun `a word repeated in another case is matched once`() {
        assertEquals(listOf("ada"), MatchHighlight.of("ada ADA").terms)
    }

    @Test
    fun `no match, and no words, highlight nothing`() {
        assertTrue(MatchHighlight.of("grace").ranges("Ada Lovelace").isEmpty())
        assertTrue(MatchHighlight.None.ranges("Ada").isEmpty())
        assertNull(MatchHighlight.None.firstMatch("Ada"))
    }

    @Test
    fun `the first match is the earliest of any word`() {
        assertEquals(4, MatchHighlight.of("lace love").firstMatch("Ada Lovelace"))
    }

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
