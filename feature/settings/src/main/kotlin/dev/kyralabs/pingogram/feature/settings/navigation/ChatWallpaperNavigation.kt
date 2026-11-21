package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.ChatWallpaperRoute
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperPageRoute

/**
 * Adds a chat's own wallpaper page, [ChatWallpaperRoute] (ADR 0041: the account's wallpaper page for one chat), to the
 * graph: pushed above the profile it is opened from. [onBack] leaves it.
 */
fun NavGraphBuilder.chatWallpaperScreen(onBack: () -> Unit) {
    composable<ChatWallpaperRoute> { entry ->
        WallpaperPageRoute(entry.toRoute<ChatWallpaperRoute>().chatId, onBack)
    }
}

/** Pushes chat [chatId]'s own wallpaper page. */
fun NavHostController.navigateToChatWallpaper(chatId: Long) {
    navigate(ChatWallpaperRoute(chatId)) { launchSingleTop = true }
}
