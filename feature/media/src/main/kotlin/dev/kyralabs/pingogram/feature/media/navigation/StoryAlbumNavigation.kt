package dev.kyralabs.pingogram.feature.media.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.feature.media.storyalbum.StoryAlbumRoute as StoryAlbum

/**
 * Adds an opened story album, [StoryAlbumRoute] (board 11b), to the graph; [onOpenStory] plays the album from a story
 * (see [navigateToStoryAlbumViewer]).
 */
fun NavGraphBuilder.storyAlbumScreen(
    onBack: () -> Unit,
    onOpenStory: (chatId: Long, albumId: Int, storyId: Int) -> Unit,
) {
    composable<StoryAlbumRoute> { entry ->
        val route = entry.toRoute<StoryAlbumRoute>()
        StoryAlbum(onBack = onBack, onOpenStory = { storyId -> onOpenStory(route.chatId, route.albumId, storyId) })
    }
}

/** Opens story album [albumId] of chat [chatId]. */
fun NavController.navigateToStoryAlbum(
    chatId: Long,
    albumId: Int,
) {
    navigate(StoryAlbumRoute(chatId, albumId)) { launchSingleTop = true }
}

/** Plays story album [albumId] of chat [chatId] in the stories viewer, from story [storyId]. */
fun NavController.navigateToStoryAlbumViewer(
    chatId: Long,
    albumId: Int,
    storyId: Int,
) {
    navigate(StoryViewerRoute(chatId, storyId, albumId)) { launchSingleTop = true }
}
