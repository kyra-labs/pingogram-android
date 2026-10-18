package dev.kyralabs.pingogram.core.domain.model

/** A country for phone number entry, from TDLib's country list (`REQUIREMENTS.md` FR-A2). */
data class Country(
    /** ISO 3166-1 alpha-2 code, e.g. `GB`. */
    val isoCode: String,
    /** Name in the user's language. */
    val name: String,
    val englishName: String,
    val flagEmoji: String,
    /** Calling codes without `+`, the main one first, e.g. `44`. */
    val callingCodes: List<String>,
    /** Not offered in the country list, but phone numbers can still belong to it. */
    val isHidden: Boolean,
)

/** What TDLib knows about a phone number from its first digits, for live formatting (`getPhoneNumberInfo`). */
data class PhoneNumberInfo(
    /** The country the number belongs to, if known. */
    val country: Country?,
    /** The calling code part of the number, without `+`; empty if unknown. */
    val callingCode: String,
    /**
     * The rest of the number formatted for its country, with `-` for each digit still expected, e.g. `7911 12- ---`.
     * More digits than that may follow.
     */
    val formattedNumber: String,
    /** The number was bought on fragment.com and is not tied to a SIM card. */
    val isAnonymous: Boolean,
) {
    override fun toString() = "PhoneNumberInfo(country=${country?.isoCode}, callingCode=$callingCode, …)"
}

/**
 * The flag emoji of an ISO 3166-1 alpha-2 country code, made of two regional indicator symbols; empty for anything
 * that is not two ASCII letters.
 */
fun flagEmoji(isoCode: String): String {
    if (isoCode.length != 2 || !isoCode.all { it.uppercaseChar() in 'A'..'Z' }) return ""
    return buildString {
        isoCode.uppercase().forEach { letter -> appendCodePoint(REGIONAL_INDICATOR_A + (letter - 'A')) }
    }
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
