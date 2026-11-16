package dev.kyralabs.pingogram.feature.media.ui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The viewer's chrome: a tap shows or hides it, except while TalkBack explores by touch (board 25). */
class ChromeStateTest {
    @Test
    fun `a tap hides the chrome and shows it again`() {
        val chrome = ChromeState()
        chrome.toggle()
        assertFalse(chrome.visible)
        chrome.toggle()
        assertTrue(chrome.visible)
    }

    @Test
    fun `with TalkBack the chrome always shows and taps leave it`() {
        val chrome = ChromeState(visible = false)
        chrome.keptShown = true
        assertTrue(chrome.visible)
        chrome.toggle()
        assertTrue(chrome.visible)

        chrome.keptShown = false
        assertFalse(chrome.visible)
    }
}
