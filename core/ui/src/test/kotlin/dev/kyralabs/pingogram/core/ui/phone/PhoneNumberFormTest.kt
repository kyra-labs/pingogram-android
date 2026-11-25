package dev.kyralabs.pingogram.core.ui.phone

import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhoneNumberFormTest {
    private val britain =
        Country("GB", "United Kingdom", "United Kingdom", "🇬🇧", persistentListOf("44"), isHidden = false)
    private val canada = Country("CA", "Canada", "Canada", "🇨🇦", persistentListOf("1"), isHidden = false)
    private val states =
        Country("US", "United States", "United States", "🇺🇸", persistentListOf("1"), isHidden = false)
    private val form = PhoneNumberForm(countries = listOf(britain, canada, states))

    @Test
    fun `a calling code picks its country`() {
        val typed = form.withCallingCodeInput("+44")

        assertEquals("44", typed.callingCode)
        assertEquals("GB", typed.countryIso)
        assertTrue(typed.isCountryDetected)
    }

    @Test
    fun `a whole number typed into the calling code is split`() {
        val typed = form.withCallingCodeInput("447911123456")

        assertEquals("44", typed.callingCode)
        assertEquals("7911123456", typed.number)
    }

    @Test
    fun `a number starting with a plus is split, others keep the calling code`() {
        assertEquals("44" to "7911123456", form.withNumberInput("+44 7911 123456").let { it.callingCode to it.number })
        assertEquals("" to "7911", form.withNumberInput("7911").let { it.callingCode to it.number })
    }

    @Test
    fun `picking a country sets its calling code and closes the picker`() {
        val picked = form.copy(isPickerOpen = true, pickerQuery = "can").withCountry("CA")

        assertEquals("1", picked.callingCode)
        assertEquals("CA", picked.countryIso)
        assertFalse(picked.isPickerOpen)
        assertNull(picked.picker)
    }

    @Test
    fun `the picker lists the countries matching its query`() {
        val picker = form.copy(isPickerOpen = true, pickerQuery = "united").picker

        assertEquals(listOf("GB", "US"), picker?.countries?.map { it.isoCode })
    }

    @Test
    fun `the detected country never replaces a typed calling code`() {
        assertEquals("GB", form.withDetectedCountry("gb").countryIso)
        assertEquals("1", form.withCallingCodeInput("1").withDetectedCountry("GB").callingCode)
        assertFalse(form.withDetectedCountry("GB").wantsDetectedCountry)
    }

    @Test
    fun `phone number info formats the number and keeps a picked country while the number is empty`() {
        val canadian = form.withCountry("CA")
        val info = PhoneNumberInfo(states, "1", "--- --- ----", isAnonymous = false)

        assertEquals("CA", canadian.withPhoneNumberInfo(info).countryIso)
        assertEquals("US", canadian.withNumberInput("212").withPhoneNumberInfo(info).countryIso)
        assertEquals("--- --- ----", canadian.withPhoneNumberInfo(info).numberFormat)
    }

    @Test
    fun `a number shorter than its country's format is too short, and warned about once`() {
        val info = PhoneNumberInfo(britain, "44", "7911 12----", isAnonymous = false)
        val short = form.withCountry("GB").withNumberInput("791112").withPhoneNumberInfo(info)

        val warned = short.warnedIfTooShort()

        assertEquals(10, short.expectedLength)
        assertTrue(short.isTooShort)
        assertTrue(warned!!.isShortWarned)
        assertNull(warned.warnedIfTooShort())
        assertFalse(warned.withNumberInput("7911123").isShortWarned)
    }

    @Test
    fun `nothing is too short where the device is not sure`() {
        val anonymous = PhoneNumberInfo(null, "888", "0812 ----", isAnonymous = true)
        val unknown = PhoneNumberInfo(null, "999", "12", isAnonymous = false)

        assertNull(
            form
                .withCallingCodeInput("888")
                .withNumberInput("0812")
                .withPhoneNumberInfo(anonymous)
                .expectedLength,
        )
        assertNull(
            form
                .withCallingCodeInput("999")
                .withNumberInput("12")
                .withPhoneNumberInfo(unknown)
                .expectedLength,
        )
        assertFalse(form.withCountry("GB").isTooShort)
    }
}
