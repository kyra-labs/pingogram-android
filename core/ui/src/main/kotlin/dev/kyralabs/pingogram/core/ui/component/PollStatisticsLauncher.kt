package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the votes over time of a poll (`feature:profile`'s statistics page, `ChatStatisticsRoute(chatId,
 * pollMessageId)`) from the conversation, which cannot depend on that feature: the message's "Poll statistics" calls it
 * with the chat's and the message's ids. Null in previews and tests, where the action does nothing.
 */
val LocalPollStatisticsLauncher = staticCompositionLocalOf<((chatId: Long, messageId: Long) -> Unit)?> { null }
