package dev.kyralabs.pingogram.core.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [internationalNumber]: how the phone's numbers become international before contacts sync sends them. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InternationalNumberTest {
    @Test
    fun `the provider's normalized number wins`() {
        assertEquals("+447911123456", internationalNumber("07911 123456", "+447911123456", "GB"))
    }

    @Test
    fun `a local number takes the country's code`() {
        assertEquals("+447911123456", internationalNumber("07911 123456", null, "GB"))
    }

    @Test
    fun `without a country, a number keeps its + and its digits`() {
        assertEquals("+15550100", internationalNumber("+1 (555) 0100", null, ""))
        assertEquals("5550100", internationalNumber("555-0100", null, ""))
    }

    @Test
    fun `a number without digits is none`() {
        assertNull(internationalNumber("ext.", null, "GB"))
        assertNull(internationalNumber(null, null, "GB"))
    }
}
