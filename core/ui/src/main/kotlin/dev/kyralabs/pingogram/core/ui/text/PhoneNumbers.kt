package dev.kyralabs.pingogram.core.ui.text

import android.telephony.PhoneNumberUtils

/**
 * A phone number as Telegram sends it (its digits, with or without `+`), formatted with its international prefix by
 * Android's rules for its calling code ("+44 7911 123456"), [country] (an ISO 3166 code, usually the locale's) breaking
 * ties; as Telegram sent it, with `+`, if it cannot be formatted. Show it with [numericContent], so it reads left to
 * right in right-to-left layouts too.
 */
fun formatPhoneNumber(
    number: String,
    country: String,
): String {
    if (number.isBlank()) return number
    val international = if (number.startsWith("+")) number else "+$number"
    return PhoneNumberUtils.formatNumber(international, country) ?: international
}
