package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.ProfileRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.fullName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the page of the user's own QR code shows (`REQUIREMENTS.md` FR-P5). */
@Immutable
sealed interface MyQrCodeUiState {
    data object Loading : MyQrCodeUiState

    data class Error(
        val error: DomainError,
    ) : MyQrCodeUiState

    /**
     * The code of [link] with the user's [name] and photo; a [ProfileLink.isTemporary] link says for how long it works
     * and that a username makes it last.
     */
    data class Content(
        val userId: Long,
        val name: UiText,
        val photo: ProfilePhoto?,
        val link: ProfileLink,
    ) : MyQrCodeUiState {
        override fun toString() = "Content(userId=$userId, link=$link, …)"
    }
}

sealed interface MyQrCodeEvent {
    data object Retry : MyQrCodeEvent
}

/**
 * The user's own QR code (`REQUIREMENTS.md` FR-P5): the link to their profile from [ProfileRepository.profileLink]
 * (`t.me/<username>`, or a temporary one without a username), asked for when the page opens and on "Try again", with
 * their name and photo from [UserRepository], following changes (a new photo, a new name). The link is shared only
 * from the page, through Android's share sheet.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyQrCodeViewModel
    @Inject
    constructor(
        private val profileRepository: ProfileRepository,
        userRepository: UserRepository,
    ) : ViewModel() {
        private val link = MutableStateFlow<Outcome<ProfileLink>?>(null)

        private val me: Flow<User> =
            userRepository.myUserId
                .filterNotNull()
                .flatMapLatest { userRepository.user(it) }
                .filterNotNull()

        val uiState: StateFlow<MyQrCodeUiState> =
            combine(me, link) { user, link ->
                when (link) {
                    null -> {
                        MyQrCodeUiState.Loading
                    }

                    is Outcome.Failure -> {
                        MyQrCodeUiState.Error(link.error)
                    }

                    is Outcome.Success -> {
                        MyQrCodeUiState.Content(
                            user.id,
                            user.fullName(),
                            user.profilePhoto,
                            link.value,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MyQrCodeUiState.Loading)

        init {
            // After the page's first frame (docs/PERFORMANCE.md §4).
            viewModelScope.launch(Dispatchers.Main) { load() }
        }

        fun onEvent(event: MyQrCodeEvent) {
            when (event) {
                MyQrCodeEvent.Retry -> {
                    if (link.value is Outcome.Failure) {
                        link.value = null
                        viewModelScope.launch { load() }
                    }
                }
            }
        }

        private suspend fun load() {
            link.value = profileRepository.profileLink()
        }
    }

/** How long the state keeps being produced after the page stops collecting it, e.g. across a rotation. */
private const val STOP_TIMEOUT_MILLIS = 5_000L
