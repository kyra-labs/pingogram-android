package dev.kyralabs.pingogram.core.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TelegramServersTest {
    @Test
    fun `test accounts' numbers sign in on the test servers, typed in any way`() {
        listOf("+9996611234", "+99966 2 1234", "+999 66 3 0000", "9996629999", "+999 (66) 2-1234").forEach {
            assertEquals(TelegramServers.Test, telegramServersFor(it), it)
        }
    }

    @Test
    fun `every other number signs in on the production servers`() {
        listOf(
            "+447911123456",
            // Data centers other than 1 to 3.
            "+9996601234",
            "+9996641234",
            // Too short or too long.
            "+999661234",
            "+99966123456",
            // Another calling code before the digits, or letters among them.
            "+19996611234",
            "+99966a11234",
            "",
        ).forEach { assertEquals(TelegramServers.Production, telegramServersFor(it), it) }
    }
}
