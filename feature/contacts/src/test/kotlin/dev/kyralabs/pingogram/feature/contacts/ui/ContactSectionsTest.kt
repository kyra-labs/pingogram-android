package dev.kyralabs.pingogram.feature.contacts.ui

import dev.kyralabs.pingogram.core.ui.text.UiText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Locale

/** [contactSections] and [matches]: letters by the language's alphabet, the others last, and search's matching. */
class ContactSectionsTest {
    private fun item(
        id: Long,
        name: String,
    ) = ContactItem(id, name, photo = null, isOnline = false, status = UiText.Plain(""))

    private fun sections(
        locale: Locale,
        vararg names: String,
    ) = contactSections(names.mapIndexed { index, name -> item(index.toLong(), name) }, locale)
        .map { section -> section.letter to section.contacts.map { it.name } }

    @Test
    fun `names share the section of their first letter, whatever its case`() {
        assertEquals(
            listOf("A" to listOf("ada", "Alan"), "B" to listOf("Bea")),
            sections(Locale.US, "ada", "Alan", "Bea"),
        )
    }

    @Test
    fun `an accented letter joins its base letter where the language sorts them as one`() {
        assertEquals(
            listOf("E" to listOf("Edith", "Émile", "Eve")),
            sections(Locale.US, "Edith", "Émile", "Eve"),
        )
    }

    @Test
    fun `a letter of its own in the language keeps its own section`() {
        assertEquals(
            listOf("A" to listOf("Anna"), "Z" to listOf("Zoe"), "Å" to listOf("Åsa")),
            sections(Locale.forLanguageTag("sv-SE"), "Anna", "Zoe", "Åsa"),
        )
    }

    @Test
    fun `names that start with no letter of an alphabet come last, under #`() {
        assertEquals(
            listOf("B" to listOf("Bea"), "#" to listOf("42 Club", "😀 Smile", "王伟")),
            sections(Locale.US, "42 Club", "😀 Smile", "Bea", "王伟"),
        )
    }

    @Test
    fun `other scripts get sections of their letters`() {
        assertEquals(
            listOf("Д" to listOf("Дмитрий", "дарья")),
            sections(Locale.forLanguageTag("ru-RU"), "Дмитрий", "дарья"),
        )
    }

    @Test
    fun `every word of the query starts a word of the name`() {
        val grace = contact(1, "Grace", "Hopper")

        assertTrue(matches(grace, "gra", Locale.US))
        assertTrue(matches(grace, " hopper  grace ", Locale.US))
        assertFalse(matches(grace, "race", Locale.US))
        assertFalse(matches(grace, "grace turing", Locale.US))
    }

    @Test
    fun `case and accents do not matter`() {
        assertTrue(matches(contact(1, "Émile"), "EMI", Locale.US))
        assertTrue(matches(contact(1, "Emile"), "émi", Locale.US))
    }

    @Test
    fun `a username matches by its start, with or without @`() {
        val ada = contact(1, "Ada", usernames = listOf("Countess"))

        assertTrue(matches(ada, "count", Locale.US))
        assertTrue(matches(ada, "@coun", Locale.US))
        assertFalse(matches(ada, "tess", Locale.US))
    }

    @Test
    fun `three or more digits match within the phone number`() {
        val bea = contact(1, "Bea", phoneNumber = "447911123456")

        assertTrue(matches(bea, "+44 7911", Locale.US))
        assertTrue(matches(bea, "123", Locale.US))
        assertFalse(matches(bea, "12", Locale.US))
        assertFalse(matches(bea, "999", Locale.US))
    }
}
