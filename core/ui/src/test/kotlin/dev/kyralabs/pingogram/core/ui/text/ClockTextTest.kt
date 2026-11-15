package dev.kyralabs.pingogram.core.ui.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

/** Countdowns on screen as a clock: `m:ss` under an hour, `h:mm:ss` above (board 23). */
class ClockTextTest {
    @Test
    fun `under an hour a countdown is minutes and seconds`() {
        assertEquals("0:42", clockText(42, Locale.US))
        assertEquals("4:59", clockText(299, Locale.US))
        assertEquals("59:59", clockText(3_599, Locale.US))
    }

    @Test
    fun `from an hour on it adds the hours`() {
        assertEquals("1:00:00", clockText(3_600, Locale.US))
        assertEquals("1:04:59", clockText(3_899, Locale.US))
    }

    @Test
    fun `a countdown never goes below zero`() {
        assertEquals("0:00", clockText(-5, Locale.US))
    }

    @Test
    fun `it uses the language's digits`() {
        assertEquals("٤:٥٩", clockText(299, Locale.forLanguageTag("ar-EG")))
    }
}
