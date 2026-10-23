package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.EditProfilePreviewStates
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.launch
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Edit profile: hoists [EditProfileViewModel], opens the system photo picker for a new photo (no storage permission,
 * `REQUIREMENTS.md` §7), and shows the view model's errors in snackbars. [onBack] leaves the page; [onSaved] leaves it
 * once everything was saved.
 */
@Composable
internal fun EditProfileRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    val currentOnBack by rememberUpdatedState(onBack)
    val currentOnSaved by rememberUpdatedState(onSaved)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                // Snackbars queue up; later effects must not wait for them.
                is EditProfileEffect.ShowError -> {
                    launch { snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showErrorDetails)) }
                }

                EditProfileEffect.Saved -> {
                    currentOnSaved()
                }

                EditProfileEffect.Leave -> {
                    currentOnBack()
                }
            }
        }
    }
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onEvent(EditProfileEvent.PhotoPicked(uri.toString()))
        }
    EditProfileScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/**
 * Editing the user's own profile (`REQUIREMENTS.md` FR-S1, Account): the photo ([ProfilePhotoEditor]), then the
 * names, the username and the bio in M3 outlined text fields ([NameSection], [UsernameSection], [BioSection]), each
 * with its limit and what is wrong below it, in the rows of segmented groups as on every settings page. "Save" in the
 * top bar saves what changed; it is enabled once something changed and everything is valid, and shows progress while
 * saving.
 *
 * Back with unsaved changes asks whether to discard them: the [BackHandler] takes the system back only then, so
 * otherwise Navigation's predictive back shows where it leads. The page keeps clear of the keyboard, so the field
 * being typed in stays in view.
 */
@Composable
internal fun EditProfileScreen(
    state: EditProfileUiState,
    onEvent: (EditProfileEvent) -> Unit,
    onPickPhoto: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val content = state as? EditProfileUiState.Content
    SettingsPageScaffold(
        title = stringResource(R.string.settings_edit_profile),
        onBack = { onEvent(EditProfileEvent.Back) },
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        actions = {
            if (content != null) {
                SaveAction(
                    enabled = content.canSave,
                    isSaving = content.isSaving,
                    onClick = { onEvent(EditProfileEvent.Save) },
                )
            }
        },
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) {
        when (state) {
            EditProfileUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_edit_profile_loading))
            }

            is EditProfileUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(EditProfileEvent.Retry) })
            }

            is EditProfileUiState.Content -> {
                SettingsGroup {
                    item { shapes ->
                        ProfilePhotoEditor(
                            account = state.account,
                            task = state.photoTask,
                            shapes = shapes,
                            onPickPhoto = onPickPhoto,
                            onRemovePhoto = { onEvent(EditProfileEvent.RemovePhoto) },
                        )
                    }
                }
                NameSection(state, onEvent)
                UsernameSection(state, onEvent)
                BioSection(state, onEvent)
            }
        }
    }
    if (content != null) {
        BackHandler(enabled = content.hasChanges || content.isSaving) { onEvent(EditProfileEvent.Back) }
        content.dialog?.let { dialog -> EditProfileConfirmation(dialog, content, onEvent) }
    }
}

/**
 * "Save" at the end of the top bar: a text button whose shape morphs when pressed (`ButtonDefaults.shapes`), in
 * `labelLargeEmphasized`; a [LoadingIndicator] in its place while saving.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SaveAction(
    enabled: Boolean,
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    val endPadding = Modifier.padding(end = MaterialTheme.spacing.extraSmall)
    if (isSaving) {
        val saving = stringResource(R.string.settings_edit_profile_saving)
        LoadingIndicator(endPadding.semantics { contentDescription = saving })
    } else {
        TextButton(onClick = onClick, shapes = ButtonDefaults.shapes(), modifier = endPadding, enabled = enabled) {
            Text(
                text = stringResource(R.string.settings_edit_profile_save),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** The open confirmation ([DestructiveDialog]): discarding the changes, removing the photo or the username. */
@Composable
private fun EditProfileConfirmation(
    dialog: EditProfileDialog,
    content: EditProfileUiState.Content,
    onEvent: (EditProfileEvent) -> Unit,
) {
    val (icon, texts) =
        when (dialog) {
            EditProfileDialog.Discard -> {
                null to
                    DestructiveDialogTexts(
                        title = stringResource(R.string.settings_discard_profile_dialog_title),
                        text = stringResource(R.string.settings_discard_profile_dialog_text),
                        confirm = stringResource(R.string.settings_discard_profile_dialog_confirm),
                    )
            }

            EditProfileDialog.RemovePhoto -> {
                UiR.drawable.ic_delete to
                    DestructiveDialogTexts(
                        title = stringResource(R.string.settings_remove_photo_dialog_title),
                        text = stringResource(R.string.settings_remove_photo_dialog_text),
                        confirm = stringResource(R.string.settings_remove_photo_dialog_confirm),
                    )
            }

            EditProfileDialog.RemoveUsername -> {
                UiR.drawable.ic_alternate_email to
                    DestructiveDialogTexts(
                        title = stringResource(R.string.settings_remove_username_dialog_title),
                        text =
                            stringResource(
                                R.string.settings_remove_username_dialog_text,
                                content.saved.username.bidiIsolated(),
                            ).bidiIsolated(),
                        confirm = stringResource(R.string.settings_remove_username_dialog_confirm),
                    )
            }
        }
    DestructiveDialog(
        icon = icon,
        texts = texts,
        onConfirm = { onEvent(EditProfileEvent.ConfirmDialog) },
        onDismiss = { onEvent(EditProfileEvent.DismissDialog) },
    )
}

@PingogramPreviews
@Composable
private fun EditProfileScreenPreview(
    @PreviewParameter(EditProfilePreviewStates::class) state: EditProfileUiState,
) {
    PingogramPreview { EditProfileScreen(state, onEvent = {}, onPickPhoto = {}) }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun EditProfileScreenExpandedPreview() {
    PingogramPreview { EditProfileScreen(SettingsPreviewData.editProfileWithPhoto, onEvent = {}, onPickPhoto = {}) }
}
