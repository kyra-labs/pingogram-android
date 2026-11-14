package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.compose.runtime.staticCompositionLocalOf
import dev.kyralabs.pingogram.core.navigation.PinnedMessagesRoute

/**
 * Opens the page of every pinned message of a conversation ([PinnedMessagesRoute]), pushed above it by the app's
 * `NavController`, also from the Chats tab's detail pane: the pinned bar's "All pinned messages". The app provides it
 * (`navigateToPinnedMessages`); null in previews and tests, where nothing opens.
 */
val LocalPinnedMessagesLauncher = staticCompositionLocalOf<((PinnedMessagesRoute) -> Unit)?> { null }
