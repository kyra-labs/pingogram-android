package dev.kyralabs.pingogram.feature.calls.ui.invite

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

/** What "Add people" shows (`docs/DESIGN_SYSTEM.md` §6 "Add people to a call"). */
@Immutable
sealed interface InvitePeopleUiState {
    data object Loading : InvitePeopleUiState

    data class Error(
        val error: DomainError,
    ) : InvitePeopleUiState

    data class Content(
        /** The contacts that match the query and are not in the call yet, sorted as the Contacts tab sorts them. */
        val candidates: ImmutableList<InviteCandidate>,
        /** How many are picked, also among those the query hides. */
        val pickedCount: Int,
        /** The user has contacts, and the query hides them all. */
        val noMatches: Boolean = false,
    ) : InvitePeopleUiState
}

/** A contact to invite: their name, photo, and whether they are picked. */
@Immutable
data class InviteCandidate(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val isPicked: Boolean,
)

/**
 * The contact picker of "Add people" in a conference call (`docs/adr/0025-conference-calls.md`): the user's contacts
 * from [ContactRepository], those already in the call left out ([setExcluded]), filtered on the device by the query.
 * The query and the picks are in the saved state; [picked] goes to the call's view model, which invites them.
 */
@HiltViewModel
class InvitePeopleViewModel
    @Inject
    internal constructor(
        private val savedStateHandle: SavedStateHandle,
        contacts: ContactRepository,
    ) : ViewModel() {
        private val excluded = MutableStateFlow<Set<Long>>(emptySet())

        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

        private val pickedIds = savedStateHandle.getStateFlow(KEY_PICKED, LongArray(0))

        val uiState: StateFlow<InvitePeopleUiState> =
            combine(contacts.contacts(), excluded, query, pickedIds) { outcome, excluded, query, picked ->
                when (outcome) {
                    is Outcome.Failure -> InvitePeopleUiState.Error(outcome.error)
                    is Outcome.Success -> content(outcome.value, excluded, query, picked.toSet())
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), InvitePeopleUiState.Loading)

        /** The picked users, in the order they were picked. */
        val picked: List<Long> get() = pickedIds.value.toList()

        /** Leaves [userIds] out: those in the call already. */
        fun setExcluded(userIds: Set<Long>) {
            excluded.value = userIds
            if (pickedIds.value.any { it in userIds }) {
                savedStateHandle[KEY_PICKED] = pickedIds.value.filterNot { it in userIds }.toLongArray()
            }
        }

        fun setQuery(query: String) {
            savedStateHandle[KEY_QUERY] = query
        }

        fun toggle(userId: Long) {
            val picked = pickedIds.value
            savedStateHandle[KEY_PICKED] =
                if (userId in picked) picked.filterNot { it == userId }.toLongArray() else picked + userId
        }

        /** Forgets the query and the picks: the sheet closed. */
        fun reset() {
            savedStateHandle[KEY_QUERY] = ""
            savedStateHandle[KEY_PICKED] = LongArray(0)
        }

        private fun content(
            contacts: List<User>,
            excluded: Set<Long>,
            query: String,
            picked: Set<Long>,
        ): InvitePeopleUiState.Content {
            val invitable = contacts.filter { it.type == UserType.Regular && it.id !in excluded }
            val needle = query.trim().lowercase(Locale.ROOT)
            val shown = if (needle.isEmpty()) invitable else invitable.filter { it.matches(needle) }
            return InvitePeopleUiState.Content(
                candidates =
                    shown
                        .map { InviteCandidate(it.id, it.fullName(), it.profilePhoto, it.id in picked) }
                        .toImmutableList(),
                pickedCount = picked.count { id -> invitable.any { it.id == id } },
                noMatches = shown.isEmpty() && invitable.isNotEmpty(),
            )
        }

        private companion object {
            const val KEY_QUERY = "invite.query"
            const val KEY_PICKED = "invite.picked"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun User.fullName(): String = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

/** The name has a word starting with [needle], or a username starts with it. */
private fun User.matches(needle: String): Boolean =
    fullName().lowercase(Locale.ROOT).let { name -> name.startsWith(needle) || name.contains(" $needle") } ||
        usernames.any { it.lowercase(Locale.ROOT).startsWith(needle.removePrefix("@")) }
