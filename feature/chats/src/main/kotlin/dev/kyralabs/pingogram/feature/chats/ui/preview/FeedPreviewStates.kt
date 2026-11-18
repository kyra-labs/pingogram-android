package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedContent
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedUiState
import kotlinx.collections.immutable.persistentListOf

/** The Channel feed's states, named, for its previews. */
internal class FeedPreviewStates : PreviewParameterProvider<FeedUiState> {
    private val data = FeedPreviewData

    private val states =
        listOf(
            "Unread and read" to
                FeedUiState(
                    content =
                        FeedContent.Posts(
                            unread = persistentListOf(data.text, data.photo, data.album),
                            read = persistentListOf(data.video, data.file, data.poll),
                        ),
                    unreadCount = 3,
                    channelCount = 14,
                ),
            "All caught up" to
                FeedUiState(
                    content =
                        FeedContent.Posts(
                            unread = persistentListOf(data.text.copy(isUnread = false)),
                            read = persistentListOf(data.video, data.file),
                            readHere = 1,
                        ),
                    channelCount = 14,
                ),
            "Error above saved posts" to
                FeedUiState(
                    content =
                        FeedContent.Posts(
                            unread = persistentListOf(data.text),
                            read = persistentListOf(data.video),
                        ),
                    unreadCount = 1,
                    channelCount = 14,
                    error = DomainError.Network,
                ),
            "Error" to FeedUiState(content = FeedContent.NoNewPosts, channelCount = 14, error = DomainError.Network),
            "Loading" to FeedUiState(content = FeedContent.Loading, channelCount = 14),
            "No new posts" to FeedUiState(content = FeedContent.NoNewPosts, channelCount = 14),
        )

    override val values: Sequence<FeedUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
