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
 * - **Catch-up**: the status line's chip opens Catch-up ([openCatchUp], owner decision 2026-11-15).
 * - **Mentions & replies**: its button opens the page of messages that mention the user or reply to them
 *   ([openMentions], `REQUIREMENTS.md` FR-X1).
 * - **Drafts**: its button, while a chat has a draft, opens the page of unsent messages ([openDrafts],
 *   `REQUIREMENTS.md` FR-X16).
 * - **Focus**: the Focus switcher's "Manage" opens Settings → Focus ([openFocus], board 31).
 * - **Feed**: the Feed tab, first in the folder row, opens the Channel feed ([openFeed], `REQUIREMENTS.md` FR-X5).
 * - **Restore**: the restore card above the list opens Backup & restore restoring a backup ([openBackup], by its
 *   document URI; `docs/adr/0039-local-backup-and-restore.md`).
 */
@Stable
class ChatListHeaderActions(
    val search: GlobalSearchState,
    private val searchOpen: State<Boolean>,
    val openSearch: () -> Unit,
    val openAccount: () -> Unit,
    val accountMenu: @Composable (content: @Composable (Modifier) -> Unit) -> Unit = { content -> content(Modifier) },
    val openCatchUp: () -> Unit = {},
    val openMentions: () -> Unit = {},
    val openDrafts: () -> Unit = {},
    val openFocus: () -> Unit = {},
    val openFeed: () -> Unit = {},
    val openBackup: (document: String) -> Unit = {},
) {
    /** Search shows, docked or as its own destination: the search button is away. */
    val isSearchOpen: Boolean get() = searchOpen.value

    /** The same actions, the Feed tab doing [open] instead: on two panes, the feed docks in the list pane. */
    fun withOpenFeed(open: () -> Unit): ChatListHeaderActions =
        ChatListHeaderActions(
            search = search,
            searchOpen = searchOpen,
            openSearch = openSearch,
            openAccount = openAccount,
            accountMenu = accountMenu,
            openCatchUp = openCatchUp,
            openMentions = openMentions,
            openDrafts = openDrafts,
            openFocus = openFocus,
            openFeed = open,
            openBackup = openBackup,
        )
}
