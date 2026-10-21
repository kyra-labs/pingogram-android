package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.ContentSheet
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.component.performToggle
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.contacts.R
import dev.kyralabs.pingogram.feature.contacts.ui.preview.EditContactPreviewStates
import kotlinx.coroutines.launch
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Edit contact screen: hoists its [EditContactViewModel]. [onBack] leaves; [onDone] leaves once the contact was
 * saved or deleted. Errors no field shows are snackbars.
 */
@Composable
internal fun EditContactRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditContactViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnDone by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditContactEffect.Done -> {
                    currentOnDone()
                }

                // Snackbars queue up; later effects must not wait for them.
                is EditContactEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }
            }
        }
    }
    EditContactScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Editing a contact, or adding a known Telegram user to the contacts (`REQUIREMENTS.md` FR-P1,
 * `docs/DESIGN_SYSTEM.md` §6), pushed above the screen it was opened from: a small top bar with back and "Edit contact"
 * (or "Add to contacts") on `surfaceContainer`, and on the rounded `surface` sheet below, at most `contentMaxWidth`
 * wide inside the window margin:
 * - the user's avatar, name and phone number (left to right), as one for screen readers;
 * - the first and last names in Material's rounded tonal fields;
 * - "Share my phone number", a switch row, only where the user cannot see the number yet;
 * - "Delete contact" in `error` as a [TextButton] at the start (a contact only), and the default [Button] "Save" (or
 *   "Add") at the end, both with the press morph, below the `FLOOD_WAIT` countdown or the request's progress.
 *
 * Deleting asks first ([DeleteContactDialog]). While the user loads, the delayed loading indicator; after a failure,
 * its error with "Try again". The screen keeps clear of the keyboard.
 */
