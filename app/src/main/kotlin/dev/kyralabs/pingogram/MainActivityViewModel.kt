package dev.kyralabs.pingogram

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Whether the account is logged in ([uiState]), from TDLib's login steps, and how long the splash screen stays
 * ([keepSplashScreen]). Nothing to restore after process death: TDLib reports the current step again.
 */
@HiltViewModel
class MainActivityViewModel internal constructor(
    authRepository: AuthRepository,
    timeSource: TimeSource,
) : ViewModel() {
    @Inject
    constructor(authRepository: AuthRepository) : this(authRepository, TimeSource.Monotonic)

    private val createdAt = timeSource.markNow()

    val uiState: StateFlow<MainActivityUiState> =
        authRepository.authStep
            .mapNotNull { it.toMainActivityUiState() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, MainActivityUiState.Loading)

    /**
     * Whether the splash screen stays on screen: while the login state is unknown, but no longer than
     * [SPLASH_SCREEN_TIMEOUT] after this view model was created (`docs/PERFORMANCE.md` §2); `PingogramApp` shows
     * progress after that. The splash screen asks before every frame it holds back.
     */
    fun keepSplashScreen(): Boolean =
        uiState.value == MainActivityUiState.Loading && createdAt.elapsedNow() < SPLASH_SCREEN_TIMEOUT

    internal companion object {
        val SPLASH_SCREEN_TIMEOUT = 300.milliseconds
    }
}
