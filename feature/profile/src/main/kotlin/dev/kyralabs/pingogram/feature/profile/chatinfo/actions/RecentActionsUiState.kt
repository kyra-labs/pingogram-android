package dev.kyralabs.pingogram.feature.profile.chatinfo.actions

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ChatEventFilter
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.profile.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

/** What the recent actions page of a supergroup or channel shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface RecentActionsUiState {
    data object Loading : RecentActionsUiState

    data class Error(
        val error: DomainError,
    ) : RecentActionsUiState

    /**
     * @property filters the kinds of actions shown; none shows every kind.
     * @property actions the actions loaded so far, newest first.
     * @property isLoading the actions of [filters] load (the chips changed).
     * @property hasMore older actions may follow; [loadingMore] while they load, [pageError] why they did not.
     */
    data class Content(
        val filters: ImmutableSet<RecentActionsFilter>,
        val actions: ImmutableList<RecentActionItem>,
        val isLoading: Boolean = false,
        val hasMore: Boolean = false,
        val loadingMore: Boolean = false,
        val pageError: DomainError? = null,
    ) : RecentActionsUiState {
        /** Older actions may be asked for now. */
        val canLoadMore: Boolean get() = hasMore && !loadingMore && !isLoading
    }
}

/**
 * An action's row: who ([name], [photo] or initials of [avatarId]) did what ([text]) when ([time]), with the message
 * it concerns ([message], one line) where there is one. [userId] opens their profile (0: a chat did it).
 */
@Immutable
data class RecentActionItem(
    val id: Long,
    val userId: Long,
    val avatarId: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val text: UiText,
    val message: UiText?,
    val time: String,
) {
    override fun toString() = "RecentActionItem(id=$id, …)"
}

/** The page's filter chips, each a few of TDLib's kinds of action. */
enum class RecentActionsFilter(
    @param:StringRes val label: Int,
    val kinds: Set<ChatEventFilter>,
) {
    Messages(
        R.string.recent_actions_filter_messages,
        setOf(ChatEventFilter.MessageEdits, ChatEventFilter.MessageDeletions, ChatEventFilter.MessagePins),
    ),
    Members(
        R.string.recent_actions_filter_members,
        setOf(ChatEventFilter.MemberJoins, ChatEventFilter.MemberLeaves, ChatEventFilter.MemberInvites),
    ),
    Admins(R.string.recent_actions_filter_admins, setOf(ChatEventFilter.MemberPromotions)),
    Restrictions(R.string.recent_actions_filter_restrictions, setOf(ChatEventFilter.MemberRestrictions)),
    Settings(
        R.string.recent_actions_filter_settings,
        setOf(ChatEventFilter.InfoChanges, ChatEventFilter.SettingChanges, ChatEventFilter.TopicChanges),
    ),
    Links(R.string.recent_actions_filter_links, setOf(ChatEventFilter.InviteLinkChanges)),
    VoiceChats(R.string.recent_actions_filter_voice_chats, setOf(ChatEventFilter.VideoChatChanges)),
}

sealed interface RecentActionsEvent {
    data class ToggleFilter(
        val filter: RecentActionsFilter,
    ) : RecentActionsEvent

    data object LoadMore : RecentActionsEvent

    data object Retry : RecentActionsEvent

    data class Open(
        val userId: Long,
    ) : RecentActionsEvent
}

sealed interface RecentActionsEffect {
    data class OpenProfile(
        val userId: Long,
    ) : RecentActionsEffect
}
