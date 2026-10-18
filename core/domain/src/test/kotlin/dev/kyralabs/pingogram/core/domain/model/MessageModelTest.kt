package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class MessageModelTest {
    @Test
    fun `waveforms with the same bytes are equal`() {
        val waveform = Waveform(byteArrayOf(1, 2, 31))

        assertEquals(Waveform(byteArrayOf(1, 2, 31)), waveform)
        assertEquals(Waveform(byteArrayOf(1, 2, 31)).hashCode(), waveform.hashCode())
        assertNotEquals(Waveform(byteArrayOf(1, 2, 30)), waveform)
    }

    @Test
    fun `entity offsets index a Kotlin string, also after emoji`() {
        val text = "👋 Hi @bob"
        val mention = TextEntity(offset = 6, length = 4, type = TextEntityType.Mention)

        assertEquals("@bob", text.substring(mention.offset, mention.offset + mention.length))
    }

    @Test
    fun `toString leaves out texts, names, phone numbers, places and links`() {
        val location = Location(51.501, -0.142)
        val printed =
            listOf(
                FormattedText(
                    "secret plans",
                    persistentListOf(TextEntity(0, 6, TextEntityType.TextUrl("https://x.org"))),
                ),
                Contact("+99966 2 1234", "Alice", "Smith", userId = 7),
                location,
                Venue(location, "Palace", "Westminster"),
                Document("passport-scan.pdf", "application/pdf", null, null, FileRef(1, 10)),
                LinkPreview(
                    url = "https://example.org/private",
                    displayUrl = "example.org/private",
                    siteName = "Example",
                    title = "Private page",
                    description = FormattedText.Empty,
                    photo = null,
                    showLargeMedia = false,
                    showAboveText = false,
                ),
                MessageOrigin.HiddenUser("Bob"),
                ServiceAction.Custom("Carol did something"),
            ).joinToString()

        val leaked =
            listOf(
                "secret",
                "x.org",
                "99966",
                "Alice",
                "Smith",
                "51.5",
                "Palace",
                "passport",
                "example",
                "Bob",
                "Carol",
            ).filter { it in printed }
        assertEquals(emptyList<String>(), leaked, printed)
    }
}
