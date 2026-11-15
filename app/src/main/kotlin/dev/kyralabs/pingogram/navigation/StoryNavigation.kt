package dev.kyralabs.pingogram.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import dev.kyralabs.pingogram.core.domain.model.SharedContent
import dev.kyralabs.pingogram.core.domain.model.SharedStory
import dev.kyralabs.pingogram.core.navigation.NewStoryRoute
import dev.kyralabs.pingogram.core.navigation.StoryAlbumRoute
import dev.kyralabs.pingogram.core.navigation.StoryViewerRoute
import dev.kyralabs.pingogram.feature.chat.ui.share.ShareInbox
import dev.kyralabs.pingogram.feature.media.navigation.navigateToStoryAlbumViewer
import dev.kyralabs.pingogram.feature.media.navigation.newStoryScreen
import dev.kyralabs.pingogram.feature.media.navigation.storyAlbumScreen
import dev.kyralabs.pingogram.feature.media.navigation.storyViewerScreen

/**
 * The stories viewer, posting stories and an opened story album, in the main graph. The viewer's share button opens the
 * share sheet above it through [shares] (board 26), where the story goes to the chats picked.
 */
internal fun NavGraphBuilder.stories(
    navController: NavHostController,
    shares: ShareInbox? = null,
) {
    storyViewerScreen(
        // The viewer and nothing below it: a second close, e.g. a swipe while its exit plays, does nothing.
        onClose = { navController.popBackStack<StoryViewerRoute>(inclusive = true) },
        onShare = { posterChatId, storyId ->
            val story = SharedContent(story = SharedStory(posterChatId, storyId))
            shares?.let { navController.openShare(story, chatId = null, inbox = it, fromOutside = false) }
        },
    )
    newStoryScreen(onClose = { navController.popBackStack<NewStoryRoute>(inclusive = true) })
    storyAlbumScreen(
        onBack = { navController.popBackStack<StoryAlbumRoute>(inclusive = true) },
        onOpenStory = navController::navigateToStoryAlbumViewer,
    )
}
