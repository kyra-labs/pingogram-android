package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.feature.chats.ui.drafts.DraftsRoute
import dev.kyralabs.pingogram.core.navigation.DraftsRoute as DraftsDestination

/**
 * Adds Drafts (`REQUIREMENTS.md` FR-X16), [DraftsDestination], to the graph; [onBack] leaves it, and a row opens its
 * chat through [onOpenChat].
 */
fun NavGraphBuilder.draftsScreen(
    onBack: () -> Unit,
    onOpenChat: (chatId: Long) -> Unit,
) {
    composable<DraftsDestination> { DraftsRoute(onBack = onBack, onOpenChat = onOpenChat) }
}