@Composable
internal fun EditContactScreen(
    state: EditContactUiState,
    onEvent: (EditContactEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? EditContactUiState.Content
    val adding = content?.isContact == false
    val title = stringResource(if (adding) R.string.contacts_add_to_contacts else R.string.contacts_edit_title)
    Scaffold(
        modifier = modifier,
        // The top bar's container; the form sits on a sheet below it (ContentSheet).
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        topBar = { ContactFormTopBar(title, onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        ContentSheet(innerPadding, Modifier.consumeWindowInsets(innerPadding)) { contentPadding ->
            when (state) {
                EditContactUiState.Loading -> {
                    val description = stringResource(R.string.contacts_edit_loading)
                    Box(Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
                        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
                    }
                }

                is EditContactUiState.Error -> {
                    EditContactError(
                        state,
                        onRetry = { onEvent(EditContactEvent.Retry) },
                        Modifier.padding(contentPadding),
                    )
                }

                is EditContactUiState.Content -> {
                    EditContactContent(state, onEvent, contentPadding)
                }
            }
        }
    }
    if (content?.showsDeleteDialog == true) {
        DeleteContactDialog(
            name = content.name,
            onConfirm = { onEvent(EditContactEvent.ConfirmDelete) },
            onDismiss = { onEvent(EditContactEvent.DismissDialog) },
        )
    }
}

/** The form in a column that scrolls, at most `contentMaxWidth` wide inside the window margin. */
@Composable
private fun EditContactContent(
    state: EditContactUiState.Content,
    onEvent: (EditContactEvent) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    .padding(horizontal = windowMargin(), vertical = MaterialTheme.spacing.large)
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) { EditContactForm(state, onEvent) }
    }
}

/** The header, the names, the switch where it applies, and the buttons. */
@Composable
private fun ColumnScope.EditContactForm(
    state: EditContactUiState.Content,
    onEvent: (EditContactEvent) -> Unit,
) {
    val enabled = !state.isWorking
    ContactHeader(state)
    ContactTextField(
        value = state.firstName,
        onValueChange = { onEvent(EditContactEvent.FirstNameChanged(it)) },
        spec =
            ContactFieldSpec(
                label = stringResource(R.string.contacts_first_name),
                error = state.fieldError.textFor(ContactField.FirstName),
                enabled = enabled,
                keyboardOptions = nameKeyboard,
                autofill = ContentType.PersonFirstName,
            ),
    )
    ContactTextField(
        value = state.lastName,
        onValueChange = { onEvent(EditContactEvent.LastNameChanged(it)) },
        spec =
            ContactFieldSpec(
                label = stringResource(R.string.contacts_last_name_optional),
                enabled = enabled,
                keyboardOptions = nameKeyboard.copy(imeAction = ImeAction.Done),
                onImeAction = { onEvent(EditContactEvent.Save) },
                autofill = ContentType.PersonLastName,
            ),
    )
    if (state.canSharePhoneNumber) {
        SharePhoneNumberRow(
            name = state.firstName.ifBlank { state.name },
            checked = state.sharePhoneNumber,
            enabled = enabled,
            onCheckedChange = { onEvent(EditContactEvent.SharePhoneNumberChanged(it)) },
        )
    }
    EditContactActions(state, onEvent)
}

/** "Delete contact" for a contact, and "Save" (or "Add"), below the countdown or the progress of a request. */
@Composable
private fun EditContactActions(
    state: EditContactUiState.Content,
    onEvent: (EditContactEvent) -> Unit,
) {
    FormEnd(
        floodWaitSeconds = state.floodWaitSeconds,
        floodWaitTotalSeconds = state.floodWaitTotalSeconds,
        isWorking = state.isWorking,
        arrangement = if (state.isContact) Arrangement.SpaceBetween else Arrangement.End,
    ) {
        if (state.isContact) {
            TextButton(
                onClick = { onEvent(EditContactEvent.Delete) },
                enabled = !state.isWorking,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.contacts_delete_contact), style = MaterialTheme.typography.labelLarge)
            }
        }
        Button(
            onClick = { onEvent(EditContactEvent.Save) },
            enabled = state.canSave,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                text = stringResource(if (state.isContact) R.string.contacts_save else R.string.contacts_add_action),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** The user's avatar, name and phone number, centered; one node for screen readers. */
@Composable
private fun ColumnScope.ContactHeader(state: EditContactUiState.Content) {
    val phone =
        remember(state.phoneNumber) {
            state.phoneNumber.ifBlank { null }?.let { formatPhoneNumber(it, Locale.getDefault().country) }
        }
    Column(
        modifier = Modifier.align(Alignment.CenterHorizontally).semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Avatar(
            photo = state.photo,
            name = state.name,
            id = state.userId,
            size = MaterialTheme.sizes.avatarLarge,
        )
        Text(
            text = state.name,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            maxLines = 2,
            style = MaterialTheme.typography.titleLarge.userContent,
        )
        if (phone != null) {
            Text(
                text = phone,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge.numericContent,
            )
        }
    }
}

/**
 * "Share my phone number": a list item with a [CheckSwitch] at its end on the fields' tonal container
 * (`surfaceContainer`), the whole row toggling it as a switch for screen readers, with a `ToggleOn`/`ToggleOff` haptic.
 */
@Composable
private fun SharePhoneNumberRow(
    name: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    ListItem(
        onClick = {
            haptics.performToggle(!checked)
            onCheckedChange(!checked)
        },
        modifier =
            Modifier.semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
            },
        enabled = enabled,
        supportingContent = {
            Text(stringResource(R.string.contacts_share_phone_number_summary, name.bidiIsolated()).bidiIsolated())
        },
        trailingContent = { CheckSwitch(checked = checked, enabled = enabled) },
        shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        content = { Text(stringResource(R.string.contacts_share_phone_number)) },
    )
}

/** Reading the user failed: its error in a soft-shaped empty state, with "Try again" ([onRetry]). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EditContactError(
    state: EditContactUiState.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.contacts_edit_error_title),
        body = errorText(state.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.contacts_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

@PingogramPreviews
@Composable
private fun EditContactScreenPreview(
    @PreviewParameter(EditContactPreviewStates::class) state: EditContactUiState,
) {
    PingogramPreview { EditContactScreen(state = state, onEvent = {}, onBack = {}) }
}
