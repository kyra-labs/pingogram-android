package dev.kyralabs.pingogram.feature.auth.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.domain.model.AuthCodeType
import dev.kyralabs.pingogram.core.domain.model.AuthStep
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.domain.model.EmailAddressResetState
import dev.kyralabs.pingogram.core.domain.model.codeLength
import dev.kyralabs.pingogram.core.domain.model.isNumeric
import dev.kyralabs.pingogram.core.domain.repository.AuthRepository
import dev.kyralabs.pingogram.core.domain.repository.ConnectionRepository
import dev.kyralabs.pingogram.core.domain.repository.CountryRepository
import dev.kyralabs.pingogram.core.ui.phone.PhoneNumberForm
import dev.kyralabs.pingogram.core.ui.time.timeLeft
import dev.kyralabs.pingogram.core.ui.time.wholeSecondsRoundedUp
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The login flow (`REQUIREMENTS.md` §5.1, `docs/TDLIB.md` authorization state machine): one [uiState] for every step,
 * driven by TDLib's [AuthStep]s plus local navigation (welcome screen, changing the number or email address, password
 * recovery), and [effects] for one-off actions. Events come in through [onEvent].
 *
 * Countdowns (flood wait, code resend, email reset) tick here, towards deadlines on [timeSource] or from the steps, so
 * tests control time. Typed input and local navigation survive process death through the [SavedStateHandle];
 * passwords and codes do not.
 *
 * It has one handler per event of a thirteen-screen flow, hence `TooManyFunctions` is suppressed.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class AuthViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val countryRepository: CountryRepository,
    connectionRepository: ConnectionRepository,
    private val timeSource: TimeSource.WithComparableMarks,
) : ViewModel() {
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        authRepository: AuthRepository,
        countryRepository: CountryRepository,
        connectionRepository: ConnectionRepository,
    ) : this(savedStateHandle, authRepository, countryRepository, connectionRepository, ElapsedRealtimeTimeSource)

    // Only touched on the main thread: events come from the UI, and the flows are collected in viewModelScope.
    private var model = savedStateHandle.restoreAuthModel()

    private val mutableUiState = MutableStateFlow(model.toUiState())
    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    private val effectChannel = Channel<AuthEffect>(Channel.BUFFERED)
    val effects: Flow<AuthEffect> = effectChannel.receiveAsFlow()

    private var countriesJob: Job? = null
    private var countryDetectionJob: Job? = null
    private var phoneNumberInfoJob: Job? = null
    private var floodWaitJob: Job? = null
    private var resendJob: Job? = null
    private var emailResetJob: Job? = null

    /** How many steps TDLib reported, to tell whether one arrived while a request ran. */
    private var stepCount = 0

    /** The success handling of a request that TDLib answered before reporting the step it led to. */
    private var awaitingStep: (() -> Unit)? = null

    init {
        viewModelScope.launch { authRepository.authStep.collect(::onAuthStep) }
        viewModelScope.launch {
            connectionRepository.connectionStatus.collect { status ->
                update { it.copy(connection = status) }
                if (status == ConnectionStatus.Ready) loadCountries()
            }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            AuthEvent.Back -> onBack()
            AuthEvent.StartMessaging -> startMessaging()
            AuthEvent.OpenTermsOfService -> sendEffect(AuthEffect.OpenUrl(TERMS_OF_SERVICE_URL))
            AuthEvent.OpenPrivacyPolicy -> sendEffect(AuthEffect.OpenUrl(PRIVACY_POLICY_URL))
            is AuthEvent.CallingCodeChanged -> onCallingCodeChanged(event.callingCode)
            is AuthEvent.PhoneNumberChanged -> onPhoneNumberChanged(event.phoneNumber)
            AuthEvent.OpenCountryPicker -> openCountryPicker()
            is AuthEvent.CountryQueryChanged -> updatePhone { it.copy(pickerQuery = event.query) }
            is AuthEvent.CountrySelected -> onCountrySelected(event.isoCode)
            AuthEvent.CloseCountryPicker -> closeCountryPicker()
            AuthEvent.SubmitPhoneNumber -> submitPhoneNumber()
            AuthEvent.LogInWithQrCode -> requestQrCode()
            AuthEvent.CancelQrCode -> cancelQrCode()
            is AuthEvent.CodeChanged -> onCodeChanged(event.code)
            AuthEvent.SubmitCode -> submitCode()
            AuthEvent.ResendCode -> resendCode()
            AuthEvent.ChangePhoneNumber -> changePhoneNumber()
            AuthEvent.OpenFragment -> openFragment()
            is AuthEvent.PasswordChanged -> updatePassword { it.copy(password = event.password, error = null) }
            AuthEvent.SubmitPassword -> submitPassword()
            AuthEvent.ForgotPassword -> forgotPassword()
            is AuthEvent.RecoveryCodeChanged -> onRecoveryCodeChanged(event.code)
            AuthEvent.SubmitRecoveryCode -> submitRecoveryCode()
            is AuthEvent.NewPasswordChanged -> onNewPasswordChanged(event.password)
            is AuthEvent.NewPasswordConfirmationChanged -> onNewPasswordConfirmationChanged(event.confirmation)
            is AuthEvent.NewPasswordHintChanged -> updatePassword { it.copy(newPasswordHint = event.hint) }
            AuthEvent.SubmitNewPassword -> submitNewPassword()
            is AuthEvent.FirstNameChanged -> onFirstNameChanged(event.firstName)
            is AuthEvent.LastNameChanged -> onLastNameChanged(event.lastName)
            is AuthEvent.TermsAcceptedChanged -> updateRegistration { it.copy(termsAccepted = event.accepted) }
            AuthEvent.ShowTermsOfService -> updateRegistration { it.copy(isTermsOpen = true) }
            AuthEvent.DismissTermsOfService -> updateRegistration { it.copy(isTermsOpen = false) }
            AuthEvent.AcceptTermsOfService -> closeTermsOfService(accepted = true)
            AuthEvent.DeclineTermsOfService -> closeTermsOfService(accepted = false)
            AuthEvent.SubmitRegistration -> submitRegistration()
            is AuthEvent.EmailAddressChanged -> onEmailAddressChanged(event.emailAddress)
            AuthEvent.SubmitEmailAddress -> submitEmailAddress()
            AuthEvent.ChangeEmailAddress -> changeEmailAddress()
            is AuthEvent.EmailCodeChanged -> onEmailCodeChanged(event.code)
            AuthEvent.SubmitEmailCode -> submitEmailCode()
            AuthEvent.ResendEmailCode -> resendEmailCode()
            AuthEvent.ResetEmailAddress -> resetEmailAddress()
            AuthEvent.ContactSupport -> contactSupport()
        }
    }

    // TDLib's steps

    private fun onAuthStep(step: AuthStep) {
        val previous = model.step
        stepCount++
        if (step == AuthStep.Closed && !model.restarting) cancelCountdowns()
        update { it.withStep(step) }
        completeAwaitedStep()
        when (step) {
            is AuthStep.WaitCode -> onWaitCode(step)

            is AuthStep.WaitEmailCode -> onWaitEmailCode(step)

            // While leaving QR login TDLib is logged out anyway, so a login arriving meanwhile does not count.
            AuthStep.Ready -> if (previous != AuthStep.Ready && !model.restarting) onAuthorized()

            else -> Unit
        }
        loadCountries()
    }

    private fun completeAwaitedStep() {
        val onSuccess = awaitingStep ?: return
        awaitingStep = null
        update { it.copy(pendingAction = null) }
        onSuccess()
    }

    private fun onWaitCode(step: AuthStep.WaitCode) {
        resendJob?.cancel()
        resendJob = countdownTo(step.resendAvailableAt) { left -> updateCode { it.copy(resendInSeconds = left) } }
        formatCodePhoneNumber(step.phoneNumber)
    }

    private fun onWaitEmailCode(step: AuthStep.WaitEmailCode) {
        emailResetJob?.cancel()
        val pending = step.resetState as? EmailAddressResetState.Pending ?: return
        emailResetJob = countdownTo(pending.resetAt) { left -> updateEmail { it.copy(resetInSeconds = left) } }
    }

    private fun onAuthorized() {
        cancelCountdowns()
        sendEffect(AuthEffect.Authorized)
    }

    // Welcome and phone number

    private fun startMessaging() {
        update { it.copy(welcomeDone = true) }
        loadCountries()
    }

    private fun onCallingCodeChanged(input: String) {
        updatePhone { it.withCallingCodeInput(input) }
        requestPhoneNumberInfo()
    }

    private fun onPhoneNumberChanged(input: String) {
        updatePhone { it.withNumberInput(input) }
        requestPhoneNumberInfo()
    }

    private fun openCountryPicker() {
        updatePhone { it.copy(isPickerOpen = true, pickerQuery = "") }
        loadCountries()
    }

    private fun closeCountryPicker() {
        update { it.afterBack(BackTarget.CloseCountryPicker) }
    }

    private fun onCountrySelected(isoCode: String) {
        if (model.phone.countries.none { it.isoCode == isoCode }) return
        updatePhone { it.withCountry(isoCode) }
        requestPhoneNumberInfo()
    }

    private fun submitPhoneNumber() {
        val phone = model.phone
        if (!phone.isPlausible) {
            updatePhone { it.copy(error = DomainError.PhoneNumberInvalid) }
            return
        }
        launchAction(
            AuthAction.SubmitPhoneNumber,
            ErrorField.Phone,
            { authRepository.setPhoneNumber(phone.internationalNumber) },
            awaitsStep = true,
        ) {
            update {
                it.copy(
                    editingPhone = false,
                    code = it.code.copy(code = "", error = null, rejectedCode = null),
                )
            }
        }
    }

    private fun requestQrCode() {
        launchAction(AuthAction.RequestQrCode, ErrorField.None, authRepository::requestQrCode, awaitsStep = true) {
            update { it.copy(editingPhone = false) }
        }
    }

    // Not blocked by a flood wait: it only ends the unauthorized session.
    private fun cancelQrCode() {
        if (model.step !is AuthStep.WaitOtherDeviceConfirmation || model.restarting) return
        update { it.afterBack(BackTarget.CancelQrCode) }
        viewModelScope.launch {
            val outcome = authRepository.cancelQrCode()
            update { it.copy(pendingAction = null, restarting = it.restarting && outcome is Outcome.Success) }
            if (outcome is Outcome.Failure) handleFailure(outcome.error, ErrorField.None)
        }
    }

    /** The country list, once; then the current country for this session ([detectCountry]). */
    private fun loadCountries() {
        // TDLib answers once it has its parameters, and not while it closes or after login.
        val canAsk = model.step !in setOf(AuthStep.Initializing, AuthStep.Closed, AuthStep.LoggingOut, AuthStep.Ready)
        if (!canAsk) return
        when {
            model.phone.countries.isNotEmpty() -> {
                detectCountry()
            }

            countriesJob?.isActive != true -> {
                countriesJob =
                    viewModelScope.launch {
                        val countries = countryRepository.countries().getOrNull() ?: return@launch
                        updatePhone { it.copy(countries = countries) }
                        detectCountry()
                        requestPhoneNumberInfo()
                    }
            }
        }
    }

    /**
     * The country of the IP address, for a calling code nobody typed yet: once per session, so again after logging
     * out, but never over what the user typed or picked.
     */
    private fun detectCountry() {
        if (!model.phone.wantsDetectedCountry || countryDetectionJob?.isActive == true) return
        countryDetectionJob =
            viewModelScope.launch {
                val isoCode = countryRepository.currentCountryCode().getOrNull() ?: return@launch
                updatePhone { it.withDetectedCountry(isoCode) }
                requestPhoneNumberInfo()
            }
    }

    /** Live formatting and country detection; an answer for an outdated number is dropped. */
    private fun requestPhoneNumberInfo() {
        phoneNumberInfoJob?.cancel()
        val phone = model.phone
        if (phone.callingCode.isEmpty()) {
            updatePhone { it.copy(numberFormat = null) }
            return
        }
        val number = phone.internationalNumber
        phoneNumberInfoJob =
            viewModelScope.launch {
                val info = countryRepository.phoneNumberInfo(number).getOrNull() ?: return@launch
                updatePhone { if (it.internationalNumber == number) it.withPhoneNumberInfo(info) else it }
            }
    }

    // Login code

    /**
     * Submits a complete code by itself, unless it is unchanged (e.g. a digit typed past the length is dropped) or
     * Telegram already rejected it: every attempt counts against the account.
     */
    private fun onCodeChanged(input: String) {
        val type = (model.step as? AuthStep.WaitCode)?.type ?: return
        val length = type.codeLength
        val code = if (type.isNumeric) input.filter(Char::isDigit).take(length ?: input.length) else input
        val form = model.code
        if (code == form.code) return
        updateCode { it.copy(code = code, error = null) }
        if (length != null && code.length == length && code != form.rejectedCode) submitCode()
    }

    private fun submitCode() {
        val code = model.code.code.trim()
        if (model.step !is AuthStep.WaitCode || code.isEmpty()) return
        launchAction(
            AuthAction.SubmitCode,
            ErrorField.Code,
            { authRepository.checkCode(code) },
            awaitsStep = true,
            onFailure = { error -> if (error.rejectsCode) updateCode { it.copy(rejectedCode = code) } },
        )
    }

    private fun resendCode() {
        val step = model.step as? AuthStep.WaitCode ?: return
        if (step.nextType == null || model.code.resendInSeconds > 0) return
        launchAction(AuthAction.ResendCode, ErrorField.Code, authRepository::resendCode, awaitsStep = true) {
            updateCode { it.copy(code = "", error = null, rejectedCode = null) }
        }
    }

    private fun changePhoneNumber() {
        val step = model.step
        val acceptsNewNumber =
            step is AuthStep.WaitCode ||
                step is AuthStep.WaitPassword ||
                step is AuthStep.WaitRegistration ||
                step is AuthStep.WaitEmailAddress ||
                step is AuthStep.WaitEmailCode ||
                step is AuthStep.WaitPremiumPurchase
        if (!acceptsNewNumber) return
        update { it.afterBack(BackTarget.ChangeNumber) }
        loadCountries()
    }

    private fun openFragment() {
        val type = (model.step as? AuthStep.WaitCode)?.type as? AuthCodeType.Fragment ?: return
        sendEffect(AuthEffect.OpenUrl(type.url))
    }

    /** The number the code went to, formatted by TDLib for the code screen. */
    private fun formatCodePhoneNumber(rawNumber: String) {
        if (model.code.formattedPhoneNumber?.first == rawNumber) return
        viewModelScope.launch {
            val info = countryRepository.phoneNumberInfo(rawNumber).getOrNull() ?: return@launch
            if (info.callingCode.isEmpty()) return@launch
            val formatted = "+${info.callingCode} ${info.formattedNumber.replace("-", "").trim()}".trim()
            updateCode { it.copy(formattedPhoneNumber = rawNumber to formatted) }
        }
    }

    // 2-step verification password

    private fun submitPassword() {
        val password = model.password.password
        if (model.step !is AuthStep.WaitPassword || password.isEmpty()) return
        launchAction(
            AuthAction.SubmitPassword,
            ErrorField.Password,
            { authRepository.checkPassword(password) },
            awaitsStep = true,
        ) { updatePassword { it.copy(password = "") } }
    }

    /** FR-A4: by recovery email; without one, explain that the password cannot be recovered (no account reset). */
    private fun forgotPassword() {
        val step = model.step as? AuthStep.WaitPassword ?: return
        if (!step.hasRecoveryEmailAddress) {
            updatePassword { it.copy(showNoRecoveryEmailInfo = true) }
            return
        }
        launchAction(
            AuthAction.RequestPasswordRecovery,
            ErrorField.PasswordRecovery,
            authRepository::requestPasswordRecovery,
        ) { updatePassword { it.copy(recovery = RecoveryStage.Code, recoveryCode = "", recoveryCodeError = null) } }
    }

    private fun onRecoveryCodeChanged(input: String) {
        updatePassword { it.copy(recoveryCode = input.filterNot(Char::isWhitespace), recoveryCodeError = null) }
    }

    private fun submitRecoveryCode() {
        val code = model.password.recoveryCode
        if (model.step !is AuthStep.WaitPassword || code.isEmpty()) return
        launchAction(
            AuthAction.SubmitRecoveryCode,
            ErrorField.RecoveryCode,
            { authRepository.checkPasswordRecoveryCode(code) },
        ) { updatePassword { it.copy(recovery = RecoveryStage.NewPassword) } }
    }

    private fun onNewPasswordChanged(input: String) {
        updatePassword { it.copy(newPassword = input, newPasswordError = null) }
    }

    private fun onNewPasswordConfirmationChanged(input: String) {
        updatePassword { it.copy(newPasswordConfirmation = input) }
    }

    private fun submitNewPassword() {
        val form = model.password
        val isComplete = form.newPassword.isNotEmpty() && form.newPassword == form.newPasswordConfirmation
        if (model.step !is AuthStep.WaitPassword || !isComplete) return
        launchAction(
            AuthAction.SubmitNewPassword,
            ErrorField.NewPassword,
            { authRepository.recoverPassword(form.recoveryCode, form.newPassword, form.newPasswordHint) },
            awaitsStep = true,
        ) { updatePassword { PasswordForm() } }
    }

    // Registration

    private fun onFirstNameChanged(input: String) {
        updateRegistration { it.copy(firstName = input.take(MAX_NAME_LENGTH), firstNameError = null) }
    }

    private fun onLastNameChanged(input: String) {
        updateRegistration { it.copy(lastName = input.take(MAX_NAME_LENGTH), lastNameError = null) }
    }

    /** Accepting or declining the terms closes them; declining un-accepts them. */
    private fun closeTermsOfService(accepted: Boolean) {
        updateRegistration { it.copy(termsAccepted = accepted, isTermsOpen = false) }
    }

    private fun submitRegistration() {
        if (model.step !is AuthStep.WaitRegistration) return
        val form = model.registration
        when {
            form.firstName.isBlank() -> {
                updateRegistration { it.copy(firstNameError = DomainError.FirstNameInvalid) }
            }

            // Registering accepts the terms, so they must have been accepted first.
            !form.termsAccepted -> {
                updateRegistration { it.copy(isTermsOpen = true) }
            }

            else -> {
                launchAction(
                    AuthAction.SubmitRegistration,
                    ErrorField.Registration,
                    { authRepository.register(form.firstName.trim(), form.lastName.trim()) },
                    awaitsStep = true,
                )
            }
        }
    }

    // Email address

    private fun onEmailAddressChanged(input: String) {
        updateEmail { it.copy(emailAddress = input.filterNot(Char::isWhitespace), error = null) }
    }

    private fun submitEmailAddress() {
        val step = model.step
        val address = model.email.emailAddress
        if (step != AuthStep.WaitEmailAddress && !(step is AuthStep.WaitEmailCode && model.editingEmail)) return
        if (!isPlausibleEmailAddress(address)) {
            updateEmail { it.copy(error = DomainError.EmailInvalid) }
            return
        }
        launchAction(
            AuthAction.SubmitEmailAddress,
            ErrorField.EmailAddress,
            { authRepository.setEmailAddress(address) },
            awaitsStep = true,
        ) {
            update {
                val email = it.email.copy(addressEntered = true, code = "", codeError = null, rejectedCode = null)
                it.copy(editingEmail = false, email = email)
            }
        }
    }

    private fun changeEmailAddress() {
        if (model.step !is AuthStep.WaitEmailCode) return
        update { it.copy(editingEmail = true, email = it.email.copy(error = null)) }
    }

    /** Like [onCodeChanged]: an unchanged or already rejected code is not submitted again by itself. */
    private fun onEmailCodeChanged(input: String) {
        val step = model.step as? AuthStep.WaitEmailCode ?: return
        val length = step.codeLength.takeIf { it > 0 }
        val code = input.filter(Char::isDigit).take(length ?: input.length)
        val form = model.email
        if (code == form.code) return
        updateEmail { it.copy(code = code, codeError = null) }
        if (length != null && code.length == length && code != form.rejectedCode) submitEmailCode()
    }

    private fun submitEmailCode() {
        val code = model.email.code
        if (model.step !is AuthStep.WaitEmailCode || model.editingEmail || code.isEmpty()) return
        launchAction(
            AuthAction.SubmitEmailCode,
            ErrorField.EmailCode,
            { authRepository.checkEmailCode(code) },
            awaitsStep = true,
            onFailure = { error -> if (error.rejectsCode) updateEmail { it.copy(rejectedCode = code) } },
        )
    }

    /** Only for an address entered in this login: TDLib has no address to resend to otherwise. */
    private fun resendEmailCode() {
        if (model.step !is AuthStep.WaitEmailCode || !model.email.addressEntered) return
        launchAction(AuthAction.ResendEmailCode, ErrorField.EmailCode, authRepository::resendCode, awaitsStep = true) {
            updateEmail { it.copy(code = "", codeError = null, rejectedCode = null) }
        }
    }

    private fun resetEmailAddress() {
        if ((model.step as? AuthStep.WaitEmailCode)?.resetState == null) return
        launchAction(AuthAction.ResetEmailAddress, ErrorField.EmailReset, authRepository::resetEmailAddress)
    }

    // Telegram Premium required

    private fun contactSupport() {
        val step = model.step as? AuthStep.WaitPremiumPurchase ?: return
        sendEffect(AuthEffect.SendEmail(step.supportEmailAddress, step.supportEmailSubject))
    }

    // Back

    /**
     * Local navigation by [afterBack], which the UI state also uses to show where back leads
     * ([AuthUiState.backStep]).
     */
    private fun onBack() {
        when (val target = model.backTarget()) {
            BackTarget.ChangeNumber -> changePhoneNumber()
            BackTarget.CancelQrCode -> cancelQrCode()
            null -> Unit
            else -> update { it.afterBack(target) }
        }
    }

    // Requests, errors and countdowns

    /**
     * Sends one request at a time, and none while Telegram asks to wait; [action] shows progress until it answers.
     * Failures go next to [errorField] when they concern it, to a flood wait countdown, or to a snackbar; [onFailure]
     * then sees the raw error. A code or password Telegram accepts or rejects gets its haptic ([inputFeedback]).
     *
     * With [awaitsStep], success means TDLib moves on to another (or a renewed) step. Its answer and that step arrive
     * on separate paths, in either order; when the answer comes first, progress stays until the step arrives, so the
     * old step neither reappears nor accepts the same request again.
     */
    private fun launchAction(
        action: AuthAction,
        errorField: ErrorField,
        request: suspend () -> Outcome<Unit>,
        awaitsStep: Boolean = false,
        onFailure: (DomainError) -> Unit = {},
        onSuccess: () -> Unit = {},
    ) {
        if (model.pendingAction != null || model.floodWaitSeconds != null) return
        update { it.copy(pendingAction = action) }
        val stepsBefore = stepCount
        viewModelScope.launch {
            val outcome = request()
            inputFeedback(action, outcome)
            if (outcome is Outcome.Success && awaitsStep && stepCount == stepsBefore) {
                awaitingStep = onSuccess
                return@launch
            }
            update { it.copy(pendingAction = null) }
            when (outcome) {
                is Outcome.Success -> {
                    onSuccess()
                }

                is Outcome.Failure -> {
                    handleFailure(outcome.error, errorField)
                    onFailure(outcome.error)
                }
            }
        }
    }

    private fun handleFailure(
        error: DomainError,
        errorField: ErrorField,
    ) {
        when {
            error is DomainError.FloodWait -> startFloodWait(error.seconds)

            // td_api.tl: a 406's message is neither shown nor interpreted; the input is reported as not accepted.
            error == DomainError.Silent && errorField == ErrorField.None -> showError(DomainError.InputRejected)

            error == DomainError.Silent -> update { it.withFieldError(errorField, DomainError.InputRejected) }

            error in errorField.inlineErrors -> update { it.withFieldError(errorField, error) }

            else -> showError(error)
        }
    }

    /**
     * `Confirm` when Telegram accepts a code or password the user entered, `Reject` when it rejects one as wrong or
     * expired (`docs/DESIGN_SYSTEM.md` §5); other failures (network, flood wait) are not about the input.
     */
    private fun inputFeedback(
        action: AuthAction,
        outcome: Outcome<Unit>,
    ) {
        if (action !in InputChecks) return
        when {
            outcome is Outcome.Success -> sendEffect(AuthEffect.InputAccepted)
            outcome is Outcome.Failure && outcome.error.rejectsInput -> sendEffect(AuthEffect.InputRejected)
        }
    }

    private fun startFloodWait(seconds: Long) {
        floodWaitJob?.cancel()
        floodWaitJob =
            countdownTo(timeSource.markNow() + seconds.seconds) { left ->
                update { it.copy(floodWaitSeconds = left.takeIf { s -> s > 0 }, floodWaitTotalSeconds = seconds) }
            }
    }

    /** Calls [onTick] with the whole seconds left until [deadline] (rounded up) each time they change, down to 0. */
    private fun countdownTo(
        deadline: ComparableTimeMark,
        onTick: (Long) -> Unit,
    ): Job =
        viewModelScope.launch {
            do {
                val left = deadline.timeLeft()
                val wholeSeconds = left.wholeSecondsRoundedUp()
                onTick(wholeSeconds)
                if (wholeSeconds > 0) delay(left - (wholeSeconds - 1).seconds)
            } while (wholeSeconds > 0)
        }

    private fun cancelCountdowns() {
        listOf(floodWaitJob, resendJob, emailResetJob).forEach { it?.cancel() }
    }

    private fun showError(error: DomainError) = sendEffect(AuthEffect.ShowError(error))

    private fun sendEffect(effect: AuthEffect) {
        effectChannel.trySend(effect)
    }

    private fun update(transform: (AuthModel) -> AuthModel) {
        model = transform(model)
        mutableUiState.value = model.toUiState()
        savedStateHandle.saveAuthModel(model)
    }

    private fun updatePhone(transform: (PhoneNumberForm) -> PhoneNumberForm) =
        update { it.copy(phone = transform(it.phone)) }

    private fun updateCode(transform: (CodeForm) -> CodeForm) = update { it.copy(code = transform(it.code)) }

    private fun updatePassword(transform: (PasswordForm) -> PasswordForm) =
        update { it.copy(password = transform(it.password)) }

    private fun updateRegistration(transform: (RegistrationForm) -> RegistrationForm) =
        update { it.copy(registration = transform(it.registration)) }

    private fun updateEmail(transform: (EmailForm) -> EmailForm) = update { it.copy(email = transform(it.email)) }

    internal companion object {
        const val TERMS_OF_SERVICE_URL = "https://telegram.org/tos"
        const val PRIVACY_POLICY_URL = "https://telegram.org/privacy"

        /** Telegram's limit for first and last names (td/telegram/AuthManager.h). */
        const val MAX_NAME_LENGTH = 64
    }
}

/** Telegram rejected the code itself (a 406 included): the same code must not be submitted again by itself. */
private val DomainError.rejectsCode: Boolean
    get() = this == DomainError.CodeInvalid || this == DomainError.CodeExpired || this == DomainError.Silent

/** Telegram rejected the code or password itself, rather than failing to check it. */
private val DomainError.rejectsInput: Boolean
    get() = rejectsCode || this == DomainError.PasswordInvalid || this == DomainError.PasswordRecoveryExpired

/** The requests that send a code or password the user entered for Telegram to check. */
private val InputChecks =
    setOf(
        AuthAction.SubmitCode,
        AuthAction.SubmitPassword,
        AuthAction.SubmitRecoveryCode,
        AuthAction.SubmitNewPassword,
        AuthAction.SubmitEmailCode,
    )
