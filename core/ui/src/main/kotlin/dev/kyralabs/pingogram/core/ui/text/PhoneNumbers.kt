package dev.kyralabs.pingogram.core.ui.text

import android.telephony.PhoneNumberUtils
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * A phone number as Telegram sends it (its digits, with or without `+`), formatted with its international prefix by
 * Android's rules for its calling code ("+44 7911 123456"), [country] (an ISO 3166 code, usually the locale's) breaking
 * ties; as Telegram sent it, with `+`, if it cannot be formatted. Show it with [numericContent], so it reads left to
 * right in right-to-left layouts too, and through [phoneNumberToShow], which hides it when the user asked.
 */
fun formatPhoneNumber(
    number: String,
    country: String,
): String {
    if (number.isBlank()) return number
    val international = if (number.startsWith("+")) number else "+$number"
    return PhoneNumberUtils.formatNumber(international, country) ?: international
}

/**
 * Whether phone numbers are hidden on screen (Privacy and security → "Hide phone numbers"), provided by the app's
 * activity; shown by default, as in previews and tests.
 */
val LocalPhoneNumbersHidden: ProvidableCompositionLocal<Boolean> = staticCompositionLocalOf { false }

/** [formatted] (a number from [formatPhoneNumber]) as the screen shows it: masked by [maskPhoneNumber] while hidden. */
@Composable
@ReadOnlyComposable
fun phoneNumberToShow(formatted: String): String =
    if (LocalPhoneNumbersHidden.current) maskPhoneNumber(formatted) else formatted

/**
 * [formatted] with every digit after its calling code as a dot, its spaces, dashes and brackets kept, so it keeps its
 * shape: "+44 7911 123456" becomes "+44 •••• ••••••". The calling code is the first group of up to three digits after
 * `+`; a number without groups, or without `+`, has every digit masked.
 */
fun maskPhoneNumber(formatted: String): String {
    val callingCode = CALLING_CODE.find(formatted)?.value?.length ?: 0
    return buildString(formatted.length) {
        formatted.forEachIndexed { index, char -> append(if (index >= callingCode && char.isDigit()) MASK else char) }
    }
}

/** "+44" of "+44 7911 123456": `+`, one to three digits, and a separator after them. */
private val CALLING_CODE = Regex("""^\+\d{1,3}(?=[\s\-(])""")

private const val MASK = '•'
