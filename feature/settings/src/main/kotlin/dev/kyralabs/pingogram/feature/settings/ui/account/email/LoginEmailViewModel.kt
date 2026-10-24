package dev.kyralabs.pingogram.feature.settings.ui.account.email

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.EmailCodeInfo
import dev.kyralabs.pingogram.core.domain.repository.LoginEmailRepository
import dev.kyralabs.pingogram.core.ui.text.isPlausibleEmailAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Changing the login email (`REQUIREMENTS.md` FR-S1, `LoginEmailRoute`): the current address as Telegram's pattern of
 * it, then the new address, then the code Telegram sends to it, submitted by itself once it has its length. A flood
 * wait counts down before anything can be sent again; what Telegram refuses shows under its field.
 *
 * The step and the address typed survive process death; the code does not, and a code step comes back as the address,
 * from which a new code is asked for.
 */
@Suppress("TooManyFunctions") // Each step's events, requests and countdowns, as in the 2-step verification flows.
@HiltViewModel
class LoginEmailViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val repository: LoginEmailRepository,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow(
                LoginEmailUiState(
                    step =
                        when (savedStateHandle.get<String>(KEY_STEP)) {
                            LoginEmailStep.Address.name, LoginEmailStep.Code.name -> LoginEmailStep.Address
                            else -> LoginEmailStep.Intro
                        },
                    address = savedStateHandle.get<String>(KEY_ADDRESS).orEmpty(),
                ),
            )
        val uiState: StateFlow<LoginEmailUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<LoginEmailEffect>(Channel.BUFFERED)
        val effects: Flow<LoginEmailEffect> = effectChannel.receiveAsFlow()

        /** The last code Telegram rejected; it is not submitted again by itself. */
        private var rejectedCode: String? = null

        private var requestJob: Job? = null
        private var floodWaitJob: Job? = null

        init {
            load()
        }

        fun onEvent(event: LoginEmailEvent) {
            when (event) {
                LoginEmailEvent.Retry -> load()
                LoginEmailEvent.Start -> if (uiState.value.current != null) goTo(LoginEmailStep.Address)
                is LoginEmailEvent.AddressChanged -> onAddressChanged(event.input)
                LoginEmailEvent.SubmitAddress -> submitAddress()
                is LoginEmailEvent.CodeChanged -> onCodeChanged(event.input)
                LoginEmailEvent.SubmitCode -> submitCode()
                LoginEmailEvent.ResendCode -> resendCode()
                LoginEmailEvent.Back -> back()
            }
        }

        private fun load() {
            mutableUiState.update { it.copy(isLoading = true, loadError = null) }
            viewModelScope.launch {
                when (val outcome = repository.address()) {
                    is Outcome.Success -> {
                        mutableUiState.update { it.copy(isLoading = false, current = outcome.value) }
                    }

                    is Outcome.Failure -> {
                        mutableUiState.update {
                            it.copy(
                                isLoading = false,
                                loadError = outcome.error,
                            )
                        }
                    }
                }
            }
        }

        private fun goTo(step: LoginEmailStep) {
            mutableUiState.update { it.copy(step = step) }
            savedStateHandle[KEY_STEP] = step.name
        }

        private fun back() {
            when (uiState.value.step) {
                LoginEmailStep.Address -> {
                    goTo(LoginEmailStep.Intro)
                }

                LoginEmailStep.Code -> {
                    requestJob?.cancel()
                    mutableUiState.update { it.copy(code = "", codeError = null, isWorking = false) }
                    goTo(LoginEmailStep.Address)
                }

                LoginEmailStep.Intro, LoginEmailStep.Done -> {
                    Unit
                }
            }
        }

        private fun onAddressChanged(input: String) {
            mutableUiState.update { it.copy(address = input, addressError = null) }
            savedStateHandle[KEY_ADDRESS] = input
        }

        private fun submitAddress() {
            val address = uiState.value.address.trim()
            if (!uiState.value.canSubmit || address.isEmpty()) return
            if (!isPlausibleEmailAddress(address)) {
                reject { mutableUiState.update { it.copy(addressError = DomainError.EmailInvalid) } }
                return
            }
            request({ repository.setAddress(address) }) { info ->
                rejectedCode = null
                showCode(info)
                goTo(LoginEmailStep.Code)
            }
        }

        private fun showCode(info: EmailCodeInfo) {
            mutableUiState.update {
                it.copy(sentTo = info.addressPattern, codeLength = info.length, code = "", codeError = null)
            }
        }

        /** Submits a complete code by itself, unless it is unchanged or Telegram already rejected it. */
        private fun onCodeChanged(input: String) {
            val state = uiState.value
            val length = state.codeLength.takeIf { it > 0 }
            val typed = input.filter(Char::isDigit).take(length ?: input.length)
            if (typed == state.code) return
            mutableUiState.update { it.copy(code = typed, codeError = null) }
            if (length != null && typed.length == length && typed != rejectedCode) submitCode()
        }

        private fun submitCode() {
            val code = uiState.value.code
            if (code.isEmpty() || !uiState.value.canSubmit) return
            request({ repository.checkCode(code) }, rejected = code) {
                mutableUiState.update { it.copy(current = it.sentTo, code = "") }
                goTo(LoginEmailStep.Done)
                savedStateHandle.remove<String>(KEY_STEP)
                effectChannel.trySend(LoginEmailEffect.Changed)
            }
        }

        private fun resendCode() {
            if (!uiState.value.canSubmit) return
            request({ repository.resendCode() }) { info ->
                rejectedCode = null
                showCode(info)
                effectChannel.trySend(LoginEmailEffect.CodeResent)
            }
        }

        /** Runs [call] while the page shows its progress, then [onSuccess]; [rejected]: the code it checks, if any. */
        private fun <T> request(
            call: suspend () -> Outcome<T>,
            rejected: String? = null,
            onSuccess: (T) -> Unit,
        ) {
            if (requestJob?.isActive == true) return
            mutableUiState.update { it.copy(isWorking = true) }
            requestJob =
                viewModelScope.launch {
                    val outcome = call()
                    mutableUiState.update { it.copy(isWorking = false) }
                    when (outcome) {
                        is Outcome.Success -> onSuccess(outcome.value)
                        is Outcome.Failure -> fail(outcome.error, rejected)
                    }
                }
        }

        private fun fail(
            error: DomainError,
            code: String?,
        ) {
            when (error) {
                is DomainError.FloodWait -> {
                    startFloodWait(error.seconds)
                }

                in AddressErrors -> {
                    reject { mutableUiState.update { it.copy(addressError = error) } }
                }

                in CodeErrors -> {
                    rejectedCode = code
                    reject { mutableUiState.update { it.copy(codeError = error) } }
                }

                DomainError.Silent -> {
                    Unit
                }

                else -> {
                    effectChannel.trySend(LoginEmailEffect.ShowError(error))
                }
            }
        }

        private inline fun reject(show: () -> Unit) {
            show()
            effectChannel.trySend(LoginEmailEffect.InputRejected)
        }

        private fun startFloodWait(seconds: Long) {
            floodWaitJob?.cancel()
            floodWaitJob =
                viewModelScope.launch {
                    for (left in seconds downTo 1) {
                        mutableUiState.update { it.copy(floodWaitSeconds = left, floodWaitTotalSeconds = seconds) }
                        delay(1.seconds)
                    }
                    mutableUiState.update { it.copy(floodWaitSeconds = null) }
                }
        }

        private companion object {
            const val KEY_STEP = "loginEmail.step"
            const val KEY_ADDRESS = "loginEmail.address"

            val AddressErrors = setOf(DomainError.EmailInvalid, DomainError.EmailNotAllowed)
            val CodeErrors = setOf(DomainError.CodeInvalid, DomainError.CodeExpired)
        }
    }
