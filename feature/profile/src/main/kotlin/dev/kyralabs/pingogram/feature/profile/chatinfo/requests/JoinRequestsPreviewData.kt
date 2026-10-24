package dev.kyralabs.pingogram.feature.profile.chatinfo.requests

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf

/** The states of the join requests page, shared by previews and screenshot tests. */
internal object JoinRequestsPreviewData {
    private val requests =
        persistentListOf(
            JoinRequestItem(1, UiText.Plain("Ada Lovelace"), null, "Mathematician and writer", "Oct 22"),
            JoinRequestItem(2, UiText.Plain("Grace Hopper"), null, "", "Oct 21"),
            JoinRequestItem(3, UiText.Plain("Alan Turing"), null, "Codebreaker, runner", "Oct 18"),
        )

    val content = JoinRequestsUiState.Content(requests, totalCount = 12, isChannel = false)

    val channel = content.copy(requests = persistentListOf(requests.first()), totalCount = 1, isChannel = true)

    val busy = content.copy(busy = persistentSetOf(2L))

    val dismissAll = content.copy(dismissAllDialog = true)

    val pageError = content.copy(pageError = DomainError.Network)

    val empty = JoinRequestsUiState.Content(persistentListOf(), totalCount = 0, isChannel = false)

    val error = JoinRequestsUiState.Error(DomainError.Network)
}
