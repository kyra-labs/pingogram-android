package dev.kyralabs.pingogram.feature.chat.ui.bot

import dev.kyralabs.pingogram.core.domain.model.Snippet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SlashWordTest {
    @Test
    fun `the slash word before the cursor, at the start or after white space`() {
        assertEquals(SlashWord(0, 4, "add"), snippetQuery("/add", 4))
        assertEquals(SlashWord(8, 13, "addr"), snippetQuery("Send to /addr", 13))
        assertEquals(SlashWord(4, 9, "адре"), snippetQuery("Hi\n /адре", 9))
        assertEquals(SlashWord(0, 1, ""), snippetQuery("/", 1))
    }

    @Test
    fun `no word inside another, after a space, in a link or without a cursor`() {
        assertNull(snippetQuery("and/or", 6))
        assertNull(snippetQuery("/add ", 5))
        assertNull(snippetQuery("https://x.dev/add", 17))
        assertNull(snippetQuery("/add", -1))
    }

    @Test
    fun `snippets whose names the query starts, ignoring case`() {
        val snippets = listOf(Snippet(1, "address", "A"), Snippet(2, "Admin", "B"), Snippet(3, "thanks", "C"))

        assertEquals(listOf(1L, 2L), snippets.namedFrom("AD").map { it.id })
        assertEquals(3, snippets.namedFrom("").size)
    }
}
