package dev.kyralabs.pingogram.feature.settings.ui.account.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.core.os.ConfigurationCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.domain.model.isNumeric
import dev.kyralabs.pingogram.core.ui.component.CountdownIndicator
import dev.kyralabs.pingogram.core.ui.phone.CountryField
import dev.kyralabs.pingogram.core.ui.phone.CountryPickerSheet
import dev.kyralabs.pingogram.core.ui.phone.PhoneNumberFields
import dev.kyralabs.pingogram.core.ui.phone.loginCodeDescription
import dev.kyralabs.pingogram.core.ui.phone.loginCodeResendText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.verificationCode
import dev.kyralabs.pingogram.core.ui.time.announcedCountdownSeconds
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.qr.StepBadge
import dev.kyralabs.pingogram.feature.settings.ui.twostep.FloodWaitNote
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR
import dev.kyralabs.pingogram.feature.settings.ui.account.phone.ChangePhoneNumberEvent as Event

/**
 * Changing the account's phone number: hoists its [ChangePhoneNumberViewModel], buzzes when Telegram rejects what was
 * typed and once the number changed, and shows the other errors in snackbars. [onBack] leaves the page.
 */
@Composable
internal fun ChangePhoneNumberRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangePhoneNumberViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ChangePhoneNumberEffect.Changed -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }

                ChangePhoneNumberEffect.InputRejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }

                // Snackbars queue up; later effects must not wait for them.
                is ChangePhoneNumberEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    ChangePhoneNumberScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Changing the number, a step at a time on a settings page (`docs/DESIGN_SYSTEM.md` §6, "Change number"), as the flows
 * of 2-step verification: what the step says in `bodyMedium`, its rounded tonal fields, the countdown of a
 * `FLOOD_WAIT`, a wavy progress indicator while Telegram answers, then a [TextButton] at the start and a [Button] at
 * the end. The new number takes sign-in's country picker and phone number fields (`core:ui`); the code, sign-in's
 * texts and resend countdown. Steps crossfade on the effects springs while the page resizes on the default spatial
 * spring; back returns to the step before while there is one.
 */
@Composable
internal fun ChangePhoneNumberScreen(
    state: ChangePhoneNumberUiState,
    onEvent: (Event) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    BackHandler(enabled = state.canGoBack) { onEvent(Event.Back) }
    val motionScheme = MaterialTheme.motionScheme
    SettingsPageScaffold(
        title = stringResource(R.string.settings_change_number_title),
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
            label = "ChangePhoneNumberStep",
        ) { step ->
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                when (step) {
                    ChangePhoneNumberStep.Intro -> IntroStep(state, onEvent)
                    ChangePhoneNumberStep.Number -> NumberStep(state, onEvent)
                    ChangePhoneNumberStep.Code -> CodeStep(state, onEvent)
                    ChangePhoneNumberStep.Done -> DoneStep(state, onBack)
                }
            }
        }
    }
    state.newNumber.picker?.takeIf { state.step == ChangePhoneNumberStep.Number }?.let { picker ->
        CountryPickerSheet(
            state = picker,
            onQueryChange = { onEvent(Event.CountryQueryChanged(it)) },
            onSelect = { onEvent(Event.CountrySelected(it)) },
            onDismiss = { onEvent(Event.CloseCountryPicker) },
        )
    }
}

/** What changing the number does, under the current number. */
@Composable
private fun ColumnScope.IntroStep(
    state: ChangePhoneNumberUiState,
    onEvent: (Event) -> Unit,
) {
    if (state.currentNumber.isNotEmpty()) {
        val number = displayNumber(state.currentNumber)
        StepText(stringResource(R.string.settings_change_number_current, number.bidiIsolated()).bidiIsolated())
    }
    StepText(stringResource(R.string.settings_change_number_intro))
    StepActions(state, primary = stringResource(R.string.settings_change_number_start), onPrimary = {
        onEvent(Event.Start)
    })
}

/** The new number: sign-in's country field and phone number fields, in the rounded tonal style of settings pages. */
@Composable
private fun ColumnScope.NumberStep(
    state: ChangePhoneNumberUiState,
    onEvent: (Event) -> Unit,
) {
    val submit = { onEvent(Event.SubmitNumber) }
    val number = state.newNumber
    StepText(stringResource(R.string.settings_change_number_new))
    CountryField(
        country = number.country,
        isPickerOpen = number.picker != null,
        onOpenPicker = { onEvent(Event.OpenCountryPicker) },
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    PhoneNumberFields(
        callingCode = number.callingCode,
        number = number.number,
        numberFormat = number.numberFormat,
        error = number.error?.let { errorText(it) },
        onCallingCodeChange = { onEvent(Event.CallingCodeChanged(it)) },
        onNumberChange = { onEvent(Event.NumberChanged(it)) },
        onDone = submit,
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_change_number_next),
        onPrimary = submit,
        enabled = number.callingCode.isNotEmpty() && number.number.isNotEmpty(),
    )
}

