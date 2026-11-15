package dev.kyralabs.pingogram.feature.chat.ui

import java.util.IllformedLocaleException
import java.util.Locale

/**
 * The name of the country or region with ISO 3166 code [code] ("DE") in [locale]'s language ("Germany"), or [code]
 * itself where it is no region code. `Locale.of` would be shorter, but it needs Android 16.
 */
internal fun regionName(
    code: String,
    locale: Locale,
): String =
    try {
        Locale
            .Builder()
            .setRegion(code)
            .build()
            .getDisplayCountry(locale)
            .ifEmpty { code }
    } catch (_: IllformedLocaleException) {
        code
    }
