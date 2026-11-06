package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier

/**
 * What the Chats tab's header gets from the app shell (`docs/DESIGN_SYSTEM.md` §6 "Chat list top bar"; owner decision
 * 2026-11-06), beside the title:
 *
 * - **Search**: the search button opens global search ([openSearch]) and grows into it with [search]'s container
 *   transform; it is away while search shows ([isSearchOpen]).
 * - **Account**: the signed-in user's avatar opens the Settings tab ([openAccount]); [accountMenu] wraps it with the
 *   Settings tab's account menu, which a long press opens (`docs/adr/0031-multiple-accounts.md`), given the modifier
 *   to put on the avatar. Without several accounts it hands the avatar no modifier.
 */
@Stable
class ChatListHeaderActions(
    val search: GlobalSearchState,
    private val searchOpen: State<Boolean>,
    val openSearch: () -> Unit,
    val openAccount: () -> Unit,
    val accountMenu: @Composable (content: @Composable (Modifier) -> Unit) -> Unit = { content -> content(Modifier) },
) {
    /** Search shows, docked or as its own destination: the search button is away. */
    val isSearchOpen: Boolean get() = searchOpen.value
}
