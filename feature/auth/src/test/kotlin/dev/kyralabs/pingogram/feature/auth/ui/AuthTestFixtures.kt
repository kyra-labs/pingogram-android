package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import org.drinkless.tdlib.TdApi

/** A small country list and a TDLib-like `getPhoneNumberInfo` for [FakeTdlibClient]. */
internal object AuthTestFixtures {
    /** A UK mobile number: +44 7911 123456. */
    const val UK_CALLING_CODE = "44"
    const val UK_NUMBER = "7911123456"
    const val UK_INTERNATIONAL = "+447911123456"

    private class TestCountry(
        val iso: String,
        val name: String,
        val callingCode: String,
        val format: String,
        val prefixes: List<String> = listOf(""),
        val isHidden: Boolean = false,
    ) {
        fun info() = TdApi.CountryInfo(iso, name, name, "", isHidden, arrayOf(callingCode))
    }

    private val countries =
        listOf(
            TestCountry("CA", "Canada", "1", "--- --- ----", prefixes = listOf("416", "604")),
            TestCountry("DE", "Germany", "49", "---- -------"),
            TestCountry("GB", "United Kingdom", "44", "---- ------"),
            TestCountry("US", "United States", "1", "--- --- ----"),
            TestCountry("YL", "Test numbers", "999", "-- ----", isHidden = true),
        )

    fun countriesResult() = TdResult.Success(TdApi.Countries(countries.map { it.info() }.toTypedArray()))

    /** Like TDLib: the longest calling code and prefix match decides the country; the rest is formatted. */
    fun phoneNumberInfo(prefix: String): TdApi.PhoneNumberInfo {
        val digits = prefix.filter(Char::isDigit)
        val best =
            countries
                .flatMap { country -> country.prefixes.map { country to it } }
                .filter { (country, regional) -> digits.startsWith(country.callingCode + regional) }
                .maxByOrNull { (country, regional) -> country.callingCode.length + regional.length }
                ?.first
                ?: return TdApi.PhoneNumberInfo(null, "", digits, false)
        val national = digits.removePrefix(best.callingCode)
        return TdApi.PhoneNumberInfo(best.info(), best.callingCode, format(national, best.format), false)
    }

    /** TDLib's formatting: the digits laid out by the format, `-` for each digit still expected. */
    private fun format(
        digits: String,
        format: String,
    ): String {
        var next = 0
        val text =
            format.map { slot ->
                when {
                    slot != '-' -> slot
                    next < digits.length -> digits[next++]
                    else -> '-'
                }
            }
        return text.joinToString("") + digits.drop(next)
    }

    fun FakeTdlibClient.answerCountryRequests(countryCode: String = "GB") {
        on(TdApi.GetCountries::class, countriesResult())
        on(TdApi.GetCountryCode::class, TdResult.Success(TdApi.Text(countryCode)))
        on(TdApi.GetPhoneNumberInfo::class) { TdResult.Success(phoneNumberInfo(it.phoneNumberPrefix)) }
    }

    fun ok() = TdResult.Success(TdApi.Ok())
}
