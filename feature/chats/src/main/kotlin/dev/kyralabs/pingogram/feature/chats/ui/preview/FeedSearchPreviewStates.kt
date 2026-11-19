package dev.kyralabs.pingogram.feature.chats.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.ui.SearchMessageItem
import dev.kyralabs.pingogram.feature.chats.ui.feed.FeedSearchUiState
import kotlinx.collections.immutable.persistentListOf

/** The Channel feed's search states, named, for its previews. */
internal class FeedSearchPreviewStates : PreviewParameterProvider<FeedSearchUiState> {
    private fun post(
        chatId: Long,
        messageId: Long,
        channel: String,
        text: String,
        date: String,
    ) = SearchMessageItem(
        chatId = chatId,
        messageId = messageId,
        chatTitle = UiText.Plain(channel),
        photo = null,
        placeholderIcon = null,
        badge = null,
        sender = null,
        preview = UiText.Plain(text),
        date = date,
        avatarShape = AvatarShape.Channel,
    )

    private val found =
        persistentListOf(
            post(-100_1, 11, "Daily Dev Digest", "Kotlin 2.4 release notes: what changed for coroutines", "9:41 AM"),
            post(-100_2, 21, "Android Weekly", "The release of Compose Material 3 1.5 brings expressive menus", "Mon"),
            post(-100_1, 12, "Daily Dev Digest", "Release candidates are out for the new build tools", "Nov 12"),
        )

    private val highlight = MatchHighlight.of("release")

    private val states =
        listOf(
            "Before a query" to FeedSearchUiState(),
            "Posts found" to FeedSearchUiState("release", found, highlight, canLoadMore = true),
            "Next page failed" to FeedSearchUiState("release", found, highlight, error = DomainError.Network),
            "Nothing found" to FeedSearchUiState("relase"),
        )

    override val values: Sequence<FeedSearchUiState> = states.asSequence().map { it.second }

    override fun getDisplayName(index: Int): String = states[index].first
}
