package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberFilter
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the members page of a group or channel shows (`REQUIREMENTS.md` FR-P3).
 *
 * @property kind the group's kind; null until it is known.
 * @property filter which members the list holds; [filters] those the user may choose between.
 * @property query what the user searches for; searching applies to every filter but administrators.
 * @property removing the member whose removal the page asks to confirm; null while it asks nothing.
 */
@Immutable
data class ChatMembersUiState(
    val kind: GroupKind? = null,
    val filter: MemberFilter = MemberFilter.Recent,
    val filters: ImmutableList<MemberFilter> = persistentListOf(MemberFilter.Recent),
    val query: String = "",
    val list: MembersList = MembersList.Loading,
    val removing: MemberItem? = null,
) {
    val canSearch: Boolean get() = filter != MemberFilter.Administrators

    override fun toString() = "ChatMembersUiState(kind=$kind, filter=$filter, list=$list, …)"
}

/** The list of the members page. */
@Immutable
sealed interface MembersList {
    data object Loading : MembersList

    /** Nobody matches the filter and the query. */
    data object Empty : MembersList

    /** The first page could not be loaded, because of [error]; [ChatMembersEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : MembersList

    /** The members loaded so far, how many match in all, and whether more can load. */
    data class Content(
        val items: ImmutableList<MemberItem>,
        val totalCount: Int,
        val more: MoreMembers = MoreMembers.None,
    ) : MembersList
}

/** The end of the members list. */
@Immutable
sealed interface MoreMembers {
    data object None : MoreMembers

    /** More can load, which the list asks for as its end comes near ([ChatMembersEvent.LoadMore]). */
    data object Available : MoreMembers

    data object Loading : MoreMembers

    /** The next page failed, because of [error]; [ChatMembersEvent.LoadMore] tries again. */
    data class Failed(
        val error: DomainError,
    ) : MoreMembers
}

/** What the user does on the members page, handled by [ChatMembersViewModel.onEvent]. */
sealed interface ChatMembersEvent {
    data class SelectFilter(
        val filter: MemberFilter,
    ) : ChatMembersEvent

    data class Search(
        val query: String,
    ) : ChatMembersEvent {
        override fun toString() = "Search(…)"
    }

    /** Load the first page again after it failed. */
    data object Retry : ChatMembersEvent

    /** The end of the list is near, or the next page failed and the user tries again. */
    data object LoadMore : ChatMembersEvent

    /** A member's row was tapped: open their profile, or a chat's conversation. */
    data class Open(
        val sender: MessageSender,
    ) : ChatMembersEvent

    /** Do [action] to member [sender], from their row's menu. */
    data class Act(
        val sender: MessageSender,
        val action: MemberAction,
    ) : ChatMembersEvent

    /** Remove the member the page asked about. */
    data object ConfirmRemove : ChatMembersEvent

    data object DismissDialog : ChatMembersEvent
}

/** One-off side effects of [ChatMembersViewModel], handled once by its route. */
sealed interface ChatMembersEffect {
    data class OpenProfile(
        val userId: Long,
    ) : ChatMembersEffect

    data class OpenChat(
        val chatId: Long,
    ) : ChatMembersEffect

    /** Open the rights page of member [userId]: their administrator rights, or their restrictions ([restrict]). */
    data class OpenRights(
        val userId: Long,
        val restrict: Boolean,
    ) : ChatMembersEffect

    /** A change was made to member [name]: a snackbar says [text] with the name. */
    data class Done(
        @param:StringRes val text: Int,
        val name: UiText,
    ) : ChatMembersEffect

    /** A change failed: a snackbar shows the error's text, none for [DomainError.Silent]. */
    data class ShowError(
        val error: DomainError,
    ) : ChatMembersEffect
}
