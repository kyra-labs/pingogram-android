package dev.kyralabs.pingogram.core.tdlib

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class TdErrorTest {
    @Test
    fun `retry delay is read from TDLib's Too Many Requests message`() {
        val error = TdError(429, "Too Many Requests: retry after 42")

        assertEquals(42.seconds, error.retryAfter)
    }

    @Test
    fun `retry delay is read from a raw FLOOD_WAIT error`() {
        val error = TdError(420, "FLOOD_WAIT_300")

        assertEquals(300.seconds, error.retryAfter)
    }

    @Test
    fun `other errors have no retry delay`() {
        val error = TdError(400, "PHONE_NUMBER_INVALID")

        assertNull(error.retryAfter)
    }
}
