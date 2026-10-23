package dev.kyralabs.pingogram.feature.contacts.ui

import dev.kyralabs.pingogram.core.domain.model.User
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

// How the contacts list groups and filters its contacts (ContactsViewModel), pure functions on the JVM.

/** The section of names that start with no letter (digits, emoji, symbols, and ideographs, which have no alphabet). */
internal const val OTHERS_SECTION = "#"

/** A contact's name as the list shows and sorts it: the first name, then the last name. */
internal fun User.contactName(): String = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")

/**
 * [contacts], already sorted by name, in sections by the first letter of their names in [locale]: letters the
 * language's alphabet sorts as one share a section, under the letter without its accents where that is the same
 * letter for the language ("Émile" under "E" in English), else under the letter itself ("Åsa" under "Å" in Swedish).
 * Names that start with anything but a letter of an alphabet go into [OTHERS_SECTION], the last section, as on
 * Android's contacts.
 */
internal fun contactSections(
    contacts: List<ContactItem>,
    locale: Locale,
): ImmutableList<ContactSection> {
    val collator = Collator.getInstance(locale).apply { strength = Collator.PRIMARY }
    val sections = LinkedHashMap<String, MutableList<ContactItem>>()
    val others = mutableListOf<ContactItem>()
    var current: String? = null
    for (contact in contacts) {
        val letter = firstLetter(contact.name, locale)
        if (letter == null) {
            others += contact
            continue
        }
        // Sorted names keep a section's names together; looking further back only guards against names the collator
        // sorts by something else than their first letter.
        val section =
            current?.takeIf { collator.compare(it, letter) == 0 }
                ?: sections.keys.firstOrNull { collator.compare(it, letter) == 0 }
                ?: baseLetter(letter, locale, collator)
        current = section
        sections.getOrPut(section) { mutableListOf() } += contact
    }
    if (others.isNotEmpty()) sections.getOrPut(OTHERS_SECTION) { mutableListOf() } += others
    return sections.map { (letter, items) -> ContactSection(letter, items.toImmutableList()) }.toImmutableList()
}

/**
 * Whether contact [user] matches what the user typed into the search field: every word of [query] starts a word of
 * the name, ignoring case and accents; or the query (with or without `@`) starts one of the usernames; or its digits,
 * three or more, are part of the phone number.
 */
internal fun matches(
    user: User,
    query: String,
    locale: Locale,
): Boolean {
    val words = query.searchWords(locale)
    val nameWords = user.contactName().searchWords(locale)
    val username = query.trim().removePrefix("@").lowercase(locale)
    val digits = query.filter(Char::isDigit)
    val isNumber = query.all { it.isDigit() || it in PHONE_SEPARATORS }
    return words.all { word -> nameWords.any { it.startsWith(word) } } ||
        (username.isNotEmpty() && user.usernames.any { it.lowercase(locale).startsWith(username) }) ||
        (isNumber && digits.length >= MIN_PHONE_DIGITS && digits in user.phoneNumber)
}

/** The first letter of [name] in upper case, if it starts with a letter of an alphabet. */
private fun firstLetter(
    name: String,
    locale: Locale,
): String? {
    val letter =
        name
            .trim()
            .takeIf(String::isNotEmpty)
            ?.codePointAt(0)
            ?.takeIf { Character.isLetter(it) && !Character.isIdeographic(it) }
    return letter?.let { String(Character.toChars(it)).uppercase(locale) }
}

/** [letter] without its accents, if [locale]'s alphabet sorts both as one letter; else [letter] itself. */
private fun baseLetter(
    letter: String,
    locale: Locale,
    collator: Collator,
): String {
    val base = letter.withoutAccents().uppercase(locale)
    return if (base.isNotEmpty() && collator.compare(base, letter) == 0) base else letter
}

/** The words of this text in lower case and without accents, for matching as the user types. */
private fun String.searchWords(locale: Locale): List<String> =
    withoutAccents().lowercase(locale).split(WHITESPACE).filter(String::isNotEmpty)

private fun String.withoutAccents(): String = Normalizer.normalize(this, Normalizer.Form.NFD).replace(MARKS, "")

private val WHITESPACE = Regex("""\s+""")

private val MARKS = Regex("""\p{Mn}+""")

/** What people type between the digits of a phone number. */
private const val PHONE_SEPARATORS = "+ -()."

/** Fewer digits would match too many numbers. */
private const val MIN_PHONE_DIGITS = 3
