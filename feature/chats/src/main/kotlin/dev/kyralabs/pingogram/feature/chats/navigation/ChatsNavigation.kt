package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import dev.kyralabs.pingogram.core.navigation.ArchiveRoute
import dev.kyralabs.pingogram.core.navigation.ChatsRoute
import dev.kyralabs.pingogram.core.navigation.NewChannelDetailsRoute
import dev.kyralabs.pingogram.core.navigation.NewChannelMembersRoute
import dev.kyralabs.pingogram.core.navigation.NewChannelRoute
import dev.kyralabs.pingogram.core.navigation.NewChannelTypeRoute
import dev.kyralabs.pingogram.core.navigation.NewGroupDetailsRoute
import dev.kyralabs.pingogram.core.navigation.NewGroupMembersRoute
import dev.kyralabs.pingogram.core.navigation.NewGroupRoute
import dev.kyralabs.pingogram.core.ui.transition.FullBleedShape
import dev.kyralabs.pingogram.core.ui.transition.ProvideNavAnimatedVisibilityScope
import dev.kyralabs.pingogram.feature.chats.ui.ArchivedChatsRoute
import dev.kyralabs.pingogram.feature.chats.ui.ChatListRoute
import dev.kyralabs.pingogram.feature.chats.ui.NewChatRoute
import dev.kyralabs.pingogram.feature.chats.ui.SearchRoute
import dev.kyralabs.pingogram.feature.chats.ui.archive.ArchiveSettingsRoute
import dev.kyralabs.pingogram.feature.chats.ui.create.NewChannelDetailsStep
import dev.kyralabs.pingogram.feature.chats.ui.create.NewChannelMembersStep
import dev.kyralabs.pingogram.feature.chats.ui.create.NewChannelTypeStep
import dev.kyralabs.pingogram.feature.chats.ui.create.NewChannelViewModel
import dev.kyralabs.pingogram.feature.chats.ui.create.NewGroupDetailsStep
import dev.kyralabs.pingogram.feature.chats.ui.create.NewGroupMembersStep
import dev.kyralabs.pingogram.feature.chats.ui.create.NewGroupViewModel
import dev.kyralabs.pingogram.core.navigation.ArchiveSettingsRoute as ArchiveSettingsDestination
import dev.kyralabs.pingogram.core.navigation.NewChatRoute as NewChatDestination
import dev.kyralabs.pingogram.core.navigation.SearchRoute as SearchDestination

/**
 * The chat list of the Chats tab, [ChatsRoute], whose destination `:app` lays out: the list pane of a list-detail
 * layout beside the conversation of [openChatId] on expanded windows, the whole screen otherwise
 * (`docs/ARCHITECTURE.md`, Navigation). Its archive row calls [onOpenArchive], its rows call [onOpenChat] with their
 * chat's id, and its "New chat" buttons call [onOpenNewChat]; [openChatId]'s row looks and reads as selected. The
 * "New chat" floating action button grows into the New chat screen ([newChatScreen]) while the destination provides
 * its animated visibility scope. A story ring above the chats calls [onOpenStory] with its chat, for the stories viewer
 * (`StoryViewerRoute`, `:feature:media`).
 *
 * A folder tab's long press opens its menu: "Mark all as read", and, where [onEditFolder] is given, "Edit folder" first
 * ("Edit folders" on "All chats"), which calls it with the folder's id, or null for "All chats": the hook a folder
 * editor plugs into (`:app` opens `ChatFolderEditRoute(folderId)`, or `ChatFoldersRoute` for null). Screen readers get
 * the menu's items as the tab's custom actions.
 */
@Composable
fun ChatListPane(
    onOpenArchive: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenStory: (chatId: Long) -> Unit = {},
    openChatId: Long? = null,
    onEditFolder: ((folderId: Int?) -> Unit)? = null,
) {
    ChatListRoute(
        onOpenArchive = onOpenArchive,
        onOpenChat = onOpenChat,
        onOpenNewChat = onOpenNewChat,
        modifier = modifier,
        onOpenStory = onOpenStory,
        openChatId = openChatId,
        onEditFolder = onEditFolder,
    )
}

/**
 * Adds the archived chats, [ArchiveRoute], to the graph; [onBack] leaves them, a row calls [onOpenChat], and the top
 * bar's settings button [onOpenSettings] (for [archiveSettingsScreen]).
 */
fun NavGraphBuilder.archiveScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    composable<ArchiveRoute> {
        ArchivedChatsRoute(onBack = onBack, onOpenChat = onOpenChat, onOpenSettings = onOpenSettings)
    }
}

/**
 * Adds the archive's settings, [ArchiveSettingsDestination], to the graph: what Telegram archives and keeps archived,
 * and how the archive row shows. Any screen may navigate to the route, e.g. a settings page; [onBack] leaves it.
 */
fun NavGraphBuilder.archiveSettingsScreen(onBack: () -> Unit) {
    composable<ArchiveSettingsDestination> { ArchiveSettingsRoute(onBack = onBack) }
}

