package dev.kyralabs.pingogram.feature.chats.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.NewChatUiState
import dev.kyralabs.pingogram.feature.chats.ui.SearchChatItem
import dev.kyralabs.pingogram.feature.chats.ui.TitleBadge
import kotlinx.collections.immutable.persistentListOf

/**
 * Sample states of the New chat screen, shared by the previews and the screenshot tests: contacts (one online, one
 * verified, some with a username), loading, no contacts, and an error.
 */
internal object NewChatPreviewData {
    private fun contact(
        id: Long,
        name: String,
        username: String? = null,
        isOnline: Boolean = false,
        badge: TitleBadge? = null,
    ) = SearchChatItem(
        id = id,
        title = UiText.Plain(name),
        photo = null,
        placeholderIcon = null,
        isOnline = isOnline,
        badge = badge,
        isSecret = false,
        isMuted = false,
        subtitle = username?.let { UiText.resource(R.string.search_username, it) },
    )

    val content =
        NewChatUiState.Content(
            persistentListOf(
                contact(2, "Ada Lovelace", username = "ada", isOnline = true),
                contact(11, "Alan Turing", username = "turing"),
                contact(4, "Grace Hopper", badge = TitleBadge.Verified),
                contact(12, "Hedy Lamarr", username = "hedy"),
                contact(13, "Katherine Johnson"),
                contact(14, "Margaret Hamilton", username = "margaret", isOnline = true),
            ),
        )

    val loading = NewChatUiState.Loading

    val empty = NewChatUiState.Empty

    val error = NewChatUiState.Error(DomainError.Network)
}
