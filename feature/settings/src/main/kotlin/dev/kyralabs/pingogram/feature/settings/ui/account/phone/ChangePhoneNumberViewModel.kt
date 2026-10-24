package dev.kyralabs.pingogram.feature.settings.ui.account.phone

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.LoginCodeInfo
import dev.kyralabs.pingogram.core.domain.model.codeLength
import dev.kyralabs.pingogram.core.domain.model.isNumeric
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.domain.repository.PhoneNumberChangeRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.phone.PhoneNumberForm
import dev.kyralabs.pingogram.core.ui.phone.toUiModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Changing the account's phone number (`REQUIREMENTS.md` FR-S1, `ChangePhoneNumberRoute`): the explanation, the new
 * number typed as at sign-in (`core:ui`'s [PhoneNumberForm]: the country picker, live formatting, the country of the IP
 * address for an empty calling code), then the code Telegram sends to it, submitted by itself once complete, with its
 * resend countdown. A flood wait counts down before anything can be sent again; what Telegram refuses about the number
 * or the code shows under its field.
 *
 * The step and the number typed survive process death; the code does not, and a code step comes back as the number,
 * from which a new code is asked for.
 */
@Suppress("TooManyFunctions") // Each step's events, requests and countdowns, as in the 2-step verification flows.
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChangePhoneNumberViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        private val countries: CountryRepository,
        private val repository: PhoneNumberChangeRepository,
        userRepository: UserRepository,
    ) : ViewModel() {
        private var form =
            PhoneNumberForm(
                callingCode = savedStateHandle.get<String>(KEY_CALLING_CODE).orEmpty(),
                number = savedStateHandle.get<String>(KEY_NUMBER).orEmpty(),
                countryIso = savedStateHandle.get<String>(KEY_COUNTRY),
                isCountryDetected = savedStateHandle.contains(KEY_CALLING_CODE),
            )

        private val mutableUiState =
            MutableStateFlow(
                ChangePhoneNumberUiState(
                    step =
                        when (savedStateHandle.get<String>(KEY_STEP)) {
                            ChangePhoneNumberStep.Number.name, ChangePhoneNumberStep.Code.name -> {
                                ChangePhoneNumberStep.Number
                            }

                            else -> {
                                ChangePhoneNumberStep.Intro
                            }
                        },
                    newNumber = form.toUiState(),
                ),
            )
        val uiState: StateFlow<ChangePhoneNumberUiState> = mutableUiState.asStateFlow()

        private val effectChannel = Channel<ChangePhoneNumberEffect>(Channel.BUFFERED)
        val effects: Flow<ChangePhoneNumberEffect> = effectChannel.receiveAsFlow()

        /** The last code Telegram rejected; it is not submitted again by itself. */
        private var rejectedCode: String? = null

        private var requestJob: Job? = null
        private var floodWaitJob: Job? = null
        private var resendJob: Job? = null
        private var phoneNumberInfoJob: Job? = null
        private var countriesJob: Job? = null

        init {
            userRepository.myUserId
                .flatMapLatest { id -> if (id == null) flowOf(null) else userRepository.user(id) }
                .onEach { user -> mutableUiState.update { it.copy(currentNumber = user?.phoneNumber.orEmpty()) } }
                .launchIn(viewModelScope)
            if (uiState.value.step == ChangePhoneNumberStep.Number) loadCountries()
        }

        fun onEvent(event: ChangePhoneNumberEvent) {
            when (event) {
                ChangePhoneNumberEvent.Start -> {
                    goTo(ChangePhoneNumberStep.Number).also { loadCountries() }
                }

                is ChangePhoneNumberEvent.CallingCodeChanged -> {
                    editNumber { it.withCallingCodeInput(event.input) }
                }

                is ChangePhoneNumberEvent.NumberChanged -> {
                    editNumber { it.withNumberInput(event.input) }
                }

                ChangePhoneNumberEvent.OpenCountryPicker -> {
                    updateForm {
                        it.copy(
                            isPickerOpen = true,
                            pickerQuery = "",
                        )
                    }
                }

                is ChangePhoneNumberEvent.CountryQueryChanged -> {
                    updateForm { it.copy(pickerQuery = event.query) }
                }

                is ChangePhoneNumberEvent.CountrySelected -> {
                    editNumber { it.withCountry(event.isoCode) }
                }

                ChangePhoneNumberEvent.CloseCountryPicker -> {
                    updateForm { it.copy(isPickerOpen = false) }
                }

                ChangePhoneNumberEvent.SubmitNumber -> {
                    submitNumber()
                }

                is ChangePhoneNumberEvent.CodeChanged -> {
                    onCodeChanged(event.input)
                }

                ChangePhoneNumberEvent.SubmitCode -> {
                    submitCode()
                }

                ChangePhoneNumberEvent.ResendCode -> {
                    resendCode()
                }

                ChangePhoneNumberEvent.Back -> {
                    back()
                }
            }
        }

        private fun goTo(step: ChangePhoneNumberStep) {
            mutableUiState.update { it.copy(step = step) }
            savedStateHandle[KEY_STEP] = step.name
        }

        private fun back() {
            when (uiState.value.step) {
                ChangePhoneNumberStep.Number -> {
                    goTo(ChangePhoneNumberStep.Intro)
                }

                ChangePhoneNumberStep.Code -> {
                    resendJob?.cancel()
                    requestJob?.cancel()
                    mutableUiState.update { it.copy(code = null, isWorking = false) }
                    goTo(ChangePhoneNumberStep.Number)
                }

                ChangePhoneNumberStep.Intro, ChangePhoneNumberStep.Done -> {
                    Unit
                }
            }
        }

        // The new number

        private fun editNumber(transform: (PhoneNumberForm) -> PhoneNumberForm) {
            updateForm(transform)
            savedStateHandle[KEY_CALLING_CODE] = form.callingCode
            savedStateHandle[KEY_NUMBER] = form.number
            savedStateHandle[KEY_COUNTRY] = form.countryIso
            requestPhoneNumberInfo()
        }

        private fun updateForm(transform: (PhoneNumberForm) -> PhoneNumberForm) {
            form = transform(form)
            mutableUiState.update { it.copy(newNumber = form.toUiState()) }
        }

        /** The country list, once; then the country of the IP address while no calling code was typed. */
        private fun loadCountries() {
            if (form.countries.isNotEmpty() || countriesJob?.isActive == true) return
            countriesJob =
                viewModelScope.launch {
                    val list = countries.countries().getOrNull() ?: return@launch
                    updateForm { it.copy(countries = list) }
                    if (form.wantsDetectedCountry) {
                        val isoCode = countries.currentCountryCode().getOrNull()
                        updateForm {
                            if (isoCode == null) it.copy(isCountryDetected = true) else it.withDetectedCountry(isoCode)
                        }
                    }
                    requestPhoneNumberInfo()
                }
        }

        /** Live formatting and the country of the number; an answer for an outdated number is dropped. */
        private fun requestPhoneNumberInfo() {
            phoneNumberInfoJob?.cancel()
            if (form.callingCode.isEmpty()) {
                updateForm { it.copy(numberFormat = null) }
                return
            }
            val number = form.internationalNumber
            phoneNumberInfoJob =
                viewModelScope.launch {
                    val info = countries.phoneNumberInfo(number).getOrNull() ?: return@launch
                    updateForm { if (it.internationalNumber == number) it.withPhoneNumberInfo(info) else it }
                }
        }

        private fun submitNumber() {
            if (!uiState.value.canSubmit) return
            if (!form.isPlausible) {
                reject { updateForm { it.copy(error = DomainError.PhoneNumberInvalid) } }
                return
            }
            val number = form.internationalNumber
            request({ repository.sendCode(number) }) { info ->
                rejectedCode = null
                showCode(info)
                goTo(ChangePhoneNumberStep.Code)
            }
        }

        // The code

        private fun showCode(info: LoginCodeInfo) {
            mutableUiState.update {
                it.copy(
                    code =
                        CodeUiState(
                            sentTo = info.phoneNumber.ifEmpty { form.internationalNumber },
                            type = info.type,
                            nextType = info.nextType,
                            resendInSeconds = info.timeoutSeconds.toLong(),
                            resendTotalSeconds = info.timeoutSeconds.toLong(),
                        ),
                )
            }
            startResendCountdown(info.timeoutSeconds.toLong())
        }

        /**
         * Submits a complete code by itself, unless it is unchanged or Telegram already rejected it: every attempt
         * counts against the account.
         */
        private fun onCodeChanged(input: String) {
            val code = uiState.value.code ?: return
            val length = code.type.codeLength
            val typed = if (code.type.isNumeric) input.filter(Char::isDigit).take(length ?: input.length) else input
            if (typed == code.code) return
            updateCode { it.copy(code = typed, error = null) }
            if (length != null && typed.length == length && typed != rejectedCode) submitCode()
        }

        private fun submitCode() {
            val code =
                uiState.value.code
                    ?.code
                    ?.trim()
                    .orEmpty()
            if (code.isEmpty() || !uiState.value.canSubmit) return
            request({ repository.checkCode(code) }, rejected = code) {
                resendJob?.cancel()
                mutableUiState.update { state ->
                    state.copy(changedTo = state.code?.sentTo ?: form.internationalNumber, code = null)
                }
                goTo(ChangePhoneNumberStep.Done)
                savedStateHandle.remove<String>(KEY_STEP)
                effectChannel.trySend(ChangePhoneNumberEffect.Changed)
            }
        }

        private fun resendCode() {
            val code = uiState.value.code ?: return
            if (!code.canResend || !uiState.value.canSubmit) return
            request({ repository.resendCode() }) { info ->
                rejectedCode = null
                showCode(info)
            }
        }

        private fun startResendCountdown(seconds: Long) {
            resendJob?.cancel()
            if (seconds <= 0) return
            resendJob =
                viewModelScope.launch {
                    for (left in seconds - 1 downTo 0) {
                        delay(1.seconds)
                        updateCode { it.copy(resendInSeconds = left) }
                    }
                }
        }

        private fun updateCode(transform: (CodeUiState) -> CodeUiState) =
            mutableUiState.update { state -> state.copy(code = state.code?.let(transform)) }

        // Requests

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

                in NumberErrors -> {
                    reject { updateForm { it.copy(error = error) } }
                }

                in CodeErrors -> {
                    rejectedCode = code
                    reject { updateCode { it.copy(error = error) } }
                }

                DomainError.Silent -> {
                    Unit
                }

                else -> {
                    effectChannel.trySend(ChangePhoneNumberEffect.ShowError(error))
                }
            }
        }

        private inline fun reject(show: () -> Unit) {
            show()
            effectChannel.trySend(ChangePhoneNumberEffect.InputRejected)
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
            const val KEY_STEP = "changePhoneNumber.step"
            const val KEY_CALLING_CODE = "changePhoneNumber.callingCode"
            const val KEY_NUMBER = "changePhoneNumber.number"
            const val KEY_COUNTRY = "changePhoneNumber.country"

            /** Errors about the new number, shown under its field. */
            val NumberErrors =
                setOf(
                    DomainError.PhoneNumberInvalid,
                    DomainError.PhoneNumberOccupied,
                    DomainError.PhoneNumberBanned,
                    DomainError.PhoneNumberFlood,
                )

            /** Errors about the code, shown under its field. */
            val CodeErrors = setOf(DomainError.CodeInvalid, DomainError.CodeExpired)
        }
    }

private fun PhoneNumberForm.toUiState() =
    NewNumberUiState(
        callingCode = callingCode,
        number = number,
        numberFormat = numberFormat,
        country = country?.toUiModel(),
        error = error,
        picker = picker,
    )
