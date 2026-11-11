package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.navigation.NewStoryRoute
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.feature.media.navigation.navigateToStoryAlbumViewer
import dev.kyralabs.pingogram.feature.media.navigation.newStoryScreen
import dev.kyralabs.pingogram.feature.media.navigation.storyAlbumScreen
import dev.kyralabs.pingogram.feature.media.navigation.storyViewerScreen

/** The stories viewer, posting stories and an opened story album, in the main graph. */
internal fun NavGraphBuilder.stories(navController: NavHostController) {
    // The viewer and nothing below it: a second close, e.g. a swipe while its exit plays, does nothing.
    storyViewerScreen(onClose = { navController.popBackStack<StoryViewerRoute>(inclusive = true) })
    newStoryScreen(onClose = { navController.popBackStack<NewStoryRoute>(inclusive = true) })
    storyAlbumScreen(
        onBack = { navController.popBackStack<StoryAlbumRoute>(inclusive = true) },
        onOpenStory = navController::navigateToStoryAlbumViewer,
    )
}
