package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Whether a screen asks the app's floating navigation bar (`docs/DESIGN_SYSTEM.md` §6.1) to step aside, so two floating
 * bars never stack: the screen shows its own floating toolbar in the bar's place (the chat list's selection). The app
 * provides one through [LocalNavigationBarVisibility] and hides the bar while [isHidden]; screens ask with
 * [HideNavigationBar].
 *
 * Requests are counted, so two screens composed at once (e.g. during a transition between them) do not show the bar
 * while either still asks.
 */
@Stable
class NavigationBarVisibility {
    private var hideRequests by mutableIntStateOf(0)

    /** Some screen asks to hide the bar. */
    val isHidden: Boolean get() = hideRequests > 0

    internal fun requestHide() {
        hideRequests++
    }

    internal fun releaseHide() {
        hideRequests--
    }
}

/**
 * The [NavigationBarVisibility] the app honours. The default belongs to nobody: without the app (previews, a screen's
 * own tests), hiding the bar has no effect.
 */
val LocalNavigationBarVisibility = staticCompositionLocalOf { NavigationBarVisibility() }

/**
 * Hides the floating navigation bar while [hidden] and this call is in the composition: the bar returns when [hidden]
 * turns false, and when the screen leaves the composition however it leaves it (navigation, a finished activity).
 * Insets stay: [LocalNavigationBarInsets] keeps the bar's space, which the screen's toolbar takes.
 */
@Composable
fun HideNavigationBar(hidden: Boolean) {
    if (!hidden) return
    val visibility = LocalNavigationBarVisibility.current
    DisposableEffect(visibility) {
        visibility.requestHide()
        onDispose { visibility.releaseHide() }
    }
}
