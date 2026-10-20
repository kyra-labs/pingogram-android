package dev.kyralabs.pingogram.feature.auth.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import dev.kyralabs.pingogram.core.ui.theme.verificationCode
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A login code field (`docs/DESIGN_SYSTEM.md` §6: M3 `TextField`): digit codes centered in the verification code
 * style (`headlineSmall`, tabular figures, wider tracking, §3.3) with a numeric keyboard, word and phrase codes in
 * `titleLarge` as typed text; [error] below it, which screen readers report as the field's error.
 *
 * @param contentType the autofill hint: [ContentType.SmsOtpCode] for codes sent by SMS, [EmailOtpCode] for codes
 *   sent by email.
 */
@Composable
internal fun AuthCodeField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    isNumeric: Boolean = true,
    contentType: ContentType? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .fieldError(error)
                .semantics { if (contentType != null) this.contentType = contentType },
        textStyle = if (isNumeric) MaterialTheme.typography.verificationCode else MaterialTheme.typography.titleLarge,
        label = { Text(label) },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = if (isNumeric) KeyboardType.NumberPassword else KeyboardType.Text,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

/**
 * A password field: masked until its visibility toggle (an Expressive [IconToggleButton]) reveals it, the password
 * keyboard without suggestions, and the password autofill hint ([ContentType.NewPassword] for [isNewPassword]).
 * [error] replaces [supportingText] and is reported as the field's error. Whether it is revealed survives
 * configuration changes, but never leaves the screen.
 */
@Composable
internal fun AuthPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    error: String? = null,
    supportingText: String? = null,
    isError: Boolean = error != null,
    isNewPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Done,
    onDone: () -> Unit = {},
) {
    var isVisible by rememberSaveable { mutableStateOf(false) }
    val support = error ?: supportingText
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            modifier
                .fillMaxWidth()
                .fieldError(error)
                .semantics { contentType = if (isNewPassword) ContentType.NewPassword else ContentType.Password },
        label = { Text(label) },
        trailingIcon = { PasswordVisibilityToggle(isVisible = isVisible, onVisibleChange = { isVisible = it }) },
        isError = isError,
        supportingText = support?.let { { Text(it) } },
        visualTransformation = if (isVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions =
            KeyboardOptions(
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = imeAction,
            ),
        // Only Done submits; Next keeps its default, moving focus to the next field.
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

/**
 * Shows or hides the password: Material Symbols "visibility" while shown, "visibility_off" while hidden. Screen
 * readers hear one label, "Show password", and its checked state.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PasswordVisibilityToggle(
    isVisible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
) {
    IconToggleButton(
        checked = isVisible,
        onCheckedChange = onVisibleChange,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors = IconButtonDefaults.iconToggleButtonColors(),
    ) {
        Icon(
            painter = painterResource(if (isVisible) UiR.drawable.ic_visibility else UiR.drawable.ic_visibility_off),
            contentDescription = stringResource(R.string.auth_password_show),
        )
    }
}

/**
 * The autofill hint for a one-time code sent by email (`HintConstants.AUTOFILL_HINT_EMAIL_OTP` of androidx.autofill),
 * which Compose's [ContentType] does not name.
 */
internal val EmailOtpCode: ContentType = ContentType("emailOTPCode")

/** Reports [message] as this text field's error to screen readers, instead of Material's generic "Invalid input". */
internal fun Modifier.fieldError(message: String?): Modifier =
    if (message != null) semantics { error(message) } else this