/**
 * The code Telegram sent: where it went (sign-in's texts), the code field (digits centred in the verification code
 * style, the SMS code autofill hint), and a new code by the next way Telegram offers once its countdown ran out.
 */
@Composable
private fun ColumnScope.CodeStep(
    state: ChangePhoneNumberUiState,
    onEvent: (Event) -> Unit,
) {
    val code = state.code ?: return
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    val submit = { onEvent(Event.SubmitCode) }
    StepText(loginCodeDescription(code.type, code.sentTo.bidiIsolated()).bidiIsolated())
    val error = code.error?.let { errorText(it) }
    TextField(
        value = code.code,
        onValueChange = { onEvent(Event.CodeChanged(it)) },
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus)
                .semantics {
                    contentType = ContentType.SmsOtpCode
                    if (error != null) error(error)
                },
        enabled = !state.isWorking,
        textStyle =
            if (code.type.isNumeric) MaterialTheme.typography.verificationCode else MaterialTheme.typography.titleLarge,
        label = { Text(stringResource(UiR.string.login_code_label)) },
        isError = error != null,
        supportingText = error?.let { { Text(it, Modifier.semantics { liveRegion = LiveRegionMode.Polite }) } },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (code.type.isNumeric) KeyboardType.NumberPassword else KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
    ResendCode(code, enabled = state.canSubmit) { onEvent(Event.ResendCode) }
    StepActions(
        state = state,
        primary = stringResource(R.string.settings_change_number_next),
        onPrimary = submit,
        enabled = code.code.isNotBlank(),
        secondary = stringResource(R.string.settings_change_number_wrong_number),
        onSecondary = { onEvent(Event.Back) },
    )
}

/**
 * The resend countdown, its progress beside it ([CountdownIndicator]), then the button for a new code. A polite live
 * region: screen readers hear the countdown at most every 15 seconds and the button once it replaces it.
 */
@Composable
private fun ResendCode(
    code: CodeUiState,
    enabled: Boolean,
    onResend: () -> Unit,
) {
    val nextType = code.nextType ?: return
    Box(
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large)
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        if (code.canResend) {
            TextButton(onClick = onResend, enabled = enabled, shapes = ButtonDefaults.shapes()) {
                Text(loginCodeResendText(nextType), style = MaterialTheme.typography.labelLarge)
            }
        } else {
            val announcement =
                stringResource(
                    UiR.string.login_code_resend_in,
                    durationText(announcedCountdownSeconds(code.resendInSeconds)),
                )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CountdownIndicator(secondsLeft = code.resendInSeconds, totalSeconds = code.resendTotalSeconds)
                Text(
                    text = stringResource(UiR.string.login_code_resend_in, durationText(code.resendInSeconds)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = announcement },
                )
            }
        }
    }
}

/** The number changed: a badge with a check, the new number, and "Done". */
@Composable
private fun ColumnScope.DoneStep(
    state: ChangePhoneNumberUiState,
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
            text = stringResource(R.string.settings_change_number_done_title),
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        state.changedTo?.let { number ->
            Text(
                text =
                    stringResource(
                        R.string.settings_change_number_done_text,
                        displayNumber(number).bidiIsolated(),
                    ).bidiIsolated(),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    StepActions(state, primary = stringResource(R.string.settings_change_number_done), onPrimary = onDone)
}

/** What a step says: `bodyMedium` in `onSurfaceVariant`, inset like the settings notes. */
@Composable
private fun StepText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The end of a step: the `FLOOD_WAIT` countdown and the progress of a request, then [secondary], if any, as a
 * [TextButton] at the start and [primary] as a default [Button] at the end, both with the press morph.
 */
@Suppress("LongParameterList") // The step's state, its two actions and whether the main one can be used.
@Composable
private fun StepActions(
    state: ChangePhoneNumberUiState,
    primary: String,
    onPrimary: () -> Unit,
    enabled: Boolean = true,
    secondary: String? = null,
    onSecondary: () -> Unit = {},
) {
    state.floodWaitSeconds?.let { FloodWaitNote(seconds = it, totalSeconds = state.floodWaitTotalSeconds) }
    if (state.isWorking) {
        val busy = stringResource(R.string.settings_change_number_busy)
        LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = busy })
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (secondary != null) Arrangement.SpaceBetween else Arrangement.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        if (secondary != null) {
            TextButton(onClick = onSecondary, enabled = !state.isWorking, shapes = ButtonDefaults.shapes()) {
                Text(secondary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Button(onClick = onPrimary, enabled = enabled && state.canSubmit, shapes = ButtonDefaults.shapes()) {
            Text(primary, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** A number as Telegram has it (digits), formatted for its calling code ("+44 7911 123456"). */
@Composable
private fun displayNumber(number: String): String {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0]
    return formatPhoneNumber(number, locale?.country.orEmpty())
}
