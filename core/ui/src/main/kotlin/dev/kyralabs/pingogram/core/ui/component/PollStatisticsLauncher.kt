package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the statistics of the poll of message `messageId` in chat `chatId` (`feature:profile`'s poll statistics page)
 * from the conversation (`feature:chat`), which cannot depend on it: a poll's message actions offer "Statistics" where
 * Telegram gives them, and the app provides this. Null in previews and tests, where nothing opens.
 */
val LocalPollStatisticsLauncher = staticCompositionLocalOf<((chatId: Long, messageId: Long) -> Unit)?> { null }
