package dev.kyralabs.pingogram.core.domain.model

/**
 * Which Enter sends the composer's message (`docs/DESIGN_SYSTEM.md` §9, `docs/audit/accessibility.md` A2):
 * [hardwareKeyboard], a physical keyboard's Enter (Shift+Enter and Ctrl+Enter then start a new line; off, Ctrl+Enter
 * sends and Enter starts a new line); [onScreenKeyboard], the on-screen keyboard's, whose action key then reads "Send".
 * On a hardware keyboard and off on the screen by default: a physical keyboard has Shift+Enter for a new line, the
 * on-screen one has no such key.
 */
data class SendKeys(
    val hardwareKeyboard: Boolean = true,
    val onScreenKeyboard: Boolean = false,
)
