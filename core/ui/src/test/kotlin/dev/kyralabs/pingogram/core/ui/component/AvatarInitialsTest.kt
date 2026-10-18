package dev.kyralabs.pingogram.core.ui.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AvatarInitialsTest {
    @Test
    fun `takes the first letters of the first two words, in title case`() {
        assertEquals("AL", avatarInitials("ada lovelace"))
        assertEquals("AK", avatarInitials("Ada King Lovelace"))
        assertEquals("A", avatarInitials("Ada"))
    }

    @Test
    fun `ignores surrounding and repeated whitespace, no-break spaces included`() {
        assertEquals("AL", avatarInitials("  Ada \t  Lovelace  "))
    }

    @Test
    fun `is empty for a name without letters, digits or symbols`() {
        assertEquals("", avatarInitials(""))
        assertEquals("", avatarInitials("   "))
        assertEquals("", avatarInitials("(...) --"))
    }

    @Test
    fun `skips punctuation and invisible formatting before a word`() {
        assertEquals("WC", avatarInitials("(Work) chat"))
        assertEquals("QN", avatarInitials("«Quoted» ‏Name"))
    }

    @Test
    fun `keeps emoji and accented letters whole`() {
        assertEquals("😀S", avatarInitials("😀 Smiles"))
        assertEquals("ÉZ", avatarInitials("émile zola"))
    }

    @Test
    fun `handles other scripts`() {
        assertEquals("МК", avatarInitials("Мария Кюри"))
        assertEquals("李", avatarInitials("李雷"))
        assertEquals("1A", avatarInitials("1st Avenue"))
    }
}
