package dev.kyralabs.pingogram.feature.chat.ui.preview

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearchUiState
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchHighlight
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchResults
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchSender
import dev.kyralabs.pingogram.feature.chat.ui.search.SenderOption
import dev.kyralabs.pingogram.feature.chat.ui.search.SenderSheet
import dev.kyralabs.pingogram.feature.chat.ui.search.withSearchMatch
import kotlinx.collections.immutable.persistentListOf

/**
 * The search in the chat for previews and screenshot tests: "motion" searched in the group of
 * [ChatPreviewData.groupChat], its second of three results shown and marked in its bubble; the bar while it searches,
 * found nothing, failed, and limited to Grace's messages; the members' sheet.
 */
internal object ChatSearchPreviewData {
    private const val GRACE = 102L
    private const val LINUS = 103L
    private const val ME = 1L

    /** The words searched, which [groupChat]'s bubble 24 marks. */
    const val QUERY = "motion"

    val found =
        ChatSearchUiState(
            results = SearchResults.Found(2, 3, isTotalApproximate = false, hasOlder = true, hasNewer = true),
            canPickSender = true,
        )

    val searching = ChatSearchUiState(SearchResults.Searching, canPickSender = true)

    val noResults = ChatSearchUiState(SearchResults.NoResults, canPickSender = true)

    val failed = ChatSearchUiState(SearchResults.Failed(DomainError.Network), canPickSender = true)

    /** Grace's messages only: the first of many. */
    val withSender =
        ChatSearchUiState(
            results = SearchResults.Found(1, 50, isTotalApproximate = true, hasOlder = true, hasNewer = false),
            sender = SearchSender(MessageSender.User(GRACE), GRACE, UiText.Plain("Grace Hopper"), photo = null),
            canPickSender = true,
        )

    /** The group conversation with bubble 24, "…the expressive motion scheme…", the result shown. */
    val groupChat: ChatUiState =
        ChatPreviewData.groupChat.withSearchMatch(SearchHighlight(messageId = 24, MatchHighlight.of(QUERY)))

    val senders =
        SenderSheet(
            persistentListOf(
                option(ME, "Ada Lovelace", R.string.search_sender_owner),
                option(GRACE, "Grace Hopper", R.string.search_sender_admin, selected = true),
                option(LINUS, "Linus Torvalds"),
            ),
        )

    val sendersFailed = SenderSheet(persistentListOf(), error = DomainError.Network)

    private fun option(
        id: Long,
        name: String,
        role: Int? = null,
        selected: Boolean = false,
    ) = SenderOption(
        sender = MessageSender.User(id),
        id = id,
        name = UiText.Plain(name),
        photo = null,
        subtitle = role?.let { UiText.resource(it) },
        selected = selected,
    )
}
