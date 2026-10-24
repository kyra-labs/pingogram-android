package dev.kyralabs.pingogram.core.ui.phone

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.PhoneNumberInfo

/**
 * A phone number being typed (`REQUIREMENTS.md` FR-A2): the countries TDLib lists, the calling code and national
 * number as digits, the country they belong to, TDLib's format for the number, the field's error, and the country
 * picker. Sign-in and changing the number in Settings keep one in their view models and change it with its `with*`
 * functions, asking TDLib for [PhoneNumberInfo] after each change ([withPhoneNumberInfo]).
 */
data class PhoneNumberForm(
    val countries: List<Country> = emptyList(),
    val callingCode: String = "",
    val number: String = "",
    val countryIso: String? = null,
    val numberFormat: String? = null,
    val error: DomainError? = null,
    val isPickerOpen: Boolean = false,
    val pickerQuery: String = "",
    /** The country of the IP address was looked up for this form (once, and only for an empty calling code). */
    val isCountryDetected: Boolean = false,
) {
    val country: Country? get() = countryIso?.let { iso -> countries.find { it.isoCode == iso } }

    /** The number as sent to TDLib: `+`, calling code and national number. */
    val internationalNumber: String get() = "+$callingCode$number"

    /** Whether the calling code and number can form a valid E.164 number ([isPlausiblePhoneNumber]). */
    val isPlausible: Boolean get() = isPlausiblePhoneNumber(callingCode, number)

    /** The open country picker, or null. */
    val picker: CountryPickerUiState?
        get() =
            if (isPickerOpen) CountryPickerUiState(pickerQuery, countries.matching(pickerQuery), countryIso) else null

    override fun toString() = "PhoneNumberForm(country=$countryIso, error=$error, …)"

    /**
     * The calling code field changed to [input]. More digits than any calling code are a whole international number
     * typed or pasted there, split into calling code and number.
     */
    fun withCallingCodeInput(input: String): PhoneNumberForm {
        val digits = input.filter(Char::isDigit)
        val split = if (digits.length > MAX_CALLING_CODE_DIGITS) splitInternationalNumber(digits, countries) else null
        val code = split?.first ?: digits.take(MAX_CALLING_CODE_DIGITS)
        return copy(
            callingCode = code,
            number = (split?.second ?: number).take(MAX_PHONE_NUMBER_DIGITS - code.length),
            countryIso = countryForCallingCode(code, country, countries)?.isoCode,
            error = null,
            isCountryDetected = true,
        )
    }

    /** The number field changed to [input]; one starting with `+` is a whole international number, split. */
    fun withNumberInput(input: String): PhoneNumberForm {
        val digits = input.filter(Char::isDigit)
        val split = if (input.trimStart().startsWith('+')) splitInternationalNumber(digits, countries) else null
        return if (split != null) {
            copy(
                callingCode = split.first,
                number = split.second,
                countryIso = countryForCallingCode(split.first, country, countries)?.isoCode,
                error = null,
                isCountryDetected = true,
            )
        } else {
            copy(number = digits.take(MAX_PHONE_NUMBER_DIGITS - callingCode.length), error = null)
        }
    }

    /** The country [isoCode] was picked: its main calling code, the picker closed. Unchanged for an unknown one. */
    fun withCountry(isoCode: String): PhoneNumberForm {
        val picked = countries.find { it.isoCode == isoCode } ?: return this
        val code = picked.callingCodes.firstOrNull().orEmpty()
        return copy(
            countryIso = isoCode,
            callingCode = code,
            number = number.take(MAX_PHONE_NUMBER_DIGITS - code.length),
            isPickerOpen = false,
            pickerQuery = "",
            error = null,
            isCountryDetected = true,
        )
    }

    /** Whether to ask for the country of the IP address now: not yet asked, nothing typed, the countries known. */
    val wantsDetectedCountry: Boolean get() = !isCountryDetected && callingCode.isEmpty() && countries.isNotEmpty()

    /** The country of the IP address ([isoCode]), unless a calling code was typed meanwhile. */
    fun withDetectedCountry(isoCode: String): PhoneNumberForm {
        val detected = countries.find { it.isoCode.equals(isoCode, ignoreCase = true) }
        return if (detected == null || callingCode.isNotEmpty()) {
            copy(isCountryDetected = true)
        } else {
            copy(
                isCountryDetected = true,
                countryIso = detected.isoCode,
                callingCode = detected.callingCodes.firstOrNull().orEmpty(),
            )
        }
    }

    /**
     * The number format and country for TDLib's [info] about the current number. A country the user picked stays while
     * the number is still empty (+1 is both Canada and the United States); after that the number decides.
     */
    fun withPhoneNumberInfo(info: PhoneNumberInfo): PhoneNumberForm {
        if (info.callingCode != callingCode) return copy(numberFormat = null)
        val detected = info.country?.isoCode
        val keepSelection = number.isEmpty() && country?.callingCodes?.contains(callingCode) == true
        return copy(
            numberFormat = numberFormatOf(info.formattedNumber),
            countryIso = if (detected == null || keepSelection) countryIso else detected,
        )
    }
}
