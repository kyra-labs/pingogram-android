package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ProfileModelTest {
    @Test
    fun `letters, digits and underscores, starting with a letter, make a username`() {
        listOf("ada_l", "Ada1815", "a_b_c_d", "abcde", "a".repeat(32)).forEach { assertNull(usernameProblem(it), it) }
    }

    @Test
    fun `a username needs 5 to 32 characters`() {
        assertEquals(UsernameProblem.TooShort, usernameProblem("ada"))
        assertEquals(UsernameProblem.TooShort, usernameProblem(""))
        assertEquals(UsernameProblem.TooLong, usernameProblem("a".repeat(33)))
    }

    @Test
    fun `other characters are refused first, wherever they are`() {
        assertEquals(UsernameProblem.InvalidCharacter, usernameProblem("ada lovelace"))
        assertEquals(UsernameProblem.InvalidCharacter, usernameProblem("adä_l"))
        assertEquals(UsernameProblem.InvalidCharacter, usernameProblem("1-a"))
        assertEquals(UsernameProblem.InvalidCharacter, usernameProblem("@ada_l"))
    }

    @Test
    fun `a username starts with a letter, even while it is short`() {
        assertEquals(UsernameProblem.StartsWithNonLetter, usernameProblem("1ada"))
        assertEquals(UsernameProblem.StartsWithNonLetter, usernameProblem("_ada_l"))
    }

    @Test
    fun `underscores neither end a username nor follow each other`() {
        assertEquals(UsernameProblem.EndsWithUnderscore, usernameProblem("ada_l_"))
        assertEquals(UsernameProblem.ConsecutiveUnderscores, usernameProblem("ada__l"))
    }

    @Test
    fun `characters are counted as TDLib counts them, by code point`() {
        assertEquals(3, "Ada".characterCount())
        // An emoji outside the Basic Multilingual Plane is two UTF-16 units but one character.
        assertEquals(4, "Ada😀".characterCount())
    }
}
