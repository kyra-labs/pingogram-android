package dev.kyralabs.pingogram.feature.settings.ui.account.email

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.verificationCode
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.account.StepActions
import dev.kyralabs.pingogram.feature.settings.ui.account.StepText
import dev.kyralabs.pingogram.feature.settings.ui.qr.StepBadge
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR
import dev.kyralabs.pingogram.feature.settings.ui.account.email.LoginEmailEvent as Event

/**
 * The login email: hoists its [LoginEmailViewModel], buzzes when Telegram rejects what was typed and once the address
 * changed, and shows a new code and the other errors in snackbars. [onBack] leaves the page.
 */
@Composable
internal fun LoginEmailRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginEmailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LoginEmailEffect.Changed -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }

                LoginEmailEffect.InputRejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }

                // Snackbars queue up; later effects must not wait for them.
                LoginEmailEffect.CodeResent -> {
                    launch { snackbarHostState.showSnackbar(resources.getString(R.string.settings_login_email_resent)) }
                }

                is LoginEmailEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    LoginEmailScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Changing the login email, a step at a time on a settings page (`docs/DESIGN_SYSTEM.md` §6, "Login email"), as
 * Change number: the address as Telegram shows it and what it is for, the new address in the rounded tonal field, the
 * code sent to it in the verification code style, then "Login email changed". Steps crossfade on the effects springs
 * while the page resizes on the default spatial spring; back returns to the step before while there is one.
 */
@Composable
internal fun LoginEmailScreen(
    state: LoginEmailUiState,
    onEvent: (Event) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.canGoBack) { onEvent(Event.Back) }
    val motionScheme = MaterialTheme.motionScheme
    SettingsPageScaffold(
        title = stringResource(R.string.settings_login_email),
        onBack = { if (state.canGoBack) onEvent(Event.Back) else onBack() },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                    fadeOut(motionScheme.fastEffectsSpec()) using
                    SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
            },
            label = "LoginEmailStep",
        ) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                when (step) {
                    LoginEmailStep.Intro -> IntroStep(state, onEvent)
                    LoginEmailStep.Address -> AddressStep(state, onEvent)
                    LoginEmailStep.Code -> CodeStep(state, onEvent)
                    LoginEmailStep.Done -> DoneStep(state, onBack)
                }
            }
        }
    }
}

/** The address as Telegram shows it and what it is for, or that the account has none; loading and failures. */
@Composable
private fun ColumnScope.IntroStep(
    state: LoginEmailUiState,
    onEvent: (Event) -> Unit,
) {
    val current = state.current
    when {
        state.isLoading -> {
            SettingsLoading(stringResource(R.string.settings_login_email_loading))
        }

        state.loadError != null -> {
            SettingsError(state.loadError, onRetry = { onEvent(Event.Retry) })
        }

        current == null -> {
            StepText(stringResource(R.string.settings_login_email_none))
        }

        else -> {
            StepText(stringResource(R.string.settings_login_email_current, current.bidiIsolated()).bidiIsolated())
            StepText(stringResource(R.string.settings_login_email_intro))
            StepActions(
                state = state,
                primary = stringResource(R.string.settings_login_email_change),
                onPrimary = { onEvent(Event.Start) },
            )
        }
    }
}

/** The new address in the rounded tonal field, with the email autofill hint and keyboard. */
@Composable
private fun ColumnScope.AddressStep(
    state: LoginEmailUiState,
    onEvent: (Event) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val submit = { onEvent(Event.SubmitAddress) }
    StepText(stringResource(R.string.settings_login_email_new))
    val error = state.addressError?.let { errorText(it) }
    TextField(
        value = state.address,
        onValueChange = { onEvent(Event.AddressChanged(it)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics {
                    contentType = ContentType.EmailAddress
                    if (error != null) error(error)
                },
        enabled = !state.isWorking,
        textStyle = MaterialTheme.typography.bodyLarge.numericContent,
        label = { Text(stringResource(R.string.settings_login_email_label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_account_flow_next),
        onPrimary = submit,
        enabled = state.address.isNotBlank(),
    )
}

/** The code Telegram sent to the new address, digits in the verification code style, with the email code hint. */
@Composable
private fun ColumnScope.CodeStep(
    state: LoginEmailUiState,
    onEvent: (Event) -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val submit = { onEvent(Event.SubmitCode) }
    StepText(stringResource(R.string.settings_login_email_code_sent, state.sentTo.bidiIsolated()).bidiIsolated())
    val error = state.codeError?.let { errorText(it) }
    TextField(
        value = state.code,
        onValueChange = { onEvent(Event.CodeChanged(it)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics {
                    contentType = EmailOtpCode
                    if (error != null) error(error)
                },
        enabled = !state.isWorking,
        textStyle = MaterialTheme.typography.verificationCode,
        label = { Text(stringResource(UiR.string.login_code_label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_account_flow_next),
        onPrimary = submit,
        enabled = state.code.isNotBlank(),
        secondary = stringResource(R.string.settings_login_email_resend),
        onSecondary = { onEvent(Event.ResendCode) },
    )
}

/** The login email changed: a badge with a check, the address as Telegram shows it, and "Done". */
@Composable
private fun ColumnScope.DoneStep(
    state: LoginEmailUiState,
    onDone: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        StepBadge(UiR.drawable.ic_check)
        Text(
            text = stringResource(R.string.settings_login_email_done_title),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        state.current?.let { address ->
            Text(
                text = stringResource(R.string.settings_login_email_done_text, address.bidiIsolated()).bidiIsolated(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    StepActions(state = state, primary = stringResource(R.string.settings_account_flow_done), onPrimary = onDone)
}

/** The autofill hint of a code sent by email (Android's `AUTOFILL_HINT_EMAIL_OTP`). */
private val EmailOtpCode: ContentType = ContentType("emailOTPCode")
