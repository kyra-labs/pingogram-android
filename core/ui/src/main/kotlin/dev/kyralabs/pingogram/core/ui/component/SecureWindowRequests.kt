package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether a screen asks for the window to be secure (`FLAG_SECURE`: no screenshots, no screen recording, nothing in the
 * recent apps), as a secret chat does while it is open (`REQUIREMENTS.md` FR-E1). The activity provides one through
 * [LocalSecureWindow] and keeps the window secure while [isRequested] or for its own reasons (the passcode lock), in
 * one place, so neither clears the flag the other needs; screens ask with [SecureWindow].
 *
 * Requests are counted, so two screens composed at once (a transition between a secret chat and the media viewer
 * above it) keep the window secure while either still asks.
 */
@Stable
class SecureWindowRequests {
    private var requests by mutableIntStateOf(0)

    /** Some screen asks for a secure window. */
    val isRequested: Boolean get() = requests > 0

    internal fun request() {
        requests++
    }

    internal fun release() {
        requests--
    }
}

/**
 * The [SecureWindowRequests] the activity honours. The default belongs to nobody: without the activity (previews, a
 * screen's own tests), asking has no effect.
 */
val LocalSecureWindow = staticCompositionLocalOf { SecureWindowRequests() }

/**
 * Keeps the window secure while [secure] and this call is in the composition: the request ends when [secure] turns
 * false, and when the screen leaves the composition however it leaves it (navigation, the passcode lock taking the
 * screen, a finished activity).
 */
@Composable
fun SecureWindow(secure: Boolean) {
    if (!secure) return
    val requests = LocalSecureWindow.current
    DisposableEffect(requests) {
        requests.request()
        onDispose { requests.release() }
    }
}
