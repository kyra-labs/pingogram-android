package dev.kyralabs.pingogram.core.ui.phone

// Phone number entry, shared by sign-in and changing the number in Settings (`REQUIREMENTS.md` FR-A2, FR-S1).

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import dev.kyralabs.pingogram.core.domain.model.Country
import dev.kyralabs.pingogram.core.domain.model.isTestPhoneNumber

/** E.164: at most 15 digits, calling code included. */
const val MAX_PHONE_NUMBER_DIGITS = 15

/** The shortest numbers in use: a 3-digit calling code and a 4-digit subscriber number (e.g. Niue, +683). */
const val MIN_PHONE_NUMBER_DIGITS = 7

/** Calling codes have up to 3 digits; Telegram lists some with a regional prefix, e.g. `1684`. */
const val MAX_CALLING_CODE_DIGITS = 4

/** Whether a calling code and a national number can form a valid E.164 number. */
fun isPlausiblePhoneNumber(
    callingCode: String,
    number: String,
): Boolean =
    callingCode.isNotEmpty() && (callingCode.length + number.length) in MIN_PHONE_NUMBER_DIGITS..MAX_PHONE_NUMBER_DIGITS

/**
 * Splits the digits of an international number into calling code and national number, by the longest calling code
 * of [countries] it starts with; null if none matches. A test account's number, whose +999 no country has, splits
 * after it (`REQUIREMENTS.md` FR-A9), so it can be pasted as one.
 */
fun splitInternationalNumber(
    digits: String,
    countries: List<Country>,
): Pair<String, String>? {
    val code =
        countries
            .asSequence()
            .flatMap { it.callingCodes }
            .filter { it.isNotEmpty() && digits.startsWith(it) }
            .maxByOrNull { it.length }
            ?: TEST_CALLING_CODE.takeIf { isTestPhoneNumber(digits) }
            ?: return null
    return code to digits.removePrefix(code).take(MAX_PHONE_NUMBER_DIGITS - code.length)
}

/** The calling code of Telegram's test accounts' numbers, +99966 X YYYY. */
private const val TEST_CALLING_CODE = "999"

/** [current] if it has [callingCode], otherwise the first listed country that has it. */
fun countryForCallingCode(
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
fun numberFormatOf(formattedNumber: String): String? =
    formattedNumber
        .map { if (it.isDigit() || it == '-') '-' else it }
        .joinToString("")
        .trimEnd()
        .takeIf { '-' in it }

/** A national number laid out by a number format, and where each digit ended up. */
class FormattedNumber(
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
fun formatNationalNumber(
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

/** Shows the national number grouped by TDLib's format ([numberFormatOf]) while the state keeps digits only. */
data class PhoneNumberVisualTransformation(
    private val format: String?,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formatNationalNumber(text.text, format)
        val offsets =
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int) = formatted.transformedOffset(offset)

                override fun transformedToOriginal(offset: Int) = formatted.originalOffset(offset)
            }
        return TransformedText(AnnotatedString(formatted.text), offsets)
    }
}
