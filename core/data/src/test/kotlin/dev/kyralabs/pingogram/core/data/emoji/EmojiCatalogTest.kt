package dev.kyralabs.pingogram.core.data.emoji

import dev.kyralabs.pingogram.core.domain.model.EmojiCategory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [parseEmojiCatalog]: the bundled catalog's format, unknown groups and emoji the device cannot draw. */
class EmojiCatalogTest {
    @Test
    fun `groups keep the catalog's order, with their emoji`() {
        val catalog = parseEmojiCatalog("smileys_emotion\t😀 😃\nflags\t🏁 🇺🇦\n") { true }

        assertEquals(listOf(EmojiCategory.SmileysAndEmotion, EmojiCategory.Flags), catalog.map { it.category })
        assertEquals(listOf("😀", "😃"), catalog[0].emoji)
        assertEquals(listOf("🏁", "🇺🇦"), catalog[1].emoji)
    }

    @Test
    fun `emoji the device cannot draw and unknown groups are left out`() {
        val catalog = parseEmojiCatalog("component\t🏻\nanimals_nature\t🐶 🫎\n") { it != "🫎" }

        assertEquals(listOf(EmojiCategory.AnimalsAndNature), catalog.map { it.category })
        assertEquals(listOf("🐶"), catalog.single().emoji)
    }
}
