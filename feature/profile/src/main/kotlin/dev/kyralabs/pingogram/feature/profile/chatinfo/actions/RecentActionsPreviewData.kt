package dev.kyralabs.pingogram.feature.profile.chatinfo.actions

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of the recent actions page, shared by previews and screenshot tests. */
internal object RecentActionsPreviewData {
    private val actions =
        persistentListOf(
            RecentActionItem(
                id = 5,
                userId = 1,
                avatarId = 1,
                name = UiText.Plain("Ada Lovelace"),
                photo = null,
                text = UiText.resource(R.string.recent_actions_title, UiText.Plain("Rust Berlin")),
                message = null,
                time = "Oct 24, 14:05",
            ),
            RecentActionItem(
                id = 4,
                userId = 2,
                avatarId = 2,
                name = UiText.Plain("Grace Hopper"),
                photo = null,
                text = UiText.resource(R.string.recent_actions_deleted),
                message = UiText.Plain("Meetup moved to Thursday, same place as last time"),
                time = "Oct 24, 11:40",
            ),
            RecentActionItem(
                id = 3,
                userId = 1,
                avatarId = 1,
                name = UiText.Plain("Ada Lovelace"),
                photo = null,
                text = UiText.resource(R.string.recent_actions_promoted, UiText.Plain("Alan Turing")),
                message = null,
                time = "Oct 23, 19:12",
            ),
            RecentActionItem(
                id = 2,
                userId = 4,
                avatarId = 4,
                name = UiText.Plain("Linus Torvalds"),
                photo = null,
                text = UiText.resource(R.string.recent_actions_joined_link),
                message = null,
                time = "Oct 23, 09:30",
            ),
        )

    val content = RecentActionsUiState.Content(persistentSetOf(), actions, hasMore = true)

    val filtered = content.copy(filters = persistentSetOf(RecentActionsFilter.Messages, RecentActionsFilter.Admins))

    val filtering = filtered.copy(isLoading = true)

    val pageError = content.copy(pageError = DomainError.Network)

    val empty = RecentActionsUiState.Content(persistentSetOf(RecentActionsFilter.VoiceChats), persistentListOf())

    val error = RecentActionsUiState.Error(DomainError.Network)
}
