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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The login flow (`REQUIREMENTS.md` §5.1, `docs/TDLIB.md` authorization state machine): one [uiState] for every step,
 * driven by TDLib's [AuthStep]s plus local navigation (welcome screen, changing the number or email address, password
 * recovery), and [effects] for one-off actions. Events come in through [onEvent].
 *
 * Countdowns (flood wait, code resend, email reset) tick here, against [timeSource], so tests control time. Typed
 * input and local navigation survive process death through the [SavedStateHandle]; passwords do not.
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
    private var phoneNumberInfoJob: Job? = null
    private var floodWaitJob: Job? = null
    private var resendJob: Job? = null
    private var emailResetJob: Job? = null

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
            is AuthEvent.NewPasswordChanged -> updatePassword { it.copy(newPassword = event.password) }
            is AuthEvent.NewPasswordConfirmationChanged -> onNewPasswordConfirmationChanged(event.confirmation)
            is AuthEvent.NewPasswordHintChanged -> updatePassword { it.copy(newPasswordHint = event.hint) }
            AuthEvent.SubmitNewPassword -> submitNewPassword()
            is AuthEvent.FirstNameChanged -> onFirstNameChanged(event.firstName)
            is AuthEvent.LastNameChanged -> onLastNameChanged(event.lastName)
            is AuthEvent.TermsAcceptedChanged -> updateRegistration { it.copy(termsAccepted = event.accepted) }
            AuthEvent.ShowTermsOfService -> updateRegistration { it.copy(isTermsOpen = true) }
            AuthEvent.DismissTermsOfService -> updateRegistration { it.copy(isTermsOpen = false) }
            AuthEvent.AcceptTermsOfService -> acceptTermsOfService()
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
        if (step == AuthStep.Closed && !model.restarting) cancelCountdowns()
        update { it.withStep(step) }
        when (step) {
            is AuthStep.WaitCode -> onWaitCode(step)
            is AuthStep.WaitEmailCode -> onWaitEmailCode(step)
            AuthStep.Ready -> if (previous != AuthStep.Ready) onAuthorized()
            else -> Unit
        }
        loadCountries()
    }

    private fun onWaitCode(step: AuthStep.WaitCode) {
        resendJob?.cancel()
        resendJob =
            countdown(step.resendTimeoutSeconds.toLong()) { left -> updateCode { it.copy(resendInSeconds = left) } }
        formatCodePhoneNumber(step.phoneNumber)
    }

    private fun onWaitEmailCode(step: AuthStep.WaitEmailCode) {
        emailResetJob?.cancel()
        val pending = step.resetState as? EmailAddressResetState.Pending ?: return
        emailResetJob =
            countdown(pending.resetInSeconds.toLong()) { left -> updateEmail { it.copy(resetInSeconds = left) } }
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
        val digits = input.filter(Char::isDigit)
        // More digits than any calling code: a whole international number was typed or pasted here.
        val isWholeNumber = digits.length > MAX_CALLING_CODE_DIGITS
        val split = if (isWholeNumber) splitInternationalNumber(digits, model.phone.countries) else null
        updatePhone { phone ->
            val callingCode = split?.first ?: digits.take(MAX_CALLING_CODE_DIGITS)
            phone.copy(
                callingCode = callingCode,
                number = (split?.second ?: phone.number).take(MAX_PHONE_NUMBER_DIGITS - callingCode.length),
                countryIso = countryForCallingCode(callingCode, phone.country, phone.countries)?.isoCode,
                error = null,
            )
        }
        requestPhoneNumberInfo()
    }

    private fun onPhoneNumberChanged(input: String) {
        val digits = input.filter(Char::isDigit)
        val isInternational = input.trimStart().startsWith('+')
        val split = if (isInternational) splitInternationalNumber(digits, model.phone.countries) else null
        updatePhone { phone ->
            if (split != null) {
                phone.copy(
                    callingCode = split.first,
                    number = split.second,
                    countryIso = countryForCallingCode(split.first, phone.country, phone.countries)?.isoCode,
                    error = null,
                )
            } else {
                phone.copy(number = digits.take(MAX_PHONE_NUMBER_DIGITS - phone.callingCode.length), error = null)
            }
        }
        requestPhoneNumberInfo()
    }

    private fun openCountryPicker() {
        updatePhone { it.copy(isPickerOpen = true, pickerQuery = "") }
        loadCountries()
    }

    private fun closeCountryPicker() {
        updatePhone { it.copy(isPickerOpen = false, pickerQuery = "") }
    }

    private fun onCountrySelected(isoCode: String) {
        val country = model.phone.countries.find { it.isoCode == isoCode } ?: return
        val callingCode = country.callingCodes.firstOrNull().orEmpty()
        updatePhone {
            it.copy(
                countryIso = isoCode,
                callingCode = callingCode,
                number = it.number.take(MAX_PHONE_NUMBER_DIGITS - callingCode.length),
                isPickerOpen = false,
                pickerQuery = "",
                error = null,
            )
        }
        requestPhoneNumberInfo()
    }

    private fun submitPhoneNumber() {
        val phone = model.phone
        if (!isPlausiblePhoneNumber(phone.callingCode, phone.number)) {
            updatePhone { it.copy(error = DomainError.PhoneNumberInvalid) }
            return
        }
        launchAction(AuthAction.SubmitPhoneNumber, ErrorField.Phone, {
            authRepository.setPhoneNumber(phone.internationalNumber)
        }) {
            update { it.copy(editingPhone = false, code = it.code.copy(code = "", error = null)) }
        }
    }

    private fun requestQrCode() {
        launchAction(AuthAction.RequestQrCode, ErrorField.None, authRepository::requestQrCode) {
            update { it.copy(editingPhone = false) }
        }
    }

    // Not blocked by a flood wait: it only ends the unauthorized session.
    private fun cancelQrCode() {
        if (model.step !is AuthStep.WaitOtherDeviceConfirmation || model.restarting) return
        update { it.copy(restarting = true, pendingAction = AuthAction.CancelQrCode) }
        viewModelScope.launch {
            val outcome = authRepository.cancelQrCode()
            update { it.copy(pendingAction = null, restarting = it.restarting && outcome is Outcome.Success) }
            if (outcome is Outcome.Failure) onFailure(outcome.error, ErrorField.None)
        }
    }

    /** Countries for the picker, and the current country by IP for a calling code nobody typed yet. */
    private fun loadCountries() {
        // TDLib answers once it has its parameters, and not while it closes or after login.
        val canAsk = model.step !in setOf(AuthStep.Initializing, AuthStep.Closed, AuthStep.LoggingOut, AuthStep.Ready)
        if (!canAsk || model.phone.countries.isNotEmpty() || countriesJob?.isActive == true) return
        countriesJob =
            viewModelScope.launch {
                val countries = countryRepository.countries().getOrNull() ?: return@launch
                updatePhone { it.copy(countries = countries) }
                if (model.phone.callingCode.isEmpty()) {
                    val isoCode = countryRepository.currentCountryCode().getOrNull()
                    val country = countries.find { it.isoCode.equals(isoCode, ignoreCase = true) }
                    if (country != null && model.phone.callingCode.isEmpty()) {
                        updatePhone {
                            it.copy(
                                countryIso = country.isoCode,
                                callingCode = country.callingCodes.firstOrNull().orEmpty(),
                            )
                        }
                    }
                }
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

    private fun onCodeChanged(input: String) {
        val type = (model.step as? AuthStep.WaitCode)?.type ?: return
        val length = type.codeLength
        val code = if (type.isNumeric) input.filter(Char::isDigit).take(length ?: input.length) else input
        updateCode { it.copy(code = code, error = null) }
        if (length != null && code.length == length) submitCode()
    }

    private fun submitCode() {
        val code = model.code.code.trim()
        if (model.step !is AuthStep.WaitCode || code.isEmpty()) return
        launchAction(AuthAction.SubmitCode, ErrorField.Code, { authRepository.checkCode(code) })
    }

    private fun resendCode() {
        val step = model.step as? AuthStep.WaitCode ?: return
        if (step.nextType == null || model.code.resendInSeconds > 0) return
        launchAction(AuthAction.ResendCode, ErrorField.Code, authRepository::resendCode) {
            updateCode { it.copy(code = "", error = null) }
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
        update { it.copy(editingPhone = true, phone = it.phone.copy(error = null)) }
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
        launchAction(AuthAction.SubmitPassword, ErrorField.Password, { authRepository.checkPassword(password) }) {
            updatePassword { it.copy(password = "") }
        }
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
        ) {
            updatePassword { it.copy(recovery = RecoveryStage.Code, recoveryCode = "", recoveryCodeError = null) }
        }
    }

    private fun onRecoveryCodeChanged(input: String) {
        updatePassword { it.copy(recoveryCode = input.filterNot(Char::isWhitespace), recoveryCodeError = null) }
    }

    private fun submitRecoveryCode() {
        val code = model.password.recoveryCode
        if (model.step !is AuthStep.WaitPassword || code.isEmpty()) return
        launchAction(AuthAction.SubmitRecoveryCode, ErrorField.RecoveryCode, {
            authRepository.checkPasswordRecoveryCode(code)
        }) {
            updatePassword { it.copy(recovery = RecoveryStage.NewPassword) }
        }
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
            ErrorField.RecoveryCode,
            { authRepository.recoverPassword(form.recoveryCode, form.newPassword, form.newPasswordHint) },
        ) { updatePassword { PasswordForm() } }
    }

    // Registration

    private fun onFirstNameChanged(input: String) {
        updateRegistration { it.copy(firstName = input.take(MAX_NAME_LENGTH), firstNameError = null) }
    }

    private fun onLastNameChanged(input: String) {
        updateRegistration { it.copy(lastName = input.take(MAX_NAME_LENGTH), lastNameError = null) }
    }

    private fun acceptTermsOfService() {
        updateRegistration { it.copy(termsAccepted = true, isTermsOpen = false) }
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
                launchAction(AuthAction.SubmitRegistration, ErrorField.Registration, {
                    authRepository.register(form.firstName.trim(), form.lastName.trim())
                })
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
        ) {
            update { it.copy(editingEmail = false, email = it.email.copy(code = "", codeError = null)) }
        }
    }

    private fun changeEmailAddress() {
        if (model.step !is AuthStep.WaitEmailCode) return
        update { it.copy(editingEmail = true, email = it.email.copy(error = null)) }
    }

    private fun onEmailCodeChanged(input: String) {
        val step = model.step as? AuthStep.WaitEmailCode ?: return
        val length = step.codeLength.takeIf { it > 0 }
        val code = input.filter(Char::isDigit).take(length ?: input.length)
        updateEmail { it.copy(code = code, codeError = null) }
        if (length != null && code.length == length) submitEmailCode()
    }

    private fun submitEmailCode() {
        val code = model.email.code
        if (model.step !is AuthStep.WaitEmailCode || model.editingEmail || code.isEmpty()) return
        launchAction(AuthAction.SubmitEmailCode, ErrorField.EmailCode, { authRepository.checkEmailCode(code) })
    }

    private fun resendEmailCode() {
        if (model.step !is AuthStep.WaitEmailCode) return
        launchAction(AuthAction.ResendEmailCode, ErrorField.EmailCode, authRepository::resendCode) {
            updateEmail { it.copy(code = "", codeError = null) }
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

    private fun onBack() {
        when (model.backTarget()) {
            BackTarget.Welcome -> update { it.copy(welcomeDone = false, phone = it.phone.copy(error = null)) }
            BackTarget.CloseCountryPicker -> closeCountryPicker()
            BackTarget.StopChangingNumber -> update { it.copy(editingPhone = false) }
            BackTarget.ChangeNumber -> changePhoneNumber()
            BackTarget.LeaveRecovery -> updatePassword { PasswordForm(password = it.password) }
            BackTarget.CloseTermsOfService -> updateRegistration { it.copy(isTermsOpen = false) }
            BackTarget.StopChangingEmail -> update { it.copy(editingEmail = false) }
            BackTarget.CancelQrCode -> cancelQrCode()
            null -> Unit
        }
    }

    // Requests, errors and countdowns

    /**
     * Sends one request at a time, and none while Telegram asks to wait; [action] shows progress until it answers.
     * Failures go next to [errorField] when they concern it, to a flood wait countdown, or to a snackbar.
     */
    private fun launchAction(
        action: AuthAction,
        errorField: ErrorField,
        request: suspend () -> Outcome<Unit>,
        onSuccess: () -> Unit = {},
    ) {
        if (model.pendingAction != null || model.floodWaitSeconds != null) return
        update { it.copy(pendingAction = action) }
        viewModelScope.launch {
            val outcome = request()
            update { it.copy(pendingAction = null) }
            when (outcome) {
                is Outcome.Success -> onSuccess()
                is Outcome.Failure -> onFailure(outcome.error, errorField)
            }
        }
    }

    private fun onFailure(
        error: DomainError,
        errorField: ErrorField,
    ) {
        when {
            error is DomainError.FloodWait -> startFloodWait(error.seconds)
            error == DomainError.Silent -> Unit
            error in errorField.inlineErrors -> update { it.withFieldError(errorField, error) }
            else -> sendEffect(AuthEffect.ShowError(error))
        }
    }

    private fun startFloodWait(seconds: Long) {
        floodWaitJob?.cancel()
        floodWaitJob = countdown(seconds) { left -> update { it.copy(floodWaitSeconds = left.takeIf { s -> s > 0 }) } }
    }

    /** Calls [onTick] with the whole seconds left (rounded up) each time they change, down to 0. */
    private fun countdown(
        seconds: Long,
        onTick: (Long) -> Unit,
    ): Job =
        viewModelScope.launch {
            val end = timeSource.markNow() + seconds.seconds
            do {
                val left = -end.elapsedNow()
                val wholeSeconds =
                    if (left.isPositive()) {
                        (left.inWholeNanoseconds + NANOS_PER_SECOND - 1) /
                            NANOS_PER_SECOND
                    } else {
                        0
                    }
                onTick(wholeSeconds)
                if (wholeSeconds > 0) delay(left - (wholeSeconds - 1).seconds)
            } while (wholeSeconds > 0)
        }

    private fun cancelCountdowns() {
        listOf(floodWaitJob, resendJob, emailResetJob).forEach { it?.cancel() }
    }

    private fun sendEffect(effect: AuthEffect) {
        effectChannel.trySend(effect)
    }

    private fun update(transform: (AuthModel) -> AuthModel) {
        model = transform(model)
        mutableUiState.value = model.toUiState()
        savedStateHandle.saveAuthModel(model)
    }

    private fun updatePhone(transform: (PhoneForm) -> PhoneForm) = update { it.copy(phone = transform(it.phone)) }

    private fun updateCode(transform: (CodeForm) -> CodeForm) = update { it.copy(code = transform(it.code)) }

    private fun updatePassword(transform: (PasswordForm) -> PasswordForm) =
        update {
            it.copy(password = transform(it.password))
        }

    private fun updateRegistration(transform: (RegistrationForm) -> RegistrationForm) =
        update { it.copy(registration = transform(it.registration)) }

    private fun updateEmail(transform: (EmailForm) -> EmailForm) = update { it.copy(email = transform(it.email)) }

    internal companion object {
        const val TERMS_OF_SERVICE_URL = "https://telegram.org/tos"
        const val PRIVACY_POLICY_URL = "https://telegram.org/privacy"

        /** Telegram's limit for first and last names (td/telegram/AuthManager.h). */
        const val MAX_NAME_LENGTH = 64
        private val NANOS_PER_SECOND = 1.seconds.inWholeNanoseconds
    }
}