/**
 * Adds the New chat screen, [NewChatDestination], to the graph, which the Chats tab's "New chat" button grows into (a
 * container transform, through the app's shared transition scope); [onBack] leaves it, a contact calls [onOpenChat]
 * with the id of its private chat, New group [onNewGroup] ([newGroupScreens]), New channel [onNewChannel]
 * ([newChannelScreens]) and New contact [onAddContact].
 */
fun NavGraphBuilder.newChatScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onNewGroup: () -> Unit,
    onNewChannel: () -> Unit,
    onAddContact: () -> Unit,
) {
    composable<NewChatDestination> {
        ProvideNavAnimatedVisibilityScope(this) {
            NewChatRoute(
                onBack = onBack,
                onOpenChat = onOpenChat,
                onNewGroup = onNewGroup,
                onNewChannel = onNewChannel,
                onAddContact = onAddContact,
            )
        }
    }
}

/**
 * Adds creating a group (`REQUIREMENTS.md` FR-P4), the nested graph [NewGroupRoute], to the graph: picking the people
 * ([NewGroupMembersRoute]), then the group's details ([NewGroupDetailsRoute]), pushed on [navController] one above
 * the other and sharing one [NewGroupViewModel], held by the graph's entry. Back from the people leaves the flow; the
 * new group calls [onOpenChat], which is to take the flow's place.
 */
fun NavGraphBuilder.newGroupScreens(
    navController: NavController,
    onOpenChat: (chatId: Long) -> Unit,
) {
    navigation<NewGroupRoute>(startDestination = NewGroupMembersRoute) {
        composable<NewGroupMembersRoute> { entry ->
            NewGroupMembersStep(
                viewModel = flowViewModel<NewGroupViewModel, NewGroupRoute>(navController, entry),
                onBack = { navController.popBackStack<NewGroupRoute>(inclusive = true) },
                onNext = { navController.navigate(NewGroupDetailsRoute) { launchSingleTop = true } },
            )
        }
        composable<NewGroupDetailsRoute> { entry ->
            NewGroupDetailsStep(
                viewModel = flowViewModel<NewGroupViewModel, NewGroupRoute>(navController, entry),
                onBack = { navController.popBackStack() },
                onOpenChat = onOpenChat,
            )
        }
    }
}

/**
 * Adds creating a channel (`REQUIREMENTS.md` FR-P4), the nested graph [NewChannelRoute], to the graph: the channel's
 * details ([NewChannelDetailsRoute]), which create it, then its type ([NewChannelTypeRoute]) and its first subscribers
 * ([NewChannelMembersRoute]), pushed on [navController] one above the other and sharing one [NewChannelViewModel],
 * held by the graph's entry. Back from the details leaves the flow; once the channel exists, back and the last step
 * call [onOpenChat], which is to take the flow's place.
 */
fun NavGraphBuilder.newChannelScreens(
    navController: NavController,
    onOpenChat: (chatId: Long) -> Unit,
) {
    navigation<NewChannelRoute>(startDestination = NewChannelDetailsRoute) {
        composable<NewChannelDetailsRoute> { entry ->
            NewChannelDetailsStep(
                viewModel = flowViewModel<NewChannelViewModel, NewChannelRoute>(navController, entry),
                onBack = { navController.popBackStack<NewChannelRoute>(inclusive = true) },
                onNext = { navController.navigate(NewChannelTypeRoute) { launchSingleTop = true } },
            )
        }
        composable<NewChannelTypeRoute> { entry ->
            NewChannelTypeStep(
                viewModel = flowViewModel<NewChannelViewModel, NewChannelRoute>(navController, entry),
                onNext = { navController.navigate(NewChannelMembersRoute) { launchSingleTop = true } },
                onOpenChat = onOpenChat,
            )
        }
        composable<NewChannelMembersRoute> { entry ->
            NewChannelMembersStep(
                viewModel = flowViewModel<NewChannelViewModel, NewChannelRoute>(navController, entry),
                onOpenChat = onOpenChat,
            )
        }
    }
}

/**
 * The view model [VM] of the flow whose nested graph [G] holds [entry], kept by the graph's entry, so every step of the
 * flow shares it and it is cleared when the flow leaves the back stack.
 */
@Composable
private inline fun <reified VM : ViewModel, reified G : Any> flowViewModel(
    navController: NavController,
    entry: NavBackStackEntry,
): VM {
    val graphEntry = remember(entry) { navController.getBackStackEntry<G>() }
    return hiltViewModel<VM>(graphEntry)
}

/**
 * Adds global search, [SearchDestination], to the graph: the full-screen search of compact and medium windows
 * (`docs/DESIGN_SYSTEM.md` §6 "Global search"), which the app's search button grows into (a container transform
 * through the app's shared transition scope, [state]); [onBack] leaves it. A chat it found calls [onOpenChat] with its
 * id, a message it found [onOpenMessage] with its chat and its id.
 */
fun NavGraphBuilder.searchScreen(
    state: GlobalSearchState,
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
) {
    composable<SearchDestination> {
        ProvideNavAnimatedVisibilityScope(this) {
            SearchRoute(
                onBack = onBack,
                onOpenChat = onOpenChat,
                onOpenMessage = onOpenMessage,
                modifier = Modifier.searchTransform(state, FullBleedShape),
            )
        }
    }
}
