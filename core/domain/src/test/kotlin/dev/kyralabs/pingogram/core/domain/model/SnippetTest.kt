package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SnippetTest {
    @Test
    fun `a name is one word of letters, digits and underscores, as long as a bot command at most`() {
        assertTrue(Snippet.isValidName("address"))
        assertTrue(Snippet.isValidName("my_address_2"))
        assertTrue(Snippet.isValidName("адрес"))
        assertTrue(Snippet.isValidName("a".repeat(Snippet.MAX_NAME_LENGTH)))

        assertFalse(Snippet.isValidName(""))
        assertFalse(Snippet.isValidName("my address"))
        assertFalse(Snippet.isValidName("/address"))
        assertFalse(Snippet.isValidName("a".repeat(Snippet.MAX_NAME_LENGTH + 1)))
    }

    @Test
    fun `a snippet logs without its text`() {
        assertEquals("Snippet(id=3)", Snippet(3, "address", "12 Main St").toString())
    }
}
