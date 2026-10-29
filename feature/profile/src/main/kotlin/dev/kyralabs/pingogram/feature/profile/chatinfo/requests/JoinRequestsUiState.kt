package dev.kyralabs.pingogram.feature.profile.chatinfo.requests

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** What the join requests page of a group or channel shows (`REQUIREMENTS.md` FR-P3). */
@Immutable
sealed interface JoinRequestsUiState {
    data object Loading : JoinRequestsUiState

    data class Error(
        val error: DomainError,
    ) : JoinRequestsUiState

    /**
     * @property requests the requests loaded so far, newest first.
     * @property totalCount how many wait in all.
     * @property isChannel the chat is a channel (subscribers rather than members).
     * @property busy the requests whose answer is on its way.
     * @property loadingMore the next page loads; [pageError] why it did not.
     * @property dismissAllDialog "Dismiss all" waits for its confirmation.
     * @property isProcessingAll every request is being approved or dismissed.
     */
    data class Content(
        val requests: ImmutableList<JoinRequestItem>,
        val totalCount: Int,
        val isChannel: Boolean,
        val busy: ImmutableSet<Long> = persistentSetOf(),
        val loadingMore: Boolean = false,
        val pageError: DomainError? = null,
        val dismissAllDialog: Boolean = false,
        val isProcessingAll: Boolean = false,
    ) : JoinRequestsUiState {
        val hasMore: Boolean get() = requests.size < totalCount
    }
}

/** A request's row: who asked ([name], [photo]), their [bio] and when ([date], "Oct 30"). */
@Immutable
data class JoinRequestItem(
    val userId: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    val bio: String,
    val date: String,
) {
    override fun toString() = "JoinRequestItem(userId=$userId, …)"
}

sealed interface JoinRequestsEvent {
    data class Approve(
        val userId: Long,
    ) : JoinRequestsEvent

    data class Dismiss(
        val userId: Long,
    ) : JoinRequestsEvent

    data object ApproveAll : JoinRequestsEvent

    /** "Dismiss all": asks first. */
    data object DismissAll : JoinRequestsEvent

    data object ConfirmDismissAll : JoinRequestsEvent

    data object CancelDismissAll : JoinRequestsEvent

    data object LoadMore : JoinRequestsEvent

    data object Retry : JoinRequestsEvent

    /** A person's row: their profile opens. */
    data class Open(
        val userId: Long,
    ) : JoinRequestsEvent
}

sealed interface JoinRequestsEffect {
    data class ShowError(
        val error: DomainError,
    ) : JoinRequestsEffect

    /** [name] joined the chat, said in a snackbar. */
    data class Approved(
        val name: UiText,
    ) : JoinRequestsEffect

    data class OpenProfile(
        val userId: Long,
    ) : JoinRequestsEffect
}
