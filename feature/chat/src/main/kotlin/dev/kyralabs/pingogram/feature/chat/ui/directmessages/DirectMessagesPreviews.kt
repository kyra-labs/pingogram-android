package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import kotlinx.collections.immutable.persistentListOf

/** The states of [DirectMessagesScreen] for previews and screenshot tests (the owner's board 7b). */
internal object DirectMessagesPreviewData {
    private val header =
        ChatHeader(
            chatId = -1_000_000_000_777L,
            title = UiText.Plain("Kyra Labs Updates"),
            photo = null,
            placeholderIcon = null,
            subtitle =
                ChatSubtitle.Status(
                    UiText.plural(
                        R.plurals.direct_subtitle_unread,
                        4,
                        UiText.plural(R.plurals.direct_subtitle_people, 12, 12),
                        4,
                    ),
                ),
            avatarShape = AvatarShape.Channel,
        )

    private fun row(
        id: Long,
        name: String,
        preview: String,
        time: String,
        unread: Int = 0,
        paid: Boolean = false,
    ) = DirectRow(
        topicId = id,
        name = UiText.Plain(name),
        photo = null,
        avatarId = id,
        avatarShape = AvatarShape.Circle,
        preview = UiText.Plain(preview),
        time = time,
        unreadCount = unread,
        isUnread = unread > 0,
        isPaid = paid,
    )

    val rows =
        DirectMessagesUiState(
            header = header,
            total = 12,
            unread = 4,
            content =
                DirectContent.Rows(
                    persistentListOf(
                        row(1, "Riya Sharma", "Is the beta open to everyone yet?", "9:41 AM", unread = 2),
                        row(2, "Owen Park", "Thanks, the fix works on my Pixel", "9:12 AM", unread = 1, paid = true),
                        row(3, "Lena Fischer", "Could you add a dark theme preview?", "Yesterday"),
                        row(4, "Arjun Mehta", "Loved the new release notes", "Mon", paid = true),
                        row(5, "Sahil Rao", "Where do I report a crash?", "Mon"),
                    ),
                    isLoadingMore = false,
                ),
        )

    val empty = DirectMessagesUiState(header = header, content = DirectContent.Empty(isFiltered = false))

    val filteredEmpty =
        DirectMessagesUiState(
            header = header,
            filter = DirectFilter.Paid,
            total = 12,
            unread = 4,
            content = DirectContent.Empty(isFiltered = true),
        )

    val failed = DirectMessagesUiState(header = header, content = DirectContent.Failed(DomainError.Network))

    val loading = DirectMessagesUiState(header = header)
}

internal class DirectMessagesPreviewStates : PreviewParameterProvider<DirectMessagesUiState> {
    override val values =
        with(DirectMessagesPreviewData) { sequenceOf(rows, empty, filteredEmpty, failed, loading) }
}

@PingogramPreviews
@Composable
private fun DirectMessagesScreenPreview(
    @PreviewParameter(DirectMessagesPreviewStates::class) state: DirectMessagesUiState,
) {
    PingogramPreview { DirectMessagesScreen(state = state, onEvent = {}, onOpenTopic = {}, onBack = {}) }
}
