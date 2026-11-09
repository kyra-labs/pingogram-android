package dev.kyralabs.pingogram.feature.calls.history

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
import dev.kyralabs.pingogram.feature.calls.ui.CameraAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

/** What "Start a call" shows (`docs/DESIGN_SYSTEM.md` §6 "Recent calls"). */
@Immutable
sealed interface StartCallUiState {
    data object Loading : StartCallUiState

    data class Error(
        val error: DomainError,
    ) : StartCallUiState

    data class Content(
        /** The contacts that match the query, people only, sorted as the Contacts tab sorts them. */
        val people: ImmutableList<StartCallPerson>,
        /** The device has a camera, so the rows offer a video call. */
        val canVideoCall: Boolean,
        /** The user has contacts, and the query hides them all. */
        val noMatches: Boolean = false,
    ) : StartCallUiState
}

/** What "Start a call" leads to. */
internal sealed interface StartCallChoice {
    /** Calls [userId], with the camera on where [isVideo]. */
    data class Person(
        val userId: Long,
        val isVideo: Boolean,
    ) : StartCallChoice

    /** Starts a new group call, to invite people to from its screen. */
    data object GroupCall : StartCallChoice
}

/** A contact to call: their name and photo. */
@Immutable
data class StartCallPerson(
    val userId: Long,
    val name: String,
    val photo: ProfilePhoto?,
)

/**
 * "Start a call" of Recent calls: the user's contacts from [ContactRepository] (people, not bots), filtered on the
 * device by the query, which is kept in the saved state. The route calls the one a row's button names, or opens a new
 * group call; this view model asks Telegram for nothing but the contacts.
 */
@HiltViewModel
class StartCallViewModel
    @Inject
    internal constructor(
        private val savedStateHandle: SavedStateHandle,
        contacts: ContactRepository,
        camera: CameraAccess,
    ) : ViewModel() {
        private val hasCamera = camera.hasCamera()

        val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

        val uiState: StateFlow<StartCallUiState> =
            combine(contacts.contacts(), query) { outcome, query ->
                when (outcome) {
                    is Outcome.Failure -> StartCallUiState.Error(outcome.error)
                    is Outcome.Success -> content(outcome.value, query)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StartCallUiState.Loading)

        fun setQuery(query: String) {
            savedStateHandle[KEY_QUERY] = query
        }

        /** Forgets the query: the sheet closed. */
        fun reset() {
            savedStateHandle[KEY_QUERY] = ""
        }

        private fun content(
            contacts: List<User>,
            query: String,
        ): StartCallUiState.Content {
            val people = contacts.filter { it.type == UserType.Regular }
            val needle = query.trim().lowercase(Locale.ROOT)
            val shown = if (needle.isEmpty()) people else people.filter { it.matches(needle) }
            return StartCallUiState.Content(
                people = shown.map { StartCallPerson(it.id, it.fullName(), it.profilePhoto) }.toImmutableList(),
                canVideoCall = hasCamera,
                noMatches = shown.isEmpty() && people.isNotEmpty(),
            )
        }

        private companion object {
            const val KEY_QUERY = "start_call.query"
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

private fun User.fullName(): String = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

/** The name has a word starting with [needle], or a username starts with it. */
private fun User.matches(needle: String): Boolean =
    fullName().lowercase(Locale.ROOT).let { name -> name.startsWith(needle) || name.contains(" $needle") } ||
        usernames.any { it.lowercase(Locale.ROOT).startsWith(needle.removePrefix("@")) }
