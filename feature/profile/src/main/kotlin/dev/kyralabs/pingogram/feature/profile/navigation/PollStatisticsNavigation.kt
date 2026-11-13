package dev.kyralabs.pingogram.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.PollStatisticsRoute
import dev.kyralabs.pingogram.feature.profile.chatinfo.stats.PollStatisticsRoute as PollStatisticsScreenRoute

/**
 * Adds a poll's statistics page, [PollStatisticsRoute], to the graph: pushed above the conversation whose poll's
 * message actions open it with [navigateToPollStatistics]. [onBack] leaves it.
 */
fun NavGraphBuilder.pollStatisticsScreen(onBack: () -> Unit) {
    composable<PollStatisticsRoute> { PollStatisticsScreenRoute(onBack = onBack) }
}

/** Pushes the statistics of the poll of message [messageId] in chat [chatId], unless they show already. */
fun NavController.navigateToPollStatistics(
    chatId: Long,
    messageId: Long,
) {
    navigate(PollStatisticsRoute(chatId, messageId)) { launchSingleTop = true }
}
