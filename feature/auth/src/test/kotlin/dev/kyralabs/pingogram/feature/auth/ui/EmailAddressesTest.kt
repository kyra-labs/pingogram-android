package dev.kyralabs.pingogram.feature.auth.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EmailAddressesTest {
    @Test
    fun `a plausible email address has one at sign with text on both sides`() {
        assertTrue(isPlausibleEmailAddress("ada@example.org"))
        assertFalse(isPlausibleEmailAddress("ada.example.org"))
        assertFalse(isPlausibleEmailAddress("@example.org"))
        assertFalse(isPlausibleEmailAddress("ada@"))
        assertFalse(isPlausibleEmailAddress("a@b@example.org"))
        assertFalse(isPlausibleEmailAddress("ada @example.org"))
    }
}
