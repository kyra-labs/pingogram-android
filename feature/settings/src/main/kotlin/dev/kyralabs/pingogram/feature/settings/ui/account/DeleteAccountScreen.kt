package dev.kyralabs.pingogram.feature.settings.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsError
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsLoading
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPageScaffold
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSectionHeader
import dev.kyralabs.pingogram.feature.settings.ui.preview.DeleteAccountPreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.twostep.FloodWaitNote
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepFieldSpec
import dev.kyralabs.pingogram.feature.settings.ui.twostep.TwoStepPasswordField
import dev.kyralabs.pingogram.feature.settings.ui.twostep.WatchChanges
import kotlinx.coroutines.launch
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Delete account page: hoists [DeleteAccountViewModel], buzzes when Telegram refuses the password or once the
 * account is deleted, and shows the other errors in snackbars. [onBack] leaves the page.
 */
@Composable
internal fun DeleteAccountRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeleteAccountViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val haptics = LocalHapticFeedback.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DeleteAccountEffect.PasswordRejected -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                }

                DeleteAccountEffect.Deleted -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                }

                // Snackbars queue up; later effects must not wait for them.
                is DeleteAccountEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    DeleteAccountScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Delete account (`docs/DESIGN_SYSTEM.md` §6, "Delete account"), a settings page:
 * - "Delete my account if away for": the periods as radio rows, the choice saved at once;
 * - "Delete now": what is lost and what stays in `bodyMedium` `onSurfaceVariant`, an optional reason and, with 2-step
 *   verification on, its password in rounded tonal fields, the `FLOOD_WAIT` countdown and a wavy progress indicator
 *   while it deletes, then "Delete account" as a default [Button] in the error role, behind a destructive
 *   `AlertDialog`.
 *
 * The reason survives configuration changes and process death; the password does not.
 */
@Composable
internal fun DeleteAccountScreen(
    state: DeleteAccountUiState,
    onEvent: (DeleteAccountEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_delete_account),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        when (state) {
            DeleteAccountUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_delete_account_loading))
            }

            is DeleteAccountUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(DeleteAccountEvent.Retry) })
            }

            is DeleteAccountUiState.Content -> {
                TtlGroup(state, onEvent)
                DeleteNowSection(state, onEvent)
            }
        }
    }
}

@Composable
private fun TtlGroup(
    state: DeleteAccountUiState.Content,
    onEvent: (DeleteAccountEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_delete_account_section_ttl),
        footer = stringResource(R.string.settings_delete_account_ttl_footer),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        state.ttlOptions.forEach { days ->
            item(key = days) { shapes ->
                RadioItem(
                    label = ttlText(days),
                    selected = days == state.ttlDays,
                    enabled = !state.savingTtl && !state.isDeleting,
                    shapes = shapes,
                    onClick = { onEvent(DeleteAccountEvent.SelectTtl(days)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DeleteNowSection(
    state: DeleteAccountUiState.Content,
    onEvent: (DeleteAccountEvent) -> Unit,
) {
    val reason = rememberTextFieldState()
    val password = remember { TextFieldState() }
    WatchChanges(password) { onEvent(DeleteAccountEvent.PasswordChanged) }
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        SettingsSectionHeader(stringResource(R.string.settings_delete_account_section_now))
        PageText(stringResource(R.string.settings_delete_account_what_is_lost))
        PageText(stringResource(R.string.settings_delete_account_what_stays))
        PageText(stringResource(R.string.settings_delete_account_sign_up_again))
        TextField(
            state = reason,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isDeleting,
            label = { Text(stringResource(R.string.settings_delete_account_reason)) },
            // The reason is the user's own text: it takes its own direction (`docs/DESIGN_SYSTEM.md` §9).
            textStyle = LocalTextStyle.current.userContent,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = REASON_MAX_LINES),
            shape = TextFieldDefaults.roundedShape,
            colors = TextFieldDefaults.tonalColors(),
        )
        if (state.hasPassword) {
            PageText(stringResource(R.string.settings_delete_account_password_text))
            if (state.passwordHint.isNotEmpty()) {
                PageText(
                    stringResource(R.string.settings_two_step_hint_is, state.passwordHint.bidiIsolated())
                        .bidiIsolated(),
                )
            }
            TwoStepPasswordField(
                state = password,
                spec =
                    TwoStepFieldSpec(
                        label = stringResource(R.string.settings_two_step_label_password),
                        error = state.passwordError?.let { errorText(it) },
                        enabled = !state.isDeleting,
                        imeAction = ImeAction.Done,
                        onAction = { onEvent(DeleteAccountEvent.RequestDeletion) },
                    ),
                isNewPassword = false,
            )
        }
        state.floodWaitSeconds?.let { FloodWaitNote(seconds = it, totalSeconds = state.floodWaitTotalSeconds) }
        if (state.isDeleting) {
            val deleting = stringResource(R.string.settings_delete_account_deleting)
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = deleting })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(
                onClick = { onEvent(DeleteAccountEvent.RequestDeletion) },
                enabled = state.canDelete(passwordEntered = password.text.isNotEmpty()),
                shapes = ButtonDefaults.shapes(),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(
                    stringResource(R.string.settings_delete_account),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
    if (state.confirming) {
        DestructiveDialog(
            icon = UiR.drawable.ic_delete,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_delete_account_dialog_title),
                    text = stringResource(R.string.settings_delete_account_dialog_text),
                    confirm = stringResource(R.string.settings_delete_account_dialog_confirm),
                ),
            onConfirm = {
                onEvent(DeleteAccountEvent.ConfirmDeletion(reason.text.toString(), password.text.toString()))
            },
            onDismiss = { onEvent(DeleteAccountEvent.DismissConfirmation) },
        )
    }
}

/** What the page says: `bodyMedium` in `onSurfaceVariant`, inset like the settings notes. */
@Composable
private fun PageText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** A period of [days]: in months where it is one of Telegram's choices ("6 months"), in days otherwise. */
@Composable
internal fun ttlText(days: Int): String {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val months = TtlMonths[days]
    return if (months != null) {
        pluralStringResource(R.plurals.settings_delete_account_ttl_months, months, numbers.format(months))
    } else {
        pluralStringResource(R.plurals.settings_delete_account_ttl_days, days, numbers.format(days))
    }
}

/** The months of Telegram's choices, by their days ([DeleteAccountViewModel.TtlChoices]). */
private val TtlMonths = mapOf(30 to 1, 90 to 3, 180 to 6, 365 to 12, 548 to 18, 730 to 24)

private const val REASON_MAX_LINES = 4

@PingogramPreviews
@Composable
private fun DeleteAccountScreenPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.withPassword, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenWithoutPasswordPreview() {
    PingogramPreview {
        DeleteAccountScreen(state = DeleteAccountPreviewStates.withoutPassword, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenDeletingPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.deleting, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenConfirmingPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.confirming, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenWrongPasswordPreview() {
    PingogramPreview {
        DeleteAccountScreen(state = DeleteAccountPreviewStates.wrongPassword, onEvent = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenFloodWaitPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.floodWait, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenLoadingPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountUiState.Loading, onEvent = {}, onBack = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteAccountScreenErrorPreview() {
    PingogramPreview {
        DeleteAccountScreen(state = DeleteAccountUiState.Error(DomainError.Network), onEvent = {}, onBack = {})
    }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun DeleteAccountScreenExpandedPreview() {
    PingogramPreview { DeleteAccountScreen(state = DeleteAccountPreviewStates.withPassword, onEvent = {}, onBack = {}) }
}
