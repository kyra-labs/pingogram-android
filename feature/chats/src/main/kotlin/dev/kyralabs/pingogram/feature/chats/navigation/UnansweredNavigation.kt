package dev.kyralabs.pingogram.feature.chats.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.feature.chats.ui.unanswered.UnansweredRoute
import dev.kyralabs.pingogram.core.navigation.UnansweredRoute as UnansweredDestination

/**
 * Adds Unanswered (`REQUIREMENTS.md` FR-X22), [UnansweredDestination], to the graph; [onBack] leaves it, and a row's
 * Reply opens its chat, its message box focused, through [onReply].
 */
fun NavGraphBuilder.unansweredScreen(
    onBack: () -> Unit,
    onReply: (chatId: Long) -> Unit,
) {
    composable<UnansweredDestination> { UnansweredRoute(onBack = onBack, onReply = onReply) }
}
