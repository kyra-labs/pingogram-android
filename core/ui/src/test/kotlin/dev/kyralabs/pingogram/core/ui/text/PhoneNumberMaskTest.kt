package dev.kyralabs.pingogram.core.ui.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [maskPhoneNumber]: what "Hide phone numbers" shows in place of a formatted number. */
class PhoneNumberMaskTest {
    @Test
    fun `keeps the calling code and the groups, and hides every other digit`() {
        assertEquals("+44 •••• ••••••", maskPhoneNumber("+44 7911 123456"))
        assertEquals("+1 •••-•••-••••", maskPhoneNumber("+1 650-555-0123"))
        assertEquals("+380 •• ••• ••••", maskPhoneNumber("+380 44 123 4567"))
    }

    @Test
    fun `keeps brackets around an area code, hiding its digits`() {
        assertEquals("+1 (•••) •••-••••", maskPhoneNumber("+1 (650) 555-0123"))
    }

    @Test
    fun `hides every digit of a number Android could not group`() {
        assertEquals("+••••••••••••", maskPhoneNumber("+447911123456"))
        assertEquals("•••• ••••", maskPhoneNumber("7911 1234"))
    }

    @Test
    fun `an empty number stays empty`() {
        assertEquals("", maskPhoneNumber(""))
    }
}
