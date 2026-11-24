package dev.kyralabs.pingogram.link

import androidx.compose.ui.platform.UriHandler
import dev.kyralabs.pingogram.core.ui.link.openUnchanged
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Links tapped in the app: Telegram's open in Pingogram, every other one in the app the system picks, without its
 * tracking parameters while the setting is on (`REQUIREMENTS.md` FR-X8), a sponsored message's always unchanged.
 */
class LinkUriHandlerTest {
    private val inApp = mutableListOf<String>()
    private val platform =
        object : UriHandler {
            val opened = mutableListOf<String>()

            override fun openUri(uri: String) {
                opened += uri
            }
        }
    private val handler = LinkUriHandler(platform) { inApp += it }

    @Test
    fun `Telegram links open in the app`() {
        handler.openUri("https://t.me/durov")
        handler.openUri("tg://resolve?domain=durov")

        assertEquals(listOf("https://t.me/durov", "tg://resolve?domain=durov"), inApp)
        assertEquals(emptyList<String>(), platform.opened)
    }

    @Test
    fun `other links go to the system`() {
        handler.openUri("https://telegram.org/faq")
        handler.openUri("mailto:someone@example.com")

        assertEquals(emptyList<String>(), inApp)
        assertEquals(listOf("https://telegram.org/faq", "mailto:someone@example.com"), platform.opened)
    }

    @Test
    fun `with link cleaning on, a link opens without its tracking parameters`() {
        val cleaning = LinkUriHandler(platform, cleansLinks = { true }) { inApp += it }

        cleaning.openUri("https://example.com/a?utm_source=news&id=1")

        assertEquals(listOf("https://example.com/a?id=1"), platform.opened)
    }

    @Test
    fun `with link cleaning off, a link opens as written`() {
        val keeping = LinkUriHandler(platform, cleansLinks = { false }) { inApp += it }

        keeping.openUri("https://example.com/a?utm_source=news&id=1")

        assertEquals(listOf("https://example.com/a?utm_source=news&id=1"), platform.opened)
    }

    @Test
    fun `a link opened unchanged, as a sponsored message's, keeps everything with cleaning on`() {
        val cleaning = LinkUriHandler(platform, cleansLinks = { true }) { inApp += it }

        cleaning.openUnchanged("https://sponsor.example/?utm_campaign=ad")

        assertEquals(listOf("https://sponsor.example/?utm_campaign=ad"), platform.opened)
    }
}
