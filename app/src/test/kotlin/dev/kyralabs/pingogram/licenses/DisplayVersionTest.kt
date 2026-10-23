package dev.kyralabs.pingogram.licenses

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DisplayVersionTest {
    @Test
    fun `a commit shows by its first 10 characters`() {
        assertEquals("a94b83d67a", displayVersion("a94b83d67ab4c8397c6245e541b31a4131ea2f79"))
    }

    @Test
    fun `a self-built TDLib keeps its suffix`() {
        assertEquals("a94b83d67a-local", displayVersion("a94b83d67ab4c8397c6245e541b31a4131ea2f79-local"))
    }

    @Test
    fun `a version shows as it is`() {
        assertEquals("1.5.0-beta01", displayVersion("1.5.0-beta01"))
        assertEquals(
            "9999.0-empty-to-avoid-conflict-with-guava",
            displayVersion("9999.0-empty-to-avoid-conflict-with-guava"),
        )
    }
}
