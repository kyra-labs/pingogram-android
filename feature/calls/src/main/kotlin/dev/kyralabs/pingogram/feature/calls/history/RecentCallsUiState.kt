package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.CallOutcome
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList

/** Which calls Recent calls lists: all of them, or the missed ones (td_api.tl `searchCallMessages.only_missed`). */
enum class CallFilter { All, Missed }

/** Recent calls: the filter and what shows below it. */
@Immutable
data class RecentCallsUiState(
    val filter: CallFilter,
    val content: RecentCallsContent,
)

@Immutable
sealed interface RecentCallsContent {
    /** The first page is on its way. */
    data object Loading : RecentCallsContent

    /** No calls, or no missed ones. */
    data object Empty : RecentCallsContent

    /** The first page failed; "Try again" asks again. */
    data class Error(
        val error: DomainError,
    ) : RecentCallsContent

    /**
     * The calls by day, newest first. [loadingMore] while the next page is on its way, [pageError] when it failed;
     * [reachedEnd] once there is none.
     */
    data class Calls(
        val items: ImmutableList<RecentCallsItem>,
        val loadingMore: Boolean,
        val pageError: DomainError?,
        val reachedEnd: Boolean,
    ) : RecentCallsContent
}

/** A row of the list: a day's header, or a call. */
@Immutable
sealed interface RecentCallsItem {
    val key: String

    /** "Today", "Yesterday", "Monday, March 17". */
    data class Day(
        override val key: String,
        val label: UiText,
    ) : RecentCallsItem

    /**
     * One call with [name] (the user's, or "Deleted account"), [photo] for the avatar, how it went ([outcome],
     * [isOutgoing], [isVideo]) worded as [kind] ("Missed video call"), its [time] of day and, once answered, its
     * [duration] ("4:05").
     */
    data class Call(
        override val key: String,
        val chatId: Long,
        val messageId: Long,
        val userId: Long,
        val name: UiText,
        val photo: ProfilePhoto?,
        val outcome: CallOutcome,
        val isOutgoing: Boolean,
        val isVideo: Boolean,
        val kind: UiText,
        val time: String,
        val duration: String?,
    ) : RecentCallsItem
}

sealed interface RecentCallsEvent {
    data class SelectFilter(
        val filter: CallFilter,
    ) : RecentCallsEvent

    /** Calls the person back, a video call for a video call. */
    data class CallBack(
        val call: RecentCallsItem.Call,
    ) : RecentCallsEvent

    data class Delete(
        val call: RecentCallsItem.Call,
    ) : RecentCallsEvent

    /** The list neared its end. */
    data object LoadMore : RecentCallsEvent

    /** "Try again", after the first page or the next one failed. */
    data object Retry : RecentCallsEvent
}

sealed interface RecentCallsEffect {
    /** Start a call to [userId], with the camera on for [isVideo]. */
    data class Call(
        val userId: Long,
        val isVideo: Boolean,
    ) : RecentCallsEffect

    /** A call left the history. */
    data object Deleted : RecentCallsEffect

    /** Deleting failed with [error]; the call is back in the list. */
    data class DeleteFailed(
        val error: DomainError,
    ) : RecentCallsEffect
}
