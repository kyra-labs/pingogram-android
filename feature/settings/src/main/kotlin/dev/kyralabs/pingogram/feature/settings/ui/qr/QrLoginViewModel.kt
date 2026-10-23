package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.repository.SessionRepository
import dev.kyralabs.pingogram.core.navigation.QrLoginRoute
import dev.kyralabs.pingogram.feature.settings.ui.SessionItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsTimeFormats
import dev.kyralabs.pingogram.feature.settings.ui.toItem
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the page that logs in another device by its QR code shows (`REQUIREMENTS.md` FR-P5). */
@Immutable
sealed interface QrLoginUiState {
    /**
     * The question, before anything is sent: [isConfirming] once "Log in" was tapped and Telegram has not answered,
     * [error] why the last try failed (an expired code, no network); null while it did not.
     */
    data class Confirm(
        val isConfirming: Boolean = false,
        val error: DomainError? = null,
    ) : QrLoginUiState

    /** The other device is logged in: [session], as Active sessions shows it. */
    data class Done(
        val session: SessionItem,
    ) : QrLoginUiState
}

sealed interface QrLoginEvent {
    /** "Log in": the user confirms, explicitly, that the code is from their own device. */
    data object Confirm : QrLoginEvent
}

/** One-off side effects of [QrLoginViewModel]: the haptics of Telegram's answer (`docs/DESIGN_SYSTEM.md` §5). */
sealed interface QrLoginEffect {
    data object Confirmed : QrLoginEffect

    data object Rejected : QrLoginEffect
}

/**
 * Logging in another device with its QR code login link [link] (`tg://login?token=…`, `REQUIREMENTS.md` FR-P5,
 * `docs/adr/0013-deep-links.md`): nothing is sent until the user taps "Log in" ([QrLoginEvent.Confirm]), however the
 * link came (scanned, or opened from another app); then [SessionRepository.confirmQrCodeLogin] once, and the new
 * session shows. An expired or used code says so (`DomainError.QrLoginExpired`); trying again is up to the user.
 *
 * After process death the question shows again: confirming a code twice only fails, as Telegram accepts it once.
 */
@HiltViewModel
class QrLoginViewModel internal constructor(
    private val link: String,
    private val sessionRepository: SessionRepository,
    private val timeFormats: SettingsTimeFormats,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        sessionRepository: SessionRepository,
        timeFormats: SettingsTimeFormats,
    ) : this(savedStateHandle.toRoute<QrLoginRoute>().link, sessionRepository, timeFormats)

    private val mutableUiState = MutableStateFlow<QrLoginUiState>(QrLoginUiState.Confirm())
    val uiState: StateFlow<QrLoginUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<QrLoginEffect>(Channel.BUFFERED)
    val effects: Flow<QrLoginEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: QrLoginEvent) {
        when (event) {
            QrLoginEvent.Confirm -> confirm()
        }
    }

    private fun confirm() {
        val state = mutableUiState.value as? QrLoginUiState.Confirm ?: return
        if (state.isConfirming) return
        mutableUiState.value = QrLoginUiState.Confirm(isConfirming = true)
        viewModelScope.launch {
            when (val confirmed = sessionRepository.confirmQrCodeLogin(link)) {
                is Outcome.Success -> {
                    val session = confirmed.value.toItem(timeFormats.formatter(), timeFormats.now())
                    mutableUiState.value = QrLoginUiState.Done(session)
                    effectChannel.send(QrLoginEffect.Confirmed)
                }

                is Outcome.Failure -> {
                    mutableUiState.value = QrLoginUiState.Confirm(error = confirmed.error)
                    effectChannel.send(QrLoginEffect.Rejected)
                }
            }
        }
    }
}
