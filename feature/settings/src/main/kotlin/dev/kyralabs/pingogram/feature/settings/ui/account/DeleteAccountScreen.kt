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
        ReasonField(reason, enabled = !state.isDeleting)
        if (state.hasPassword) PasswordPart(state, password, onEvent)
        DeleteActions(state, passwordEntered = password.text.isNotEmpty(), onEvent)
    }
    if (state.confirming) {
        DeleteConfirmation(
            onConfirm = {
                onEvent(DeleteAccountEvent.ConfirmDeletion(reason.text.toString(), password.text.toString()))
            },
            onDismiss = { onEvent(DeleteAccountEvent.DismissConfirmation) },
        )
    }
}
