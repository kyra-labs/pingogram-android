package dev.kyralabs.pingogram.link

import androidx.compose.ui.platform.UriHandler
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Links tapped in the app: Telegram's open in Pingogram, every other one in the app the system picks. */
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
}
