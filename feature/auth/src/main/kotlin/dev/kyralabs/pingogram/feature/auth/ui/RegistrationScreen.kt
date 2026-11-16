package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.withLink
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.fieldError
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * FR-A5: a new account's first and last name, and a line saying that signing up accepts Telegram's terms of service,
 * with a link to them; where Telegram asks for them to be accepted explicitly, its terms in a dialog
 * (`docs/DESIGN_SYSTEM.md` §6 "Sign-up terms", board 23).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RegistrationScreen(
    state: AuthStepUiState.Registration,
    canSubmit: Boolean,
    pendingAction: AuthAction?,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_registration_title),
        badgeShape = MaterialShapes.Clover8Leaf,
        badgeIcon = UiR.drawable.ic_person_filled,
        description = stringResource(R.string.auth_registration_description),
        isError = state.firstNameError != null || state.lastNameError != null,
        modifier = modifier,
        primaryAction = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_create_account),
                onClick = { onEvent(AuthEvent.SubmitRegistration) },
                enabled = canSubmit && state.firstName.isNotBlank(),
                inProgress = pendingAction == AuthAction.SubmitRegistration,
            )
        },
    ) {
        NameField(
            value = state.firstName,
            onValueChange = { onEvent(AuthEvent.FirstNameChanged(it)) },
            label = stringResource(R.string.auth_first_name),
            error = state.firstNameError,
            contentType = ContentType.PersonFirstName,
            imeAction = ImeAction.Next,
        )
        NameField(
            value = state.lastName,
            onValueChange = { onEvent(AuthEvent.LastNameChanged(it)) },
            label = stringResource(R.string.auth_last_name),
            error = state.lastNameError,
            contentType = ContentType.PersonLastName,
            imeAction = ImeAction.Done,
            onDone = { onEvent(AuthEvent.SubmitRegistration) },
        )
        TermsNotice(onOpenTerms = { onEvent(AuthEvent.OpenTermsOfService) })
    }
    if (state.isTermsOfServiceOpen) {
        TermsOfServiceDialog(text = state.termsOfServiceText, minUserAge = state.minUserAge, onEvent = onEvent)
    }
}

@Composable
private fun NameField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    contentType: ContentType,
    error: DomainError? = null,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val errorMessage = error?.let { errorText(it) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .fieldError(errorMessage)
                .semantics { this.contentType = contentType },
        label = { Text(label) },
        isError = errorMessage != null,
        supportingText = errorMessage?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
    )
}

/**
 * "By signing up you agree to the Terms of Service." in `bodyMedium` `onSurfaceVariant`, "Terms of Service" a link in
 * `primary` that opens Telegram's terms ([onOpenTerms]), its own focus stop for screen readers. No checkbox: owner
 * decision 2026-11-15, board 23.
 */
@Composable
private fun TermsNotice(onOpenTerms: () -> Unit) {
    val linkText = stringResource(R.string.auth_terms_of_service)
    val notice = stringResource(R.string.auth_signup_terms_notice, linkText)
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    val text =
        remember(notice, linkText, linkStyles) {
            buildAnnotatedString {
                val start = notice.indexOf(linkText)
                if (start < 0) {
                    append(notice)
                } else {
                    append(notice.substring(0, start))
                    withLink(
                        LinkAnnotation.Clickable(TERMS_LINK_TAG, linkStyles) { onOpenTerms() },
                    ) { append(linkText) }
                    append(notice.substring(start + linkText.length))
                }
            }
        }
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

/**
 * Telegram's terms of service when Telegram asks for them to be accepted explicitly (td_api.tl
 * `termsOfService.show_popup`): an [AlertDialog] with the document symbol, "Terms of Service" as its title, the minimum
 * age, Telegram's own text as it sent it (user content, scrolling), "Read the full terms" (Telegram's page), then
 * "Decline", which goes back to the phone number, and "Accept". Back and an outside tap close it without either.
 */
@Composable
private fun TermsOfServiceDialog(
    text: String,
    minUserAge: Int,
    onEvent: (AuthEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    AlertDialog(
        onDismissRequest = { onEvent(AuthEvent.DismissTermsOfService) },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_description), contentDescription = null) },
        title = { Text(text = stringResource(R.string.auth_terms_of_service)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
            ) {
                if (minUserAge > 0) {
                    Text(
                        text = pluralStringResource(R.plurals.auth_terms_min_age, minUserAge, minUserAge),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(text = text, style = LocalTextStyle.current.userContent)
                AuthTextButton(
                    text = stringResource(R.string.auth_terms_read_full),
                    onClick = { onEvent(AuthEvent.OpenTermsOfService) },
                )
            }
        },
        dismissButton = {
            AuthTextButton(
                text = stringResource(R.string.auth_terms_decline),
                onClick = { onEvent(AuthEvent.DeclineTermsOfService) },
            )
        },
        confirmButton = {
            Button(onClick = { onEvent(AuthEvent.AcceptTermsOfService) }, shapes = ButtonDefaults.shapes()) {
                Text(
                    text = stringResource(R.string.auth_terms_accept),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
    )
}

private const val TERMS_LINK_TAG = "terms"

@PingogramPreviews
@Composable
private fun RegistrationScreenPreview() {
    AuthScreenPreview(AuthPreviewData.registration)
}

@PingogramPreviews
@Composable
private fun RegistrationScreenErrorPreview() {
    AuthScreenPreview(AuthPreviewData.registrationError)
}

@PingogramPreviews
@Composable
private fun RegistrationTermsPreview() {
    AuthScreenPreview(AuthPreviewData.registrationTerms)
}
