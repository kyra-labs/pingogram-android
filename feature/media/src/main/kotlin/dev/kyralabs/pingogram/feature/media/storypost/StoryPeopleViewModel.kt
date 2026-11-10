package dev.kyralabs.pingogram.feature.media.storypost

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

/** What "Selected people" shows (`docs/DESIGN_SYSTEM.md` §6 "New story: who sees it"). */
@Immutable
internal sealed interface StoryPeopleUiState {
    data object Loading : StoryPeopleUiState

    data class Error(
        val error: DomainError,
    ) : StoryPeopleUiState

    data class Content(
        /** The contacts that match the query, in the order the contacts list sorts them. */
        val candidates: ImmutableList<StoryPerson>,
        /** How many are picked, also among those the query hides. */
        val pickedCount: Int,
        /** The user has contacts, and the query hides them all. */
        val noMatches: Boolean = false,
    ) : StoryPeopleUiState
}

/** A contact who may see the story: their name, photo, and whether they are picked. */
@Immutable
internal data class StoryPerson(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
    val isPicked: Boolean,
)

/**
 * The people picker of a story's "Selected people" (board 10b, `docs/adr/0036-posting-stories.md`): the user's
 * contacts from [ContactRepository] (people only, no bots), filtered on the device by the query, starting with those
 * the story was already for ([start]). The query and the picks are in the saved state until the sheet closes
 * ([reset]); [picked] goes to the composer.
 */
@HiltViewModel
internal class StoryPeopleViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        contacts: ContactRepository,
    ) : ViewModel() {
        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

        private val pickedIds = savedStateHandle.getStateFlow(KEY_PICKED, LongArray(0))

        val uiState: StateFlow<StoryPeopleUiState> =
            combine(contacts.contacts(), query, pickedIds) { outcome, query, picked ->
                when (outcome) {
                    is Outcome.Failure -> StoryPeopleUiState.Error(outcome.error)
                    is Outcome.Success -> content(outcome.value, query, picked.toSet())
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoryPeopleUiState.Loading)

        /** The picked users, in the order they were picked. */
        val picked: List<Long> get() = pickedIds.value.toList()

        /** Picks [userIds] as the sheet opens: who the story is for already. Once per opening, not after a rotation. */
        fun start(userIds: List<Long>) {
            if (savedStateHandle.get<Boolean>(KEY_STARTED) == true) return
            savedStateHandle[KEY_STARTED] = true
            savedStateHandle[KEY_PICKED] = userIds.toLongArray()
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
            savedStateHandle[KEY_STARTED] = false
        }

        private fun content(
            contacts: List<User>,
            query: String,
            picked: Set<Long>,
        ): StoryPeopleUiState.Content {
            val people = contacts.filter { it.type == UserType.Regular }
            val needle = query.trim().lowercase(Locale.ROOT)
            val shown = if (needle.isEmpty()) people else people.filter { it.matches(needle) }
            return StoryPeopleUiState.Content(
                candidates =
                    shown
                        .map { StoryPerson(it.id, it.fullName(), it.profilePhoto, it.id in picked) }
                        .toImmutableList(),
                pickedCount = picked.count { id -> people.any { it.id == id } },
                noMatches = shown.isEmpty() && people.isNotEmpty(),
            )
        }

        private companion object {
            const val KEY_QUERY = "storyPeople.query"
            const val KEY_PICKED = "storyPeople.picked"
            const val KEY_STARTED = "storyPeople.started"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun User.fullName(): String = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

/** The name has a word starting with [needle], or a username starts with it. */
private fun User.matches(needle: String): Boolean =
    fullName().lowercase(Locale.ROOT).let { name -> name.startsWith(needle) || name.contains(" $needle") } ||
        usernames.any { it.lowercase(Locale.ROOT).startsWith(needle.removePrefix("@")) }
