package dev.kyralabs.pingogram.core.ui.transition

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize

/**
 * Whether the endless animations of the content it is provided to stand still where they are, read where they run
 * (outside composition, as snapshot state): under the conversation's floating message menu, whose blur is drawn again
 * for every frame of what animates under it. An animated sticker or custom emoji keeps the frame it shows, typing dots
 * their brightness; they go on once it is false again. Never paused unless provided.
 */
val LocalAnimationsPaused = staticCompositionLocalOf<() -> Boolean> { NeverPaused }

/** The default of [LocalAnimationsPaused]. */
val NeverPaused: () -> Boolean = { false }

/**
 * This window as the players of animations see it: focused only while [paused] is false too, so a pause holds them
 * on their frame as losing the focus does. Not for the composition: text fields and focus keep the real window.
 */
fun WindowInfo.focusedUnless(paused: () -> Boolean): WindowInfo =
    if (paused === NeverPaused) this else PausableWindowInfo(this, paused)

private class PausableWindowInfo(
    private val window: WindowInfo,
    private val paused: () -> Boolean,
) : WindowInfo {
    override val isWindowFocused: Boolean get() = window.isWindowFocused && !paused()

    override val keyboardModifiers: PointerKeyboardModifiers get() = window.keyboardModifiers

    override val containerSize: IntSize get() = window.containerSize

    override val containerDpSize: DpSize get() = window.containerDpSize
}
