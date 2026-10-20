package dev.kyralabs.pingogram.core.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PushPayloadTest {
    @Test
    fun `carries every field of the push, then the sent time`() {
        val json = PushPayload.json(mapOf("p" to "dGVzdA", "loc_key" to "MESSAGE_TEXT"), 1_760_000_000_123, null)

        assertEquals("""{"p":"dGVzdA","loc_key":"MESSAGE_TEXT","google.sent_time":1760000000123}""", json)
    }

    @Test
    fun `adds the notification sound when the push has one`() {
        val json = PushPayload.json(emptyMap(), 5, "default")

        assertEquals("""{"google.sent_time":5,"google.notification.sound":"default"}""", json)
    }

    @Test
    fun `escapes what JSON strings cannot hold`() {
        val json = PushPayload.json(mapOf("custom" to "{\"a\":\"b\\c\"}\n\u0001"), 0, null)

        assertEquals("""{"custom":"{\"a\":\"b\\c\"}\n\u0001","google.sent_time":0}""", json)
    }
}
