package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.showsMessage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * The search in the conversation while it is open (`REQUIREMENTS.md` §5.9): the bar in the composer's place shows
 * [results], the sender it is limited to ([sender]), and whether the chat has senders to pick ([canPickSender]:
 * groups; channels post as themselves, private chats have two people).
 */
@Immutable
data class ChatSearchUiState(
    val results: SearchResults = SearchResults.Idle,
    val sender: SearchSender? = null,
    val canPickSender: Boolean = false,
)

/** What a search found, for the bar's counter and its buttons. */
@Immutable
sealed interface SearchResults {
    /** Nothing to search yet: no words, and no sender. */
    data object Idle : SearchResults

    /** The first page is on its way. */
    data object Searching : SearchResults

    /** Nothing matches. */
    data object NoResults : SearchResults

    /** Searching failed with [error]: the bar offers to try again. */
    data class Failed(
        val error: DomainError,
    ) : SearchResults

    /**
     * Result [position] (1 for the newest) of [total] shows in the list; [isTotalApproximate] where TDLib did not say
     * how many there are and more pages may follow ("3 of 50+"). [hasOlder] and [hasNewer] enable the bar's buttons;
     * [isLoadingOlder] while the next page loads.
     */
    data class Found(
        val position: Int,
        val total: Int,
        val isTotalApproximate: Boolean,
        val hasOlder: Boolean,
        val hasNewer: Boolean,
        val isLoadingOlder: Boolean = false,
    ) : SearchResults
}

/** The sender a search is limited to: [name] and [photo] for the chip, [id] for its placeholder's colors. */
@Immutable
data class SearchSender(
    val sender: MessageSender,
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
) {
    override fun toString() = "SearchSender(sender=$sender, …)"
}

/**
 * The sheet of the chat's members to search the messages of (`ChatSearchEvent.OpenSenders`): [options] as its search
 * field finds them, null while they load; [error] when they could not be listed.
 */
@Immutable
data class SenderSheet(
    val options: ImmutableList<SenderOption>?,
    val error: DomainError? = null,
)

/**
 * A member in the [SenderSheet]: [name], [photo] (or [placeholderIcon] on the placeholder), [id] for the
 * placeholder's colors, [subtitle] ("online", "last seen …", "Admin"), [selected] when search is limited to them.
 */
@Immutable
data class SenderOption(
    val sender: MessageSender,
    val id: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int? = null,
    val subtitle: UiText? = null,
    val selected: Boolean = false,
) {
    override fun toString() = "SenderOption(sender=$sender, selected=$selected, …)"
}

/**
 * The state with the bubble of the result [highlight] shows marking its words ([ChatItem.Bubble.searchMatch]): only
 * that bubble is copied, so no other recomposes; the state as it is without a highlight.
 */
internal fun ChatUiState.withSearchMatch(highlight: SearchHighlight?): ChatUiState {
    val content = content as? ChatContent.Content
    if (highlight == null || content == null) return this
    var changed = false
    val items =
        content.items.map { item ->
            if (item is ChatItem.Bubble && item.showsMessage(highlight.messageId) && item.text.text.isNotEmpty()) {
                changed = true
                item.copy(searchMatch = highlight.terms)
            } else {
                item
            }
        }
    return if (changed) copy(content = content.copy(items = items.toImmutableList())) else this
}
