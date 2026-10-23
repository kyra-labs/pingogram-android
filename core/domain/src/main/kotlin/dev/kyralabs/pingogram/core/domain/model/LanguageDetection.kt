package dev.kyralabs.pingogram.core.domain.model

import java.util.Locale

/**
 * What the device found a text's language to be (`DeviceLanguageDetector`), on the device itself: nothing of the text
 * leaves it to find out (`REQUIREMENTS.md` §6.2).
 */
sealed interface LanguageDetection {
    /** The text is in [languageTag] (IETF BCP 47, "es", "pt-BR"), as sure as the detector needs to be. */
    data class Detected(
        val languageTag: String,
    ) : LanguageDetection

    /** The detector could not tell: too short a text, several languages, no letters. */
    data object Undetermined : LanguageDetection

    /** The device cannot detect languages (before Android 10). */
    data object Unavailable : LanguageDetection
}

/**
 * Whether [languageTag] is the same language as one of [languages] (IETF BCP 47 tags), by their language subtag:
 * "pt-BR" reads as "pt", "en-GB" as "en". Old ISO 639 codes count as their new ones ("iw" is "he", "in" is "id"), as
 * `Locale` maps them.
 */
fun isLanguageAmong(
    languageTag: String,
    languages: Collection<String>,
): Boolean {
    val language = primaryLanguageOf(languageTag)
    return language.isNotEmpty() && languages.any { primaryLanguageOf(it) == language }
}

/**
 * The language subtag of [languageTag] in lower case ("pt" for "pt-BR"), with today's ISO 639 codes ("he", never "iw",
 * which Android's `Locale.getLanguage` still gives); empty if it names no language.
 */
fun primaryLanguageOf(languageTag: String): String {
    val locale = Locale.forLanguageTag(languageTag)
    return if (locale.language.isEmpty()) "" else locale.toLanguageTag().substringBefore('-').lowercase(Locale.ROOT)
}
