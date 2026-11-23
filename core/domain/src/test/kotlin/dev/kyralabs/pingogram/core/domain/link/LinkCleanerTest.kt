package dev.kyralabs.pingogram.core.domain.link

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** [LinkCleaner] (`REQUIREMENTS.md` FR-X8): tracking parameters go, everything else stays as written. */
class LinkCleanerTest {
    @Test
    fun `removes every utm parameter and the listed click ids, keeping the rest in order`() {
        assertEquals(
            "https://example.com/a?id=7&lang=en#top",
            LinkCleaner.cleanUrl(
                "https://example.com/a?utm_source=x&id=7&fbclid=abc&utm_campaign=y&lang=en&gclid=1#top",
            ),
        )
    }

    @Test
    fun `drops the question mark when no parameter is left, keeping the fragment`() {
        assertEquals(
            "https://example.com/page#part",
            LinkCleaner.cleanUrl("https://example.com/page?utm_medium=a#part"),
        )
        assertEquals("http://example.com/", LinkCleaner.cleanUrl("http://example.com/?msclkid=1&yclid=2"))
    }

    @Test
    fun `matches names without regard to case`() {
        assertEquals("https://example.com/?q=1", LinkCleaner.cleanUrl("https://example.com/?UTM_Source=x&q=1&FBCLID=y"))
    }

    @Test
    fun `removes si only from YouTube and Spotify links`() {
        assertEquals("https://youtu.be/abc?t=42", LinkCleaner.cleanUrl("https://youtu.be/abc?si=XYZ&t=42"))
        assertEquals(
            "https://www.youtube.com/watch?v=abc",
            LinkCleaner.cleanUrl("https://www.youtube.com/watch?v=abc&si=XYZ"),
        )
        assertEquals(
            "https://open.spotify.com/track/1",
            LinkCleaner.cleanUrl("https://open.spotify.com/track/1?si=XYZ"),
        )
        assertEquals("https://example.com/?si=keep", LinkCleaner.cleanUrl("https://example.com/?si=keep"))
    }

    @Test
    fun `leaves a link without tracking parameters, and any other scheme, exactly as it is`() {
        val links =
            listOf(
                "https://example.com/search?q=a%20b&page=2",
                "https://example.com/path#utm_source=fragment",
                "tg://resolve?domain=pingogram&utm_source=x",
                "mailto:someone@example.com?subject=utm_source",
            )
        links.forEach { assertSame(it, LinkCleaner.cleanUrl(it)) }
    }

    @Test
    fun `cleans a link written out in a text and moves the formatting after it`() {
        val link = "https://example.com/a?utm_source=news&id=1"
        val text =
            FormattedText(
                "See $link now, bold",
                persistentListOf(
                    TextEntity(offset = 4, length = link.length, type = TextEntityType.Url),
                    TextEntity(offset = 4 + link.length + 6, length = 4, type = TextEntityType.Bold),
                ),
            )

        val cleaned = LinkCleaner.clean(text)

        val clean = "https://example.com/a?id=1"
        assertEquals("See $clean now, bold", cleaned.text)
        assertEquals(TextEntity(4, clean.length, TextEntityType.Url), cleaned.entities[0])
        assertEquals("bold", cleaned.text.substring(cleaned.entities[1].offset, cleaned.entities[1].offset + 4))
    }

    @Test
    fun `finds links typed without an entity, leaving the punctuation after them`() {
        val text = FormattedText("Read (www.example.com/?fbclid=1). Also https://x.org/?gclid=2.", persistentListOf())

        assertEquals("Read (www.example.com/). Also https://x.org/.", LinkCleaner.clean(text).text)
    }

    @Test
    fun `an entity around a link shrinks with it`() {
        val link = "https://example.com/?utm_source=a"
        val text = FormattedText(link, persistentListOf(TextEntity(0, link.length, TextEntityType.Italic)))

        val cleaned = LinkCleaner.clean(text)

        assertEquals(TextEntity(0, "https://example.com/".length, TextEntityType.Italic), cleaned.entities.single())
    }

    @Test
    fun `cleans the target of a text link without changing its text`() {
        val text =
            FormattedText(
                "our site",
                persistentListOf(TextEntity(0, 8, TextEntityType.TextUrl("https://example.com/?utm_source=a&p=1"))),
            )

        val cleaned = LinkCleaner.clean(text)

        assertEquals("our site", cleaned.text)
        assertEquals(TextEntityType.TextUrl("https://example.com/?p=1"), cleaned.entities.single().type)
    }

    @Test
    fun `a text with nothing to clean is returned as it is, and cleaning twice changes nothing more`() {
        val plain = FormattedText("Hello https://example.com/?q=1 and emoji 😀", persistentListOf())
        assertSame(plain, LinkCleaner.clean(plain))

        val once = LinkCleaner.clean(FormattedText("😀 https://example.com/?utm_id=1&q=2 😀", persistentListOf()))
        assertEquals("😀 https://example.com/?q=2 😀", once.text)
        assertSame(once, LinkCleaner.clean(once))
    }
}
