package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.components.fieldError
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/** FR-A5: a new account's first and last name, and accepting Telegram's terms of service. */
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
        description = stringResource(R.string.auth_registration_description),
        modifier = modifier,
        actions = {
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
        TermsAcceptance(accepted = state.termsAccepted, onEvent = onEvent)
    }
    if (state.isTermsOfServiceOpen) {
        TermsOfServiceSheet(text = state.termsOfServiceText, minUserAge = state.minUserAge, onEvent = onEvent)
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

@Composable
private fun TermsAcceptance(
    accepted: Boolean,
    onEvent: (AuthEvent) -> Unit,
) {
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .minimumInteractiveComponentSize()
                    .toggleable(value = accepted, role = Role.Checkbox) { onEvent(AuthEvent.TermsAcceptedChanged(it)) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            // The row toggles, so the checkbox itself takes no clicks.
            Checkbox(checked = accepted, onCheckedChange = null)
            Text(
                text = stringResource(R.string.auth_accept_terms),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
        }
        AuthTextButton(
            text = stringResource(R.string.auth_read_terms),
            onClick = { onEvent(AuthEvent.ShowTermsOfService) },
        )
    }
}

/** Telegram's terms of service in a sheet, with the minimum age; accepting checks the box. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TermsOfServiceSheet(
    text: String,
    minUserAge: Int,
    onEvent: (AuthEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    ModalBottomSheet(
        onDismissRequest = { onEvent(AuthEvent.DismissTermsOfService) },
        sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = spacing.windowMarginCompact)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
        ) {
            Text(
                text = stringResource(R.string.auth_terms_of_service),
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.semantics { heading() },
            )
            if (minUserAge > 0) {
                Text(
                    text = pluralStringResource(R.plurals.auth_terms_min_age, minUserAge, minUserAge),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(vertical = spacing.medium),
                horizontalArrangement = Arrangement.spacedBy(spacing.small, Alignment.End),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                AuthTextButton(
                    text = stringResource(R.string.auth_terms_decline),
                    onClick = { onEvent(AuthEvent.DeclineTermsOfService) },
                )
                Button(
                    onClick = { onEvent(AuthEvent.AcceptTermsOfService) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(
                        text = stringResource(R.string.auth_terms_accept),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
    }
}

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
