package dev.kyralabs.pingogram.feature.profile.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.ChatInfoRoute
import dev.kyralabs.pingogram.core.navigation.ChatMembersRoute
import dev.kyralabs.pingogram.core.navigation.ChatPermissionsRoute
import dev.kyralabs.pingogram.core.navigation.EditChatInfoRoute
import dev.kyralabs.pingogram.core.navigation.EditInviteLinkRoute
import dev.kyralabs.pingogram.core.navigation.InviteLinksRoute
import dev.kyralabs.pingogram.core.navigation.MemberRightsRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoNavigation
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoPage
import dev.kyralabs.pingogram.feature.profile.chatinfo.INVITE_LINKS_CHANGED_RESULT
import dev.kyralabs.pingogram.feature.profile.chatinfo.MEMBERS_CHANGED_RESULT
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatInfoRoute as ChatInfoScreenRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatMembersRoute as ChatMembersScreenRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.ChatPermissionsRoute as ChatPermissionsScreenRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.EditChatInfoRoute as EditChatInfoScreenRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.EditInviteLinkRoute as EditInviteLinkScreenRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.InviteLinksRoute as InviteLinksScreenRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.MemberRightsRoute as MemberRightsScreenRoute

/**
 * Adds the info page of a group or channel, [ChatInfoRoute] (`REQUIREMENTS.md` FR-P3, FR-P4), and the pages it
 * opens to the graph, each pushed above the one before: the members ([ChatMembersRoute]), a member's rights
 * ([MemberRightsRoute]), editing the title and description ([EditChatInfoRoute]), the permissions
 * ([ChatPermissionsRoute]) and the invite links ([InviteLinksRoute], [EditInviteLinkRoute]). [onOpen] pushes one of
 * those routes, [onBack] leaves the page shown. [onLeft] runs once the user left or deleted the chat, which is gone
 * from the lists. [onOpenChat] opens a chat (a mention, a removed channel), [onOpenProfile] a member's profile, and
 * [onOpenMedia] / [onOpenMessage] the shared media as the profile's do. A rights page or link editor that saved goes
 * back through [NavController.popBackStackAfterGroupChange], so the page below reads its list again.
 */
@Suppress("LongParameterList") // The app's navigation from the info page.
fun NavGraphBuilder.chatInfoScreens(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
    onLeft: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
    onOpenProfile: (userId: Long) -> Unit,
    onOpenMedia: (chatId: Long, messageId: Long) -> Unit,
    onOpenMessage: (chatId: Long, messageId: Long) -> Unit,
    onSaved: () -> Unit,
) {
    composable<ChatInfoRoute> { entry ->
        val chatId = entry.toRoute<ChatInfoRoute>().chatId
        ChatInfoScreenRoute(
            navigation =
                ChatInfoNavigation(
                    onBack = onBack,
                    onLeft = onLeft,
                    onOpenChat = onOpenChat,
                    onOpenProfile = onOpenProfile,
                    onOpenMembers = { filter -> onOpen(ChatMembersRoute(chatId, filter.name)) },
                    onOpenPage = { page ->
                        onOpen(
                            when (page) {
                                ChatInfoPage.EditInfo -> EditChatInfoRoute(chatId)
                                ChatInfoPage.Permissions -> ChatPermissionsRoute(chatId)
                                ChatInfoPage.InviteLinks -> InviteLinksRoute(chatId)
                            },
                        )
                    },
                    onOpenMedia = onOpenMedia,
                    onOpenMessage = onOpenMessage,
                ),
        )
    }
    composable<ChatMembersRoute> { entry ->
        val chatId = entry.toRoute<ChatMembersRoute>().chatId
        ChatMembersScreenRoute(
            onBack = onBack,
            onOpenProfile = onOpenProfile,
            onOpenChat = onOpenChat,
            onOpenRights = { userId, restrict -> onOpen(MemberRightsRoute(chatId, userId, restrict)) },
        )
    }
    composable<MemberRightsRoute> { MemberRightsScreenRoute(onBack = onBack, onSaved = onSaved) }
    composable<EditChatInfoRoute> { EditChatInfoScreenRoute(onBack = onBack) }
    composable<ChatPermissionsRoute> { ChatPermissionsScreenRoute(onBack = onBack) }
    composable<InviteLinksRoute> { entry ->
        val chatId = entry.toRoute<InviteLinksRoute>().chatId
        val results = entry.savedStateHandle
        val changed by results.getStateFlow(INVITE_LINKS_CHANGED_RESULT, false).collectAsStateWithLifecycle()
        InviteLinksScreenRoute(
            onBack = onBack,
            onOpenEditor = { link -> onOpen(EditInviteLinkRoute(chatId, link)) },
            refresh = changed,
            onRefreshed = { results[INVITE_LINKS_CHANGED_RESULT] = false },
        )
    }
    composable<EditInviteLinkRoute> { EditInviteLinkScreenRoute(onBack = onBack, onSaved = onSaved) }
}

/**
 * Leaves a member's rights page or the link editor once it saved, back to the members or the invite links below it,
 * which then read their list again: a result in that page's saved state, as Navigation returns results.
 */
fun NavController.popBackStackAfterGroupChange() {
    previousBackStackEntry?.savedStateHandle?.let { below ->
        below[MEMBERS_CHANGED_RESULT] = true
        below[INVITE_LINKS_CHANGED_RESULT] = true
    }
    popBackStack()
}
