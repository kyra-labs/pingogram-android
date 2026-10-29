package dev.kyralabs.pingogram.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.Proxy
import dev.kyralabs.pingogram.core.domain.model.ProxyType
import dev.kyralabs.pingogram.core.domain.model.SettingsPage
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.navigation.ActiveSessionsRoute
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ChatBoostRoute
import dev.kyralabs.pingogram.core.navigation.ChatFolderInviteRoute
import dev.kyralabs.pingogram.core.navigation.ChatFoldersRoute
import dev.kyralabs.pingogram.core.navigation.ChatInviteRoute
import dev.kyralabs.pingogram.core.navigation.ChatRoute
import dev.kyralabs.pingogram.core.navigation.DataStorageRoute
import dev.kyralabs.pingogram.core.navigation.EditProfileRoute
import dev.kyralabs.pingogram.core.navigation.LanguageRoute
import dev.kyralabs.pingogram.core.navigation.MyQrCodeRoute
import dev.kyralabs.pingogram.core.navigation.NewChatRoute
import dev.kyralabs.pingogram.core.navigation.NotificationSettingsRoute
import dev.kyralabs.pingogram.core.navigation.PrivacySecurityRoute
import dev.kyralabs.pingogram.core.navigation.ProxyLinkRoute
import dev.kyralabs.pingogram.core.navigation.QrLoginRoute
import dev.kyralabs.pingogram.core.navigation.StickerSetRoute

/**
 * Hands [link], a Telegram link another app opened Pingogram with, to [onOpen] once the main graph shows, after
 * `SessionNavigationEffect` moved there, and reports it handled ([onHandled]). Unlike a notification's chat, a link
 * belongs to no account: signed out, it waits for the login and opens after it (`docs/adr/0013-deep-links.md`). The
 * passcode lock holds it too, as the app, and this effect, are not composed while locked.
 */
@Composable
internal fun OpenLinkEffect(
    navController: NavHostController,
    uiState: MainActivityUiState,
    link: String?,
    onOpen: (link: String) -> Unit,
    onHandled: () -> Unit,
) {
    val currentEntry by navController.currentBackStackEntryAsState()
    val inMainGraph = currentEntry?.destination.isInMainGraph()
    val currentOnOpen by rememberUpdatedState(onOpen)
    val currentOnHandled by rememberUpdatedState(onHandled)
    LaunchedEffect(link, uiState, inMainGraph) {
        if (link == null || uiState != MainActivityUiState.SignedIn || !inMainGraph) return@LaunchedEffect
        currentOnOpen(link)
        currentOnHandled()
    }
}

/**
 * Opens the screen of a link's [target] (`docs/adr/0013-deep-links.md`); false for a notice, which has no screen
 * (`LinkViewModel` never hands one over).
 *
 * - A **chat** opens in the Chats tab's detail pane on expanded and wider windows ([besideList], [navigateToChat]),
 *   back on the Chats tab. On others it is pushed above the screen the link was tapped on, or, from another app
 *   ([fromOutside]), above the chat list, as from a notification, so back returns to the chat list. A link to the chat
 *   already open shows nothing new; one to a message of it opens the conversation at the message in its place, as
 *   "Show in chat" does. A **bot's start link** opens the bot's chat the same way, which `LinkViewModel` handed the
 *   start parameter to: its "Start" sends it once tapped, also in the conversation already open.
 * - A **user's profile** is pushed, and so is a group's or channel's **info** (`ChatInfoRoute`).
 * - **Contacts** and **Settings** switch tabs, a page of Settings is pushed above its tab; **Edit profile** and **New
 *   chat** are pushed; **search** opens with [onOpenSearch], docked or full screen as the window wants.
 * - An **invitation** opens the join sheet above the screen (`ChatInviteRoute`, a dialog destination), a **shared
 *   folder** its sheet (`ChatFolderInviteRoute`, also a dialog destination), a **sticker set** its sheet
 *   (`StickerSetRoute`, a dialog destination of the settings pages' graph); a **QR code
 *   login** the page that asks before logging the device in (`QrLoginRoute`); a **boost** link the chat's boosts
 *   (`ChatBoostRoute`); the user's **QR code** its page (`MyQrCodeRoute`); a **proxy** the sheet that asks before
 *   connecting through it (`ProxyLinkRoute`, a dialog destination of the settings pages' graph). Each of them changes
 *   nothing by itself.
 * - A **message to share** (`t.me/share?url=…&text=…`) opens the share sheet through [onShare], to pick the chat it
 *   goes to, never sent by itself.
 * - A **group call's** link (`t.me/call/…`) opens the voice chat's screen through [onJoinGroupCall], which asks before
 *   it joins (`docs/adr/0025-conference-calls.md`).
 */
