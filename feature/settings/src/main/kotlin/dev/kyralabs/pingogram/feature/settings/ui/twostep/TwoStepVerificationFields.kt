package dev.kyralabs.pingogram.feature.settings.ui.twostep

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecureTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import dev.kyralabs.pingogram.core.ui.text.durationText
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.flow.drop

// The fields of 2-step verification's flows: rounded tonal text fields directly on the settings page
// (docs/DESIGN_SYSTEM.md §6, "Text fields" and "Settings rows"), passwords in Material's SecureTextField.

/**
 * A password: Material's [SecureTextField] in the rounded tonal style, with the password autofill hint
 * ([ContentType.NewPassword] for a new one) and a keyboard without suggestions.
 */
@Composable
internal fun TwoStepPasswordField(
    state: TextFieldState,
    spec: TwoStepFieldSpec,
    isNewPassword: Boolean,
    modifier: Modifier = Modifier,
) {
    SecureTextField(
        state = state,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password },
        enabled = spec.enabled,
        label = { Text(spec.label) },
        supportingText = spec.error?.let { { Text(it) } },
        isError = spec.error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = spec.imeAction),
        onKeyboardAction = { spec.onAction() },
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** A line of text (a hint, an email address, a code) in the rounded tonal style, with its autofill hint. */
@Composable
internal fun TwoStepTextField(
    state: TextFieldState,
    spec: TwoStepFieldSpec,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    autofill: ContentType? = null,
) {
    TextField(
        state = state,
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { if (autofill != null) contentType = autofill },
        enabled = spec.enabled,
        label = { Text(spec.label) },
        supportingText = spec.error?.let { { Text(it) } },
        isError = spec.error != null,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = spec.imeAction),
        onKeyboardAction = { spec.onAction() },
        lineLimits = TextFieldLineLimits.SingleLine,
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** Focuses the step's first field once, so the keyboard shows; and calls [onChange] whenever [field]'s text changes. */
@Composable
internal fun FirstField(
    focusRequester: FocusRequester,
    field: TextFieldState,
    onChange: () -> Unit,
) {
    LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
    WatchChanges(field, onChange)
}

/** Calls [onChange] whenever [field]'s text changes, so the step's field leaves its error state. */
@Composable
internal fun WatchChanges(
    field: TextFieldState,
    onChange: () -> Unit,
) {
    LaunchedEffect(field) {
        snapshotFlow { field.text.toString() }.drop(1).collect { onChange() }
    }
}

/** The text of a field's [error]. */
@Composable
internal fun fieldErrorText(error: TwoStepVerificationFieldError?): String? =
    when (error) {
        null -> {
            null
        }

        is TwoStepVerificationFieldError.Rejected -> {
            errorText(error.error)
        }

        TwoStepVerificationFieldError.PasswordsDiffer -> {
            stringResource(
                R.string.settings_two_step_error_passwords_differ,
            )
        }

        TwoStepVerificationFieldError.HintIsPassword -> {
            stringResource(
                R.string.settings_two_step_error_hint_is_password,
            )
        }
    }

/**
 * While Telegram asks to wait (`FLOOD_WAIT`): the time left in the error role, and beside it a determinate
 * [CircularWavyProgressIndicator] that runs down from [totalSeconds] (`docs/DESIGN_SYSTEM.md` §5), gliding from one
 * second to the next on the effects spring and read while drawing. The indicator is hidden from screen readers; they
 * hear the text as a polite live region, under a minute in 15-second steps rather than every second.
 */
@Composable
internal fun FloodWaitNote(
    seconds: Long,
    totalSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val fraction by animateFloatAsState(
        targetValue = if (totalSeconds > 0) seconds.toFloat() / totalSeconds else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "FloodWait",
    )
    val text = stringResource(R.string.settings_two_step_flood_wait, durationText(seconds))
    val announced = if (seconds in 1 until SECONDS_PER_MINUTE) (seconds + STEP - 1) / STEP * STEP else seconds
    val announcement = stringResource(R.string.settings_two_step_flood_wait, durationText(announced))
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.clearAndSetSemantics {},
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = text,
            modifier =
                Modifier.clearAndSetSemantics {
                    contentDescription = announcement
                    liveRegion = LiveRegionMode.Polite
                },
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private const val SECONDS_PER_MINUTE = 60L

/** Rounding up never claims less time than is left. */
private const val STEP = 15L
