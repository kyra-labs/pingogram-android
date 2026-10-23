package dev.kyralabs.pingogram.feature.chat.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.ChatExportRoute
import dev.kyralabs.pingogram.feature.chat.ui.export.ChatExportRoute as ChatExportScreenRoute

/**
 * Adds the export page, [ChatExportRoute] (`REQUIREMENTS.md` §5.9, `docs/adr/0021-chat-export.md`), to the graph:
 * pushed above the profile or info page it is opened from, with the shared axis on X. [onBack] leaves it; the export
 * it starts goes on in the background.
 */
fun NavGraphBuilder.chatExportScreen(onBack: () -> Unit) {
    composable<ChatExportRoute> { ChatExportScreenRoute(onBack = onBack) }
}

/** Pushes the export page of chat [chatId], unless it is shown already. */
fun NavController.navigateToChatExport(chatId: Long) {
    navigate(ChatExportRoute(chatId)) { launchSingleTop = true }
}
