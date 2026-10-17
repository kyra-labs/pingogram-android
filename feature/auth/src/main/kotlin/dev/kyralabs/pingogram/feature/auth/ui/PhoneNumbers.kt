package dev.kyralabs.pingogram.feature.auth.ui

import dev.kyralabs.pingogram.core.domain.model.Country

/** E.164: at most 15 digits, calling code included. */
internal const val MAX_PHONE_NUMBER_DIGITS = 15

/** The shortest numbers in use: a 3-digit calling code and a 4-digit subscriber number (e.g. Niue, +683). */
internal const val MIN_PHONE_NUMBER_DIGITS = 7

/** Calling codes have up to 3 digits; Telegram lists some with a regional prefix, e.g. `1684`. */
internal const val MAX_CALLING_CODE_DIGITS = 4

/** Whether a calling code and a national number can form a valid E.164 number. */
internal fun isPlausiblePhoneNumber(
    callingCode: String,
    number: String,
): Boolean =
    callingCode.isNotEmpty() && (callingCode.length + number.length) in MIN_PHONE_NUMBER_DIGITS..MAX_PHONE_NUMBER_DIGITS

/**
 * Splits the digits of an international number into calling code and national number, by the longest calling code
 * of [countries] it starts with; null if none matches.
 */
internal fun splitInternationalNumber(
    digits: String,
    countries: List<Country>,
): Pair<String, String>? =
    countries
        .asSequence()
        .flatMap { it.callingCodes }
        .filter { it.isNotEmpty() && digits.startsWith(it) }
        .maxByOrNull { it.length }
        ?.let { code -> code to digits.removePrefix(code).take(MAX_PHONE_NUMBER_DIGITS - code.length) }

/** [current] if it has [callingCode], otherwise the first listed country that has it. */
internal fun countryForCallingCode(
    callingCode: String,
    current: Country?,
    countries: List<Country>,
): Country? =
    when {
        callingCode.isEmpty() -> null
        current != null && callingCode in current.callingCodes -> current
        else -> countries.firstOrNull { !it.isHidden && callingCode in it.callingCodes }
    }

/**
 * The grouping of TDLib's formatted national number (`7911 12- ---`): every digit or `-` becomes a `-` slot, other
 * characters are separators. Null when it has no slots.
 */
internal fun numberFormatOf(formattedNumber: String): String? =
    formattedNumber
        .map { if (it.isDigit() || it == '-') '-' else it }
        .joinToString("")
        .trimEnd()
        .takeIf { '-' in it }

/** A national number laid out by a number format, and where each digit ended up. */
internal class FormattedNumber(
    val text: String,
    /** The index in [text] of each digit of the number. */
    val digitPositions: IntArray,
) {
    /** The index in [text] for a cursor after [digitCount] digits. */
    fun transformedOffset(digitCount: Int): Int =
        if (digitCount >= digitPositions.size) text.length else digitPositions[digitCount.coerceAtLeast(0)]

    /** How many digits come before index [offset] in [text]. */
    fun originalOffset(offset: Int): Int = digitPositions.count { it < offset }
}

/**
 * Lays out [digits] by [format] ([numberFormatOf]): separators go between digits, never after the last one, and
 * digits beyond the format are appended as they are.
 */
internal fun formatNationalNumber(
    digits: String,
    format: String?,
): FormattedNumber {
    if (format.isNullOrEmpty()) return FormattedNumber(digits, IntArray(digits.length) { it })
    val text = StringBuilder()
    val positions = IntArray(digits.length)
    var next = 0
    for (slot in format) {
        if (next == digits.length) break
        if (slot == '-') {
            positions[next] = text.length
            text.append(digits[next++])
        } else {
            text.append(slot)
        }
    }
    while (next < digits.length) {
        positions[next] = text.length
        text.append(digits[next++])
    }
    return FormattedNumber(text.toString(), positions)
}

/** A loose check before asking Telegram: one `@` with something on both sides, and no spaces. */
internal fun isPlausibleEmailAddress(address: String): Boolean {
    val at = address.indexOf('@')
    return at > 0 && at == address.lastIndexOf('@') && at < address.lastIndex && address.none { it.isWhitespace() }
}
