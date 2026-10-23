package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester

/**
 * Asks the composer's text field for the focus, which opens the keyboard: when the user starts a reply or an edit
 * (`ChatEffect.FocusComposer`), not when a draft brings a reply back as the chat opens. Each [request] counts; the
 * composer focuses its field when [requests] changes, and on none at first, so a rotation does not ask again.
 */
@Stable
internal class ComposerFocusState {
    /** How often the focus was asked for. */
    var requests by mutableIntStateOf(0)
        private set

    fun request() {
        requests++
    }
}

/**
 * The composer's [ComposerFocusState]; with [requestAtStart] (a chat opened from the list with a hardware keyboard),
 * already asked once, so the field takes the focus as soon as it shows.
 */
@Composable
internal fun rememberComposerFocusState(requestAtStart: Boolean = false): ComposerFocusState =
    remember { ComposerFocusState().apply { if (requestAtStart) request() } }

/** A focus requester for the text field, which takes the focus each time [focus] is asked for it (none at first). */
@Composable
internal fun rememberFocusRequester(focus: ComposerFocusState): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(focus.requests) {
        if (focus.requests > 0) requester.requestFocus()
    }
    return requester
}
