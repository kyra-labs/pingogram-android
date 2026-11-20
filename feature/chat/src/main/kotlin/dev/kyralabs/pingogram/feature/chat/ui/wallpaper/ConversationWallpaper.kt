package dev.kyralabs.pingogram.feature.chat.ui.wallpaper

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import dev.kyralabs.pingogram.core.ui.theme.isDarkTheme
import dev.kyralabs.pingogram.core.ui.wallpaper.ChatWallpaperLayer

/**
 * The conversation's wallpaper, which `ChatRoute` provides from [ChatWallpaperViewModel]; the theme-color one alone
 * where nothing provides it (previews, screenshot tests).
 */
internal val LocalConversationWallpaper = compositionLocalOf { ConversationWallpaper() }

/**
 * The wallpaper behind the conversation's bars and messages (`docs/DESIGN_SYSTEM.md` §6 "Chat appearance"): the chat's
 * own, else the account's for the theme dimmed as Chat appearance says in dark theme, else the theme-color one.
 */
@Composable
internal fun ConversationWallpaperLayer(modifier: Modifier = Modifier) {
    val shown = LocalConversationWallpaper.current.forTheme(isDarkTheme())
    ChatWallpaperLayer(shown.wallpaper, shown.darkThemeDimming, modifier)
}
