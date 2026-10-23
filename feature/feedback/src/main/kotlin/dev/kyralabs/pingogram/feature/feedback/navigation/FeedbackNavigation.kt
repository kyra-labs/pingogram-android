package dev.kyralabs.pingogram.feature.feedback.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.FeedbackRoute
import dev.kyralabs.pingogram.feature.feedback.FeedbackRoute as FeedbackScreenRoute

/**
 * Adds "Send feedback", [FeedbackRoute] (`docs/TESTING_GUIDE.md`), to the graph: pushed above the screen it is opened
 * from (Settings → About). [onBack] closes it.
 */
fun NavGraphBuilder.feedbackScreen(onBack: () -> Unit) {
    composable<FeedbackRoute> { FeedbackScreenRoute(onBack = onBack) }
}

/** Opens "Send feedback", unless it shows already. */
fun NavController.navigateToFeedback() = navigate(FeedbackRoute) { launchSingleTop = true }
