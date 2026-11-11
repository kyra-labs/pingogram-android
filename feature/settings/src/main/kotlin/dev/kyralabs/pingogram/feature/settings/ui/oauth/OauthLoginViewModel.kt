package dev.kyralabs.pingogram.feature.settings.ui.oauth

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.ApplicationScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.OauthLoginRequest
import dev.kyralabs.pingogram.core.domain.repository.OauthLoginRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.navigation.OauthLoginRoute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The steps of the sheet. */
enum class OauthLoginStep {
    Loading,

    /** The request could not be read: [OauthLoginUiState.error] says why. */
    Failed,

    /** The user picks the code the website shows. */
    MatchCode,

    /** What is shared, and "Log in". */
    Confirm,

    /** Logged in; the website was opened to go on. */
    Done,
}

/**
 * The sheet of a "Log in with Telegram" request.
 *
 * @property request what the website or app asks for; null until read.
 * @property accountName the account the user logs in with.
 * @property botName the website's or app's bot, as `@username` or its name; null without one.
 * @property allowMessages the user lets the bot write to them: off until they turn it on.
 * @property sharePhoneNumber the user shares their phone number: off until they turn it on.
 * @property isBusy Telegram is asked: the code checked, the login accepted or declined.
 * @property error why reading, checking or logging in failed; null otherwise.
 */
@Immutable
data class OauthLoginUiState(
    val step: OauthLoginStep = OauthLoginStep.Loading,
    val request: OauthLoginRequest? = null,
    val accountName: String? = null,
    val botName: String? = null,
    val allowMessages: Boolean = false,
    val sharePhoneNumber: Boolean = false,
    val isBusy: Boolean = false,
    val error: DomainError? = null,
)

/** What the user does in the sheet. */
sealed interface OauthLoginEvent {
    data class PickCode(
        val code: String,
    ) : OauthLoginEvent

    data class SetAllowMessages(
        val allow: Boolean,
    ) : OauthLoginEvent

    data class SetSharePhoneNumber(
        val share: Boolean,
    ) : OauthLoginEvent

    data object LogIn : OauthLoginEvent

    /** "Decline": the request is declined and the sheet closes. */
    data object Decline : OauthLoginEvent

    data object Retry : OauthLoginEvent
}

/** One-off effects of [OauthLoginViewModel]. */
sealed interface OauthLoginEffect {
    /** Open [url] in the browser, where the website goes on after the login. */
    data class OpenUrl(
        val url: String,
    ) : OauthLoginEffect {
        override fun toString() = "OpenUrl(…)"
    }

    /** The request was declined: the sheet closes. */
    data object Close : OauthLoginEffect
}

/**
 * The sheet of a "Log in with Telegram" request (`docs/DESIGN_SYSTEM.md` §6 "Log in with Telegram"): reads the request
 * ([OauthLoginRepository.request]), asks for the code the website shows where Telegram wants one first, then shows what
 * is shared. Nothing is shared before "Log in", and then only what the user turned on: the phone number and the bot's
 * messages are off at first, and the TON wallet is never shared. "Decline", and closing the sheet without answering
 * ([onDismissed]), decline the request in the app's scope, as the sheet's destination leaves with it.
 *
 * The code the user picked and the two switches are kept in the [SavedStateHandle]; a picked code is not asked for
 * again.
 */
