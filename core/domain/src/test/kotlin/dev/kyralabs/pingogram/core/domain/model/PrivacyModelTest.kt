package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrivacyModelTest {
    private val both =
        PrivacyExceptions(allowedUserCount = 2, restrictedChatCount = 1)

    @Test
    fun `no exceptions change nothing`() {
        assertFalse(PrivacyChoice.entries.any { PrivacyRules(it).hasExceptions })
    }

    @Test
    fun `everybody counts only those never allowed`() {
        val rules = PrivacyRules(PrivacyChoice.Everybody, both)

        assertEquals(listOf(false, true), listOf(rules.hasAlwaysAllowed, rules.hasNeverAllowed))
    }

    @Test
    fun `nobody counts only those always allowed`() {
        val rules = PrivacyRules(PrivacyChoice.Nobody, both)

        assertEquals(listOf(true, false), listOf(rules.hasAlwaysAllowed, rules.hasNeverAllowed))
    }

    @Test
    fun `my contacts counts both`() {
        val rules = PrivacyRules(PrivacyChoice.Contacts, both)

        assertEquals(listOf(true, true), listOf(rules.hasAlwaysAllowed, rules.hasNeverAllowed))
    }

    @Test
    fun `premium subscribers allowed are an exception`() {
        assertTrue(PrivacyRules(PrivacyChoice.Contacts, PrivacyExceptions(allowsPremiumUsers = true)).hasExceptions)
    }

    @Test
    fun `exceptions that change nothing for the choice are none`() {
        assertFalse(PrivacyRules(PrivacyChoice.Everybody, PrivacyExceptions(allowedUserCount = 3)).hasExceptions)
        assertFalse(PrivacyRules(PrivacyChoice.Nobody, PrivacyExceptions(restrictedUserCount = 3)).hasExceptions)
    }
}
