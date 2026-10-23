package dev.kyralabs.pingogram.core.ui.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words of a search query that a text shows highlighted, and where it shows them. */
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
}