@HiltViewModel
class OauthLoginViewModel internal constructor(
    private val url: String,
    private val savedStateHandle: SavedStateHandle,
    private val repository: OauthLoginRepository,
    private val userRepository: UserRepository,
    private val applicationScope: CoroutineScope,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        repository: OauthLoginRepository,
        userRepository: UserRepository,
        @ApplicationScope applicationScope: CoroutineScope,
    ) : this(
        savedStateHandle.toRoute<OauthLoginRoute>().url,
        savedStateHandle,
        repository,
        userRepository,
        applicationScope,
    )

    private val mutableUiState =
        MutableStateFlow(
            OauthLoginUiState(
                allowMessages = savedStateHandle[KEY_ALLOW_MESSAGES] ?: false,
                sharePhoneNumber = savedStateHandle[KEY_SHARE_PHONE] ?: false,
            ),
        )
    val uiState: StateFlow<OauthLoginUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<OauthLoginEffect>(Channel.BUFFERED)
    val effects: Flow<OauthLoginEffect> = effectChannel.receiveAsFlow()

    private var answered = false

    init {
        load()
    }

    fun onEvent(event: OauthLoginEvent) {
        when (event) {
            is OauthLoginEvent.PickCode -> pickCode(event.code)
            is OauthLoginEvent.SetAllowMessages -> setAllowMessages(event.allow)
            is OauthLoginEvent.SetSharePhoneNumber -> setSharePhoneNumber(event.share)
            OauthLoginEvent.LogIn -> logIn()
            OauthLoginEvent.Decline -> decline(close = true)
            OauthLoginEvent.Retry -> load()
        }
    }

    /** The sheet was closed without an answer: the request is declined. */
    fun onDismissed() {
        decline(close = false)
    }

    private fun load() {
        mutableUiState.update { it.copy(step = OauthLoginStep.Loading, error = null) }
        viewModelScope.launch {
            when (val found = repository.request(url)) {
                is Outcome.Failure -> {
                    mutableUiState.update { it.copy(step = OauthLoginStep.Failed, error = found.error) }
                }

                is Outcome.Success -> {
                    val request = found.value
                    val picked = savedStateHandle.get<String>(KEY_CODE)
                    mutableUiState.update {
                        it.copy(
                            step =
                                if (request.needsMatchCode && picked == null) {
                                    OauthLoginStep.MatchCode
                                } else {
                                    OauthLoginStep.Confirm
                                },
                            request = request,
                            accountName = userRepository.myUserId.value?.let { userRepository.nameOf(it) },
                            botName = request.botUserId.takeIf { it != 0L }?.let { userRepository.nameOf(it, preferUsername = true) },
                        )
                    }
                }
            }
        }
    }

    private fun pickCode(code: String) {
        val state = uiState.value
        if (state.isBusy || state.step != OauthLoginStep.MatchCode) return
        mutableUiState.value = state.copy(isBusy = true, error = null)
        viewModelScope.launch {
            when (val checked = repository.checkMatchCode(url, code)) {
                is Outcome.Success -> {
                    savedStateHandle[KEY_CODE] = code
                    mutableUiState.update { it.copy(step = OauthLoginStep.Confirm, isBusy = false) }
                }

                // A wrong code ends the request: there is nothing left to answer.
                is Outcome.Failure -> {
                    answered = true
                    mutableUiState.update {
                        it.copy(step = OauthLoginStep.Failed, isBusy = false, error = checked.error)
                    }
                }
            }
        }
    }

    private fun setAllowMessages(allow: Boolean) {
        savedStateHandle[KEY_ALLOW_MESSAGES] = allow
        mutableUiState.update { it.copy(allowMessages = allow) }
    }

    private fun setSharePhoneNumber(share: Boolean) {
        savedStateHandle[KEY_SHARE_PHONE] = share
        mutableUiState.update { it.copy(sharePhoneNumber = share) }
    }

    private fun logIn() {
        val state = uiState.value
        val request = state.request
        if (state.isBusy || state.step != OauthLoginStep.Confirm || request == null) return
        mutableUiState.value = state.copy(isBusy = true, error = null)
        viewModelScope.launch {
            val accepted =
                repository.accept(
                    url = url,
                    matchCode = savedStateHandle[KEY_CODE] ?: "",
                    allowMessages = request.asksToMessage && state.allowMessages,
                    sharePhoneNumber = request.asksForPhoneNumber && state.sharePhoneNumber,
                )
            when (accepted) {
                is Outcome.Success -> {
                    answered = true
                    mutableUiState.update { it.copy(step = OauthLoginStep.Done, isBusy = false) }
                    if (accepted.value.isNotBlank()) effectChannel.trySend(OauthLoginEffect.OpenUrl(accepted.value))
                }

                is Outcome.Failure -> {
                    mutableUiState.update { it.copy(isBusy = false, error = accepted.error) }
                }
            }
        }
    }

    private fun decline(close: Boolean) {
        // A request never read, or already answered, has nothing left to decline.
        if (!answered && uiState.value.request != null) {
            answered = true
            // The destination leaves with the sheet: the answer must outlive this view model.
            applicationScope.launch { repository.decline(url) }
        }
        if (close) effectChannel.trySend(OauthLoginEffect.Close)
    }

    private companion object {
        const val KEY_CODE = "oauth_login_code"
        const val KEY_ALLOW_MESSAGES = "oauth_login_allow_messages"
        const val KEY_SHARE_PHONE = "oauth_login_share_phone"
    }
}

/** The name of the user [id], or `@username` with [preferUsername] where they have one; null where TDLib cannot say. */
private suspend fun UserRepository.nameOf(
    id: Long,
    preferUsername: Boolean = false,
): String? =
    (getUser(id) as? Outcome.Success)?.value?.let { user ->
        user.usernames.firstOrNull()?.takeIf { preferUsername }?.let { "@$it" }
            ?: listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
    }
