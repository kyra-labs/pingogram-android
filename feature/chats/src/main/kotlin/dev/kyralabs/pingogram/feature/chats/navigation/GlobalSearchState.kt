package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.rememberViewModelStoreOwner
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.containerTransform
import dev.kyralabs.pingogram.feature.chats.ui.SearchRoute

/**
 * Where global search shows and what it shares with the app's search button (`docs/DESIGN_SYSTEM.md` §5, §6 "Global
 * search"), held by the app shell across its navigation and the `NavHost`.
 *
 * - **Docked** ([isDocked]): on expanded windows search opens in the Chats tab's list pane ([SearchPane]) instead of as
 *   its own destination ([searchScreen]); the app shows the pane while this is true, and the pane sets it false when it
 *   closes. Saved, so docked search survives rotation and process death.
 * - **Container transform**: the search button ([searchButtonTransform]) and search ([searchScreen], [SearchPane])
 *   share their bounds while one comes and the other goes. Each side tells the other its corners ([buttonShape],
 *   [searchShape]), so both clip to the same shape as it morphs: the floating navigation bar's circle or the rail's
 *   rounded square, and the full-screen destination's square corners or the pane's rounded ones.
 */
@Stable
class GlobalSearchState(
    initiallyDocked: Boolean = false,
) {
    /** Search shows docked in the Chats tab's list pane. */
    var isDocked by mutableStateOf(initiallyDocked)

    /** The corners of the search button the app shows, which search grows from and shrinks back into. */
    internal var buttonShape: CornerBasedShape by mutableStateOf(CircleShape)

    /** The corners of search where it shows, which the search button grows into. */
    internal var searchShape: CornerBasedShape by mutableStateOf(FullBleedShape)

    companion object {
        /** Keeps whether search is docked across rotation and process death. */
        val Saver: Saver<GlobalSearchState, Boolean> =
            Saver(save = { it.isDocked }, restore = { GlobalSearchState(initiallyDocked = it) })
    }
}

/** A [GlobalSearchState], saved. */
@Composable
fun rememberGlobalSearchState(): GlobalSearchState =
    rememberSaveable(saver = GlobalSearchState.Saver) {
        GlobalSearchState()
    }

/**
 * The search button's side of its container transform into search ([GlobalSearchState]): this content, clipped to
 * [shape], and search share their bounds while the `NavHost` destination or the [SearchPane] that provides the
 * animated visibility scope comes or goes, and the button's own animated visibility scope goes or comes with it. The
 * app hides the button while search shows, so the two never show at once.
 */
@Composable
fun Modifier.searchButtonTransform(
    state: GlobalSearchState,
    shape: CornerBasedShape,
): Modifier {
    SideEffect { state.buttonShape = shape }
    return containerTransform(GlobalSearchTransformKey, shape = shape, counterpartShape = state.searchShape)
}

/** Search's side of the container transform with the search button: its own corners are [shape]. */
@Composable
internal fun Modifier.searchTransform(
    state: GlobalSearchState,
    shape: CornerBasedShape,
): Modifier {
    SideEffect { state.searchShape = shape }
    // A whole screen: laid out once and scaled as it grows, not measured anew in every frame.
    return containerTransform(
        GlobalSearchTransformKey,
        shape = shape,
        counterpartShape = state.buttonShape,
        fixedLayout = true,
    )
}

/**
 * Global search docked in the Chats tab's list pane on expanded windows (`docs/DESIGN_SYSTEM.md` §6 "Global search",
 * §7): the same screen as the full-screen destination, in the pane's container ([shape], its rounded corners beside
 * the detail pane), growing out of the search button where the caller provides an animated visibility scope. A chat it
 * found calls [onOpenChat], a message [onOpenMessage]; the app opens them in the detail pane.
 *
 * Its back arrow, and system back while [backEnabled] (the caller turns it off while back has something else to close
 * first, e.g. the conversation beside it), close it ([GlobalSearchState.isDocked] false). Its view model lives as long
 * as the pane ([rememberViewModelStoreOwner]): closed and opened again, search starts afresh, as the destination does.
 */
@Composable
fun SearchPane(
    state: GlobalSearchState,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = FullBleedShape,
    backEnabled: Boolean = true,
) {
    val close = { state.isDocked = false }
    BackHandler(enabled = backEnabled, onBack = close)
    CompositionLocalProvider(LocalViewModelStoreOwner provides rememberViewModelStoreOwner()) {
        SearchRoute(
            onBack = close,
            onOpenChat = onOpenChat,
            onOpenMessage = onOpenMessage,
            modifier = modifier.searchTransform(state, shape),
        )
    }
}

/** What the search button and search share for their container transform. */
private data object GlobalSearchTransformKey
