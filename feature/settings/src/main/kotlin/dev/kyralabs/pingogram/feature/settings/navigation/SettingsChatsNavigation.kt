package dev.kyralabs.pingogram.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import dev.kyralabs.pingogram.core.navigation.AppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ChatAppearanceRoute
import dev.kyralabs.pingogram.core.navigation.ChatsShownFirstRoute
import dev.kyralabs.pingogram.core.navigation.CleanupRoute
import dev.kyralabs.pingogram.core.navigation.ColorThemeRoute
import dev.kyralabs.pingogram.core.navigation.MutedWordsRoute
import dev.kyralabs.pingogram.core.navigation.QuickReactionRoute
import dev.kyralabs.pingogram.core.navigation.RaiseToListenRoute
import dev.kyralabs.pingogram.core.navigation.SettingsChatsRoute
import dev.kyralabs.pingogram.core.navigation.SnippetsRoute
import dev.kyralabs.pingogram.core.navigation.SwipeActionsRoute
import dev.kyralabs.pingogram.core.navigation.TranscriptionSettingsRoute
import dev.kyralabs.pingogram.core.navigation.WallpaperRoute
import dev.kyralabs.pingogram.feature.settings.ui.chats.ChatsPagePages
import dev.kyralabs.pingogram.feature.settings.ui.chats.ChatsPageRoute
import dev.kyralabs.pingogram.feature.settings.ui.wallpaper.WallpaperPageRoute
import dev.kyralabs.pingogram.feature.settings.ui.AppearanceRoute as AppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ChatAppearanceRoute as ChatAppearanceScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.ColorThemeRoute as ColorThemeScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.chatlist.SwipeActionsRoute as SwipeActionsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.cleanup.CleanupRoute as CleanupScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.mutedwords.MutedWordsRoute as MutedWordsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.raisetolisten.RaiseToListenRoute as RaiseToListenScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.reactions.QuickReactionRoute as QuickReactionScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.shownfirst.ChatsShownFirstRoute as ChatsShownFirstScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.snippets.SnippetsRoute as SnippetsScreenRoute
import dev.kyralabs.pingogram.feature.settings.ui.transcription.TranscriptionSettingsRoute as TranscriptionSettingsScreenRoute

/** Appearance and Chats, and the pages they open with [onOpen]; [onBack] leaves each. */
internal fun NavGraphBuilder.appearanceAndChatPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    appearancePages(onOpen, onBack)
    chatPages(onOpen, onBack)
}

/** Appearance and the pages it opens with [onOpen]: the Theme page, Chat appearance and its wallpaper page. */
private fun NavGraphBuilder.appearancePages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<AppearanceRoute> {
        AppearanceScreenRoute(
            onOpenColorTheme = { onOpen(ColorThemeRoute) },
            onOpenChatAppearance = { onOpen(ChatAppearanceRoute) },
            onBack = onBack,
        )
    }
    settingsPage<ColorThemeRoute> { ColorThemeScreenRoute(onBack = onBack) }
    settingsPage<ChatAppearanceRoute> {
        ChatAppearanceScreenRoute(onOpenWallpaper = { onOpen(WallpaperRoute) }, onBack = onBack)
    }
    settingsPage<WallpaperRoute> { WallpaperPageRoute(chatId = null, onBack = onBack) }
}

/**
 * The Chats page and the pages it opens with [onOpen]: Double-tap to react, Snippets, voice message transcription,
 * Raise to listen, Swipe actions, Chats shown first, Muted words and Cleanup suggestions.
 */
private fun NavGraphBuilder.chatPages(
    onOpen: (route: Any) -> Unit,
    onBack: () -> Unit,
) {
    settingsPage<SettingsChatsRoute> {
        ChatsPageRoute(
            pages =
                ChatsPagePages(
                    onOpenQuickReaction = { onOpen(QuickReactionRoute) },
                    onOpenSnippets = { onOpen(SnippetsRoute) },
                    onOpenTranscription = { onOpen(TranscriptionSettingsRoute) },
                    onOpenSwipeActions = { onOpen(SwipeActionsRoute) },
                    onOpenShownFirst = { onOpen(ChatsShownFirstRoute) },
                    onOpenRaiseToListen = { onOpen(RaiseToListenRoute) },
                    onOpenMutedWords = { onOpen(MutedWordsRoute) },
                    onOpenCleanup = { onOpen(CleanupRoute) },
                ),
            onBack = onBack,
        )
    }
    settingsPage<QuickReactionRoute> { QuickReactionScreenRoute(onBack = onBack) }
    settingsPage<RaiseToListenRoute> { RaiseToListenScreenRoute(onBack = onBack) }
    settingsPage<SwipeActionsRoute> { SwipeActionsScreenRoute(onBack = onBack) }
    settingsPage<ChatsShownFirstRoute> { ChatsShownFirstScreenRoute(onBack = onBack) }
    settingsPage<TranscriptionSettingsRoute> { TranscriptionSettingsScreenRoute(onBack = onBack) }
    settingsPage<SnippetsRoute> { SnippetsScreenRoute(onBack = onBack) }
    settingsPage<MutedWordsRoute> { MutedWordsScreenRoute(onBack = onBack) }
    settingsPage<CleanupRoute> { CleanupScreenRoute(onBack = onBack) }
}
