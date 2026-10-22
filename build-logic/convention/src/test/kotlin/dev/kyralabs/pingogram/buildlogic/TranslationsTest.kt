package dev.kyralabs.pingogram.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class TranslationsTest {
    @TempDir
    lateinit var root: File

    private fun strings(
        module: String,
        directory: String,
        vararg entries: String,
    ): File =
        File(root, "$module/src/main/res/$directory/strings.xml").apply {
            parentFile.mkdirs()
            writeText("<resources>\n${entries.joinToString("\n")}\n</resources>\n")
        }

    private fun string(
        name: String,
        translatable: Boolean = true,
    ) = """<string name="$name"${if (translatable) "" else " translatable=\"false\""}>x</string>"""

    private fun plurals(name: String) = """<plurals name="$name"><item quantity="other">x</item></plurals>"""

    private val files get() = root.walkTopDown().filter { it.name == "strings.xml" }.toList()

    @Test
    fun `coverage counts translatable strings and plurals of every module together`() {
        strings("a", "values", string("one"), string("two"), plurals("three"), string("brand", translatable = false))
        strings("b", "values", string("four"))
        strings("a", "values-de", string("one"), plurals("three"))
        strings("b", "values-de", string("four"))
        strings("a", "values-fi", string("one"))

        val coverage = Translations.coverage(files)

        assertEquals(Coverage(translated = 3, total = 4), coverage["de"])
        assertEquals(Coverage(translated = 1, total = 4), coverage["fi"])
    }

    @Test
    fun `stale strings a translation keeps and other qualifiers do not count`() {
        strings("a", "values", string("one"), string("two"))
        strings("a", "values-de", string("one"), string("gone"))
        strings("a", "values-night", string("one"))

        val coverage = Translations.coverage(files)

        assertEquals(mapOf("de" to Coverage(1, 2)), coverage)
    }

    @Test
    fun `English ships first, then every language at 80 percent or more`() {
        val coverage =
            mapOf(
                "de" to Coverage(80, 100),
                "fi" to Coverage(79, 100),
                "ar" to Coverage(100, 100),
                "pt-BR" to Coverage(95, 100),
            )

        assertEquals(listOf("en", "ar", "de", "pt-BR"), Translations.shipped(coverage))
    }

    @Test
    fun `resource qualifiers and language tags convert both ways`() {
        assertEquals("de", Translations.languageTagOf("de"))
        assertEquals("pt-BR", Translations.languageTagOf("pt-rBR"))
        assertEquals("sr-Latn", Translations.languageTagOf("b+sr+Latn"))
        assertNull(Translations.languageTagOf("night"))
        assertNull(Translations.languageTagOf("v31"))
        assertNull(Translations.languageTagOf("sw600dp"))
        assertEquals("pt-rBR", Translations.qualifierOf("pt-BR"))
        assertEquals("b+sr+Latn", Translations.qualifierOf("sr-Latn"))
        assertEquals("fil", Translations.qualifierOf("fil"))
    }

    @Test
    fun `the locale config lists the shipped languages`() {
        val xml = Translations.localeConfig(listOf("en", "de"))

        assertTrue("""<locale android:name="en" />""" in xml)
        assertTrue("""<locale android:name="de" />""" in xml)
    }
}
