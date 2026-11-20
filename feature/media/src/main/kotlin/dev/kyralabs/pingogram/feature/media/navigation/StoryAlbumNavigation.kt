package dev.kyralabs.pingogram.feature.media.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.navigation.StoryAlbumPickerRoute
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.feature.media.storyalbum.StoryAlbumPickerRoute as StoryAlbumPicker
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

/**
 * Adds picking stories for one of the user's albums, [StoryAlbumPickerRoute] (owner decision 2026-11-20), to the graph;
 * [onDone] leaves it once the album was created or changed, [onClose] without a change.
 */
fun NavGraphBuilder.storyAlbumPickerScreen(
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    composable<StoryAlbumPickerRoute> { StoryAlbumPicker(onDone = onDone, onClose = onClose) }
}

/** Picks the stories of a new album [name] of chat [chatId], the user's own. */
fun NavController.navigateToNewStoryAlbum(
    chatId: Long,
    name: String,
) {
    navigate(StoryAlbumPickerRoute(chatId, name = name)) { launchSingleTop = true }
}

/** Picks stories to add to album [albumId] of chat [chatId], or with [removing] to take out of it. */
fun NavController.navigateToStoryAlbumPicker(
    chatId: Long,
    albumId: Int,
    removing: Boolean,
) {
    navigate(StoryAlbumPickerRoute(chatId, albumId, removing = removing)) { launchSingleTop = true }
}
