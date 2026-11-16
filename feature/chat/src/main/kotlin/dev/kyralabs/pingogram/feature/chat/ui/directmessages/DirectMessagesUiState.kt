package dev.kyralabs.pingogram.feature.chat.ui.directmessages

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatHeader
import kotlinx.collections.immutable.ImmutableList

/**
 * A channel's direct messages as its administrators see them (the owner's board 7b): the channel in the top bar
 * ([header]), the [filter] chosen with its counts ([total] people, [unread] of them unread), and the [content].
 */
@Immutable
data class DirectMessagesUiState(
    val header: ChatHeader? = null,
    val filter: DirectFilter = DirectFilter.All,
    val total: Int = 0,
    val unread: Int = 0,
    val content: DirectContent = DirectContent.Loading,
)

/** Which conversations show: every one, those with unread messages, or those whose sender pays per message. */
enum class DirectFilter(
    @param:StringRes val label: Int,
) {
    All(R.string.direct_filter_all),
    Unread(R.string.direct_filter_unread),
    Paid(R.string.direct_filter_paid),
    ;

    companion object {
        /** The filter named [name], as saved; [All] for anything else. */
        fun of(name: String?): DirectFilter = entries.firstOrNull { it.name == name } ?: All
    }
}

/** What the list shows. */
@Immutable
sealed interface DirectContent {
    data object Loading : DirectContent

    /** No conversation (yet), or none under the filter and the search ([isFiltered]). */
    data class Empty(
        val isFiltered: Boolean,
    ) : DirectContent

    data class Failed(
        val error: DomainError,
    ) : DirectContent

    /** The [rows], with a page loading below them ([isLoadingMore]). */
    data class Rows(
        val rows: ImmutableList<DirectRow>,
        val isLoadingMore: Boolean,
    ) : DirectContent
}

/**
 * One person's conversation with the channel: who wrote ([name], their [photo] or initials of [avatarId] in
 * [avatarShape]), their last message ([preview], [time]), how many of their messages are unread ([unreadCount], or
 * marked as unread: [isUnread]), and whether they pay Telegram Stars per message ([isPaid]).
 */
@Immutable
data class DirectRow(
    val topicId: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val avatarId: Long,
    val avatarShape: AvatarShape,
    val preview: UiText?,
    val time: String?,
    val unreadCount: Int,
    val isUnread: Boolean,
    val isPaid: Boolean,
) {
    override fun toString() = "DirectRow(topicId=$topicId, unreadCount=$unreadCount, isPaid=$isPaid, …)"
}

/** What the list's user does. */
sealed interface DirectMessagesEvent {
    data class FilterChanged(
        val filter: DirectFilter,
    ) : DirectMessagesEvent

    data class QueryChanged(
        val query: String,
    ) : DirectMessagesEvent

    /** Marks a conversation as unread, or as read again. */
    data class MarkUnread(
        val topicId: Long,
        val unread: Boolean,
    ) : DirectMessagesEvent

    /** The list's end came near: the next page. */
    data object LoadMore : DirectMessagesEvent

    data object Retry : DirectMessagesEvent
}