@Suppress("CyclomaticComplexMethod") // One branch per kind of target, each a single navigation.
internal fun NavHostController.openLinkTarget(
    target: LinkTarget,
    fromOutside: Boolean,
    onOpenSearch: () -> Unit,
    onShare: (SharedContent) -> Unit = {},
    besideList: Boolean = false,
    onJoinGroupCall: ((String) -> Unit)? = null,
): Boolean {
    when (target) {
        is LinkTarget.Chat -> openLinkedChat(target, fromOutside, besideList)
        is LinkTarget.BotStart -> openLinkedChat(LinkTarget.Chat(target.chatId), fromOutside, besideList)
        is LinkTarget.ChatInfo -> navigateToChatInfo(target.chatId)
        is LinkTarget.UserProfile -> navigateToProfile(target.userId)
        LinkTarget.EditProfile -> navigate(EditProfileRoute) { launchSingleTop = true }
        LinkTarget.Contacts -> navigateToTopLevelDestination(TopLevelDestination.Contacts)
        is LinkTarget.Settings -> openSettings(target.page)
        is LinkTarget.NewChat -> navigate(NewChatRoute) { launchSingleTop = true }
        LinkTarget.Search -> onOpenSearch()
        is LinkTarget.ChatInvite -> navigate(ChatInviteRoute(target.inviteLink)) { launchSingleTop = true }
        is LinkTarget.ChatFolderInvite -> navigate(ChatFolderInviteRoute(target.inviteLink)) { launchSingleTop = true }
        is LinkTarget.StickerSet -> navigate(StickerSetRoute(name = target.name)) { launchSingleTop = true }
        is LinkTarget.QrCodeLogin -> navigate(QrLoginRoute(target.link)) { launchSingleTop = true }
        is LinkTarget.ChatBoost -> navigate(ChatBoostRoute(target.chatId)) { launchSingleTop = true }
        LinkTarget.MyQrCode -> navigate(MyQrCodeRoute) { launchSingleTop = true }
        is LinkTarget.ProxyServer -> navigate(target.proxy.linkRoute()) { launchSingleTop = true }
        is LinkTarget.MessageDraft -> onShare(SharedContent(text = target.text))
        else -> return target is LinkTarget.GroupCall && onJoinGroupCall?.invoke(target.link) != null
    }
    return true
}

/**
 * Opens the conversation of [target] in the Chats tab's detail pane where [besideList], or pushes it, [fromOutside]
 * above the chat list; nothing if it shows already.
 */
private fun NavHostController.openLinkedChat(
    target: LinkTarget.Chat,
    fromOutside: Boolean,
    besideList: Boolean,
) {
    val current = currentBackStackEntry
    val openChat = current?.takeIf { it.destination.hasRoute<ChatRoute>() }?.toRoute<ChatRoute>()
    val sameChat = openChat?.chatId == target.chatId
    if (sameChat && target.messageId == null) return
    if (besideList) return navigateToChat(target.chatId, target.messageId, besideList = true)
    navigate(ChatRoute(target.chatId, target.messageId)) {
        when {
            sameChat -> popUpTo<ChatRoute> { inclusive = true }
            fromOutside -> popUpTo(TopLevelDestination.Start.route)
        }
    }
}

/** The route of the sheet that asks before connecting through this proxy. */
private fun Proxy.linkRoute(): ProxyLinkRoute =
    when (val type = type) {
        is ProxyType.Socks5 -> {
            ProxyLinkRoute(server, port, type.kind.name, username = type.username, password = type.password)
        }

        is ProxyType.Http -> {
            ProxyLinkRoute(server, port, type.kind.name, type.username, type.password, httpOnly = type.httpOnly)
        }

        is ProxyType.Mtproto -> {
            ProxyLinkRoute(server, port, type.kind.name, secret = type.secret)
        }
    }

/** The Settings tab, with [page] pushed above it if the link named one. */
private fun NavHostController.openSettings(page: SettingsPage?) {
    navigateToTopLevelDestination(TopLevelDestination.Settings)
    val route: Any =
        when (page) {
            null -> return
            SettingsPage.Appearance -> AppearanceRoute
            SettingsPage.ChatFolders -> ChatFoldersRoute
            SettingsPage.DataAndStorage -> DataStorageRoute
            SettingsPage.ActiveSessions -> ActiveSessionsRoute
            SettingsPage.Language -> LanguageRoute
            SettingsPage.Notifications -> NotificationSettingsRoute
            SettingsPage.PrivacyAndSecurity -> PrivacySecurityRoute
        }
    navigate(route) { launchSingleTop = true }
}
