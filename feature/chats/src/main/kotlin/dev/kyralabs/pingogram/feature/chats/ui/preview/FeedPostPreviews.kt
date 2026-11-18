package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedPost

/** One Channel feed card for each kind of content, unread and read, for the card's previews. */
internal class FeedPostPreviews : PreviewParameterProvider<FeedPost> {
    override val values: Sequence<FeedPost> = FeedPreviewData.posts.asSequence()
}
