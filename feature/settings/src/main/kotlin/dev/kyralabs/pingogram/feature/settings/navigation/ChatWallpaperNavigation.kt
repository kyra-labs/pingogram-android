package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.ChatWallpaperRoute
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.ChatOwnWallpaperRoute

/**
 * Adds a chat's own wallpaper page, [ChatWallpaperRoute] (owner decision 2026-11-20), to the graph: pushed above the
 * profile it is opened from. [onBack] leaves it.
 */
fun NavGraphBuilder.chatWallpaperScreen(onBack: () -> Unit) {
    composable<ChatWallpaperRoute> { entry -> ChatOwnWallpaperRoute(entry.toRoute<ChatWallpaperRoute>().chatId, onBack) }
}

/** Pushes chat [chatId]'s own wallpaper page. */
fun NavHostController.navigateToChatWallpaper(chatId: Long) {
    navigate(ChatWallpaperRoute(chatId)) { launchSingleTop = true }
}
