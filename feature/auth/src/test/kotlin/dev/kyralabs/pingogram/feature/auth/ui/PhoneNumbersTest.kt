package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.domain.model.Country
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNumbersTest {
    private fun country(
        iso: String,
        vararg callingCodes: String,
        isHidden: Boolean = false,
    ) = Country(iso, iso, iso, "", callingCodes.toList(), isHidden)

    private val countries =
        listOf(country("US", "1"), country("AS", "1684"), country("GB", "44"), country("XX", "4", isHidden = true))

    @Test
    fun `a plausible number has a calling code and 7 to 15 digits`() {
        assertTrue(isPlausiblePhoneNumber("44", "7911123456"))
        assertTrue(isPlausiblePhoneNumber("683", "1234"))
        assertFalse(isPlausiblePhoneNumber("", "7911123456"))
        assertFalse(isPlausiblePhoneNumber("44", "1234"))
        assertFalse(isPlausiblePhoneNumber("44", "1234567890123456"))
    }

    @Test
    fun `an international number is split by the longest calling code`() {
        assertEquals("1684" to "2581234", splitInternationalNumber("16842581234", countries))
        assertEquals("1" to "2025550123", splitInternationalNumber("12025550123", countries))
        assertEquals("44" to "7911123456", splitInternationalNumber("447911123456", countries))
        assertNull(splitInternationalNumber("999123", countries))
    }

    @Test
    fun `the current country stays if it has the calling code`() {
        val americanSamoa = countries[1]

        assertEquals(americanSamoa, countryForCallingCode("1684", americanSamoa, countries))
        assertEquals("US", countryForCallingCode("1", countries[2], countries)?.isoCode)
        assertNull(countryForCallingCode("4", null, countries))
        assertNull(countryForCallingCode("", countries[0], countries))
    }

    @Test
    fun `a number format turns digits and placeholders into slots`() {
        assertEquals("---- ------", numberFormatOf("7911 12----"))
        assertEquals("(---) ---", numberFormatOf("(555) ---"))
        assertNull(numberFormatOf(""))
    }

    @Test
    fun `digits are laid out by the format, separators only between digits`() {
        assertEquals("7911 12", formatNationalNumber("791112", "---- ------").text)
        assertEquals("7911", formatNationalNumber("7911", "---- ------").text)
        assertEquals("7911 123456789", formatNationalNumber("7911123456789", "---- ------").text)
        assertEquals("(555) 12", formatNationalNumber("55512", "(---) ---").text)
        assertEquals("791112", formatNationalNumber("791112", null).text)
    }

    @Test
    fun `cursor offsets map between digits and the formatted text`() {
        val formatted = formatNationalNumber("55512", "(---) ---")

        assertArrayEquals(intArrayOf(1, 2, 3, 6, 7), formatted.digitPositions)
        assertEquals(1, formatted.transformedOffset(0))
        assertEquals(6, formatted.transformedOffset(3))
        assertEquals(8, formatted.transformedOffset(5))
        assertEquals(0, formatted.originalOffset(0))
        assertEquals(3, formatted.originalOffset(5))
        assertEquals(5, formatted.originalOffset(8))
    }

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
