package dev.kyralabs.pingogram.feature.media.story

import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

/**
 * The stories viewer with a hardware keyboard (`docs/DESIGN_SYSTEM.md` §9 "Keyboard & mouse"): the stories take the
 * focus as they show, so the keys work at once. While they hold it themselves (not the reply field or a button),
 * Right shows the next story and Left the previous one (mirrored in right-to-left layouts, as the tap zones are), and
 * Space pauses or resumes ([isPaused], [StoryViewerEvent.Hold]); Esc closes the viewer ([onClose]) wherever the focus
 * is in it.
 */
@Composable
internal fun Modifier.storyKeys(
    isPaused: Boolean,
    onEvent: (StoryViewerEvent) -> Unit,
    onClose: () -> Unit,
): Modifier {
    val focus = remember { FocusRequester() }
    val owner = remember { FocusHolder() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val currentIsPaused by rememberUpdatedState(isPaused)
    val currentOnEvent by rememberUpdatedState(onEvent)
    val currentOnClose by rememberUpdatedState(onClose)
    LaunchedEffect(focus) { focus.requestFocus() }
    val handler =
        remember(owner, rtl) {
            { event: KeyEvent ->
                val down = event.type == KeyEventType.KeyDown
                val ownKey = owner.isFocused
                when {
                    event.key == Key.Escape -> {
                        if (down) currentOnClose()
                        true
                    }

                    ownKey && (event.key == Key.DirectionRight || event.key == Key.DirectionLeft) -> {
                        val next = (event.key == Key.DirectionRight) != rtl
                        if (down) currentOnEvent(if (next) StoryViewerEvent.Next else StoryViewerEvent.Previous)
                        true
                    }

                    ownKey && event.key == Key.Spacebar -> {
                        if (down) currentOnEvent(StoryViewerEvent.Hold(!currentIsPaused))
                        true
                    }

                    else -> {
                        false
                    }
                }
            }
        }
    return onKeyEvent(handler)
        .onFocusChanged { owner.isFocused = it.isFocused }
        .focusRequester(focus)
        .focusable()
}

/** Whether the stories themselves have the focus, rather than something in them; read in key callbacks only. */
private class FocusHolder(
    var isFocused: Boolean = false,
)
