package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class TelegramLinksTest {
    @ParameterizedTest
    @ValueSource(
        strings = [
            "tg://resolve?domain=durov",
            "TG:resolve?domain=durov",
            "https://t.me/durov",
            "http://t.me/durov",
            "HTTPS://T.ME/durov",
            "https://telegram.me/durov",
            "https://telegram.dog/durov",
            "https://durov.t.me",
            "https://www.t.me/durov",
            "https://t.me./durov",
            "https://t.me:443/durov",
            "https://t.me",
            "https://t.me?start=1",
            " https://t.me/+15551234567 ",
        ],
    )
    fun `Telegram's links open in the app`(url: String) {
        assertTrue(isTelegramLink(url))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "https://telegram.org/tos",
            "https://telegra.ph/page",
            "https://example.com/t.me/durov",
            "https://t.me.example.com/durov",
            "https://evilt.me/durov",
            "https://t.me@example.com/durov",
            "mailto:someone@t.me",
            "t.me/durov",
            "ftp://t.me/durov",
            "https:t.me/durov",
            "",
        ],
    )
    fun `other links do not`(url: String) {
        assertFalse(isTelegramLink(url))
    }

    @Test
    fun `web links are http and https ones`() {
        assertTrue(isWebLink("https://t.me/durov"))
        assertTrue(isWebLink("HTTP://example.com"))
        assertFalse(isWebLink("tg://resolve?domain=durov"))
        assertFalse(isWebLink("t.me/durov"))
    }
}
