package dev.kyralabs.pingogram.feature.chat.ui.reply

import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.TextQuote
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * What [quoteOf] quotes of a message's text from what is selected in its lifted bubble: the part found in the text,
 * where the selection says it is, trimmed and cut to Telegram's length, with the formatting a quote keeps.
 */
class QuoteSelectionTest {
    private fun text(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, entities.toList().toImmutableList())

    private fun plain(text: String) = FormattedText(text, persistentListOf())

    @Test
    fun `a part inside the text is quoted where it first occurs`() {
        assertEquals(TextQuote(plain("on"), 3), quoteOf(plain("Go on, go on"), listOf("on")))
    }

    @Test
    fun `a selection going on past the text quotes its end`() {
        val quote = quoteOf(plain("go on, go on"), listOf("go on", "9:41 AM"))

        assertEquals(TextQuote(plain("go on"), 7), quote)
    }

    @Test
    fun `a selection from the sender quotes the text's start`() {
        assertEquals(TextQuote(plain("go on"), 0), quoteOf(plain("go on, go on"), listOf("Ada", "go on")))
    }

    @Test
    fun `only the formatting a quote keeps, cut to the quote, and custom emoji only whole`() {
        val source =
            text(
                "Hello 🙂 brave world",
                TextEntity(0, 11, TextEntityType.Bold),
                TextEntity(6, 2, TextEntityType.CustomEmoji(5)),
                TextEntity(9, 5, TextEntityType.Code),
                TextEntity(15, 5, TextEntityType.TextUrl("https://example.com")),
            )

        assertEquals(
            TextQuote(
                text(
                    "llo 🙂 brave",
                    TextEntity(0, 9, TextEntityType.Bold),
                    TextEntity(4, 2, TextEntityType.CustomEmoji(5)),
                ),
                2,
            ),
            quoteOf(source, listOf("llo 🙂 brave")),
        )
        // Half of the custom emoji's fallback selected: the quote stops before it.
        assertEquals(
            listOf(TextEntity(0, 3, TextEntityType.Bold)),
            quoteOf(source, listOf("lo \uD83D"))?.text?.entities,
        )
    }

    @Test
    fun `a long selection is cut to Telegram's length, never in half an emoji`() {
        val long = "a".repeat(MAX_QUOTE_LENGTH - 1) + "🙂" + "b"

        val quote = quoteOf(plain(long), listOf(long))

        assertEquals(MAX_QUOTE_LENGTH - 1, quote?.text?.text?.length)
    }

    @Test
    fun `nothing of the text, or only white space, quotes nothing`() {
        assertNull(quoteOf(plain("Go on"), listOf("Ada", "9:41 AM")))
        assertNull(quoteOf(plain("Go on"), listOf("  ")))
        assertNull(quoteOf(plain("Go on"), emptyList()))
    }
}
