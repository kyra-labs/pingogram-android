package dev.kyralabs.pingogram.core.ui.text

import dev.kyralabs.pingogram.core.ui.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** Names and other text from people, isolated as arguments of Pingogram's strings. */
class BidiIsolationTest {
    @Test
    fun `wraps text in first-strong isolates`() {
        assertEquals("⁨שלום⁩", "שלום".bidiIsolated())
    }

    @Test
    fun `leaves empty text empty`() {
        assertEquals("", "".bidiIsolated())
    }

    @Test
    fun `isolates plain text`() {
        assertEquals(UiText.Plain("⁨Grace⁩"), UiText.Plain("Grace").bidiIsolated())
    }

    @Test
    fun `leaves Pingogram's own strings as they are`() {
        val someone = UiText.resource(R.string.preview_someone)

        assertSame(someone, someone.bidiIsolated())
    }
}
