package dev.kyralabs.pingogram.feature.media.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.NewStoryRoute
import dev.kyralabs.pingogram.feature.media.storypost.NewStoryRoute as NewStory

/**
 * Adds posting a story, [NewStoryRoute], to the graph, with the `NavHost`'s transitions for full-screen destinations
 * (`docs/adr/0036-posting-stories.md`): [onClose] leaves it, [onNext] goes on to the composer with the picked items.
 */
fun NavGraphBuilder.newStoryScreen(
    onClose: () -> Unit,
    onNext: () -> Unit,
) {
    composable<NewStoryRoute> { NewStory(onClose = onClose, onNext = onNext) }
}

/** Opens posting a story; once, however often it is asked for. */
fun NavController.navigateToNewStory() {
    navigate(NewStoryRoute) { launchSingleTop = true }
}
