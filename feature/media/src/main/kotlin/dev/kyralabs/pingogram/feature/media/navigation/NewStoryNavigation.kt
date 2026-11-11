package dev.kyralabs.pingogram.feature.media.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import dev.kyralabs.pingogram.core.navigation.NewStoryRoute
import dev.kyralabs.pingogram.feature.media.storypost.NewStoryRoute as NewStory

/**
 * Adds posting a story, [NewStoryRoute], to the graph, with the `NavHost`'s transitions for full-screen destinations
 * (`docs/adr/0036-posting-stories.md`): picking the photos and videos, then composing the story. [onClose] leaves
 * it, also once the stories are posted.
 */
fun NavGraphBuilder.newStoryScreen(onClose: () -> Unit) {
    composable<NewStoryRoute> { NewStory(onClose = onClose) }
}

/** Opens posting a story; once, however often it is asked for. */
fun NavController.navigateToNewStory() {
    navigate(NewStoryRoute) { launchSingleTop = true }
}
