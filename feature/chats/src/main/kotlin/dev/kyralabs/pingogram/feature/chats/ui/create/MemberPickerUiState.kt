package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Picking people from the contacts to add to a new group or channel (`REQUIREMENTS.md` FR-P4): the search [query] as
 * typed, the contacts it shows ([contacts]) and whom the user picked so far ([picked], in the order they were picked).
 */
@Immutable
data class MemberPickerUiState(
    val query: String = "",
    val contacts: MemberList = MemberList.Loading,
    val picked: ImmutableList<MemberItem> = persistentListOf(),
) {
    override fun toString() = "MemberPickerUiState(contacts=$contacts, picked=${picked.size})"
}

/** The contacts of a [MemberPickerUiState]. */
@Immutable
sealed interface MemberList {
    /** The contacts are on their way. */
    data object Loading : MemberList

    /** The user has no contacts to pick from. */
    data object Empty : MemberList

    /** No contact matches the query. */
    data object NoResults : MemberList

    /** Listing the contacts failed with [error]: shown with a retry ([MemberPickerEvent.Retry]). */
    data class Error(
        val error: DomainError,
    ) : MemberList

    /** The contacts to show, sorted by name, those matching the query while there is one. */
    data class Content(
        val items: ImmutableList<MemberItem>,
    ) : MemberList {
        override fun toString() = "Content(${items.size})"
    }
}

/**
 * A contact to pick: [name] as the user saved it, its photo, the online dot, its first username (without `@`), if any,
 * and whether it is picked ([isPicked]).
 */
@Immutable
data class MemberItem(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val isOnline: Boolean,
    val username: String?,
    val isPicked: Boolean,
) {
    override fun toString() = "MemberItem(userId=$userId, isPicked=$isPicked, …)"
}

/** What the user does while picking people, sent to the flow's view model. */
sealed interface MemberPickerEvent {
    data class QueryChanged(
        val query: String,
    ) : MemberPickerEvent

    /** A contact's row was tapped: picks or unpicks [userId]. */
    data class Toggle(
        val userId: Long,
    ) : MemberPickerEvent

    /** A picked contact's chip was tapped: unpicks [userId]. */
    data class Remove(
        val userId: Long,
    ) : MemberPickerEvent

    /** "Try again" after listing the contacts failed. */
    data object Retry : MemberPickerEvent
}
