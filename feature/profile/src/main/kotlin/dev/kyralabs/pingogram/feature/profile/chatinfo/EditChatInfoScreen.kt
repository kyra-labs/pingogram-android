package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.ui.component.PhotoEditor
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.ui.profileRowContainerColor

/** The page that edits a group's or channel's title and description: hoists its view model; closes once saved. */
@Composable
internal fun EditChatInfoRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditChatInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showDetails = isDebuggable()
    val haptics = LocalHapticFeedback.current
    val currentOnBack by rememberUpdatedState(onBack)
    val photoPicker =
        rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) viewModel.onEvent(EditChatInfoEvent.PhotoPicked(uri.toString()))
        }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EditChatInfoEffect.Saved -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    currentOnBack()
                }

                is EditChatInfoEffect.ShowError -> {
                    haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    snackbarHostState.showSnackbar(errorMessage(resources, effect.error, showDetails))
                }
            }
        }
    }
    EditChatInfoScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        onPickPhoto = { photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
    )
}

/**
 * Editing the photo, title and description (`docs/DESIGN_SYSTEM.md` §6 "Group and channel info"), a settings-style
 * page:
 * - **Photo**: a segment of its own on the rows' container with the shared [PhotoEditor] (the photo large and round,
 *   "Set new photo", which opens the system photo picker through [onPickPhoto], and "Remove photo" in `error`, which
 *   asks with an `AlertDialog`), its upload's progress around it. It changes at once, apart from "Save".
 * - **Texts**: the name and the description in rounded tonal `TextField`s on the page, the description over several
 *   lines with what it is for below it, a field's error below it; a `LinearWavyProgressIndicator` while Telegram
 *   answers, then a default `Button` "Save" at the end.
 *
 * The content keeps clear of the keyboard.
 */
@Suppress("LongParameterList") // The state, where its events go, back, the snackbars and the photo picker.
@Composable
internal fun EditChatInfoScreen(
    state: EditChatInfoUiState,
    onEvent: (EditChatInfoEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onPickPhoto: () -> Unit = {},
) {
    InfoPageScaffold(
        title = stringResource(R.string.chat_info_edit),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.ime),
    ) { innerPadding ->
        when (state) {
            EditChatInfoUiState.Loading -> {
                PageLoading(innerPadding)
            }

            is EditChatInfoUiState.Error -> {
                InfoPageBody(innerPadding) { PageError(state.error) { onEvent(EditChatInfoEvent.Retry) } }
            }

            is EditChatInfoUiState.Content -> {
                InfoPageContent(innerPadding) {
                    PhotoSegment(state, onEvent, onPickPhoto)
                    EditFields(state, onEvent)
                }
                if (state.confirmingPhotoRemoval) RemovePhotoDialog(state.kind, onEvent)
            }
        }
    }
}

/** The photo on the rows' container, in a segment of its own, with what can be done with it. */
@Composable
private fun PhotoSegment(
    state: EditChatInfoUiState.Content,
    onEvent: (EditChatInfoEvent) -> Unit,
    onPickPhoto: () -> Unit,
) {
    Surface(
        shape = ListItemDefaults.segmentedShapes(index = 0, count = 1).shape,
        color = profileRowContainerColor(),
    ) {
        PhotoEditor(
            photo = state.photo,
            name = state.title,
            avatarId = state.avatarId,
            task = state.photoTask,
            contentDescription =
                stringResource(
                    if (state.kind ==
                        GroupKind.Channel
                    ) {
                        R.string.chat_info_channel_photo
                    } else {
                        R.string.chat_info_group_photo
                    },
                ),
            onPick = onPickPhoto,
            onRemove = { onEvent(EditChatInfoEvent.RemovePhoto) },
        )
    }
}

/** Whether to remove the photo: an `AlertDialog`, as every destructive action asks. */
@Composable
private fun RemovePhotoDialog(
    kind: GroupKind,
    onEvent: (EditChatInfoEvent) -> Unit,
) {
    val channel = kind == GroupKind.Channel
    ConfirmationDialog(
        title =
            stringResource(
                if (channel) R.string.chat_info_remove_photo_title_channel else R.string.chat_info_remove_photo_title,
            ),
        text =
            stringResource(
                if (channel) R.string.chat_info_remove_photo_text_channel else R.string.chat_info_remove_photo_text,
            ),
        confirm = stringResource(R.string.chat_info_remove_photo_confirm),
        onConfirm = { onEvent(EditChatInfoEvent.ConfirmPhotoRemoval) },
        onDismiss = { onEvent(EditChatInfoEvent.DismissPhotoRemoval) },
    )
}

@Composable
private fun EditFields(
    state: EditChatInfoUiState.Content,
    onEvent: (EditChatInfoEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        InfoTextField(
            value = state.title,
            onValueChange = { onEvent(EditChatInfoEvent.TitleChanged(it)) },
            label =
                stringResource(
                    if (state.kind == GroupKind.Channel) {
                        R.string.chat_info_channel_name
                    } else {
                        R.string.chat_info_group_name
                    },
                ),
            enabled = !state.isSaving,
            error = state.titleError,
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Next,
                ),
        )
        InfoTextField(
            value = state.description,
            onValueChange = { onEvent(EditChatInfoEvent.DescriptionChanged(it)) },
            label = stringResource(R.string.chat_info_description),
            enabled = !state.isSaving,
            error = state.descriptionError,
            supporting = stringResource(R.string.chat_info_description_note),
            singleLine = false,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        if (state.isSaving) SavingIndicator()
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onEvent(EditChatInfoEvent.Save) },
                shapes = ButtonDefaults.shapes(),
                enabled = state.canSave,
            ) {
                Text(stringResource(R.string.chat_info_save), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/**
 * A text field of the group pages (`docs/DESIGN_SYSTEM.md` §6 "Text fields"): the rounded tonal `TextField`
 * (`TextFieldDefaults.roundedShape`, `tonalColors()`) as Edit profile's, user content in its own direction, [error]'s
 * text below it in `error` (announced as the field's error), else [supporting].
 */
@Suppress("LongParameterList") // What the field shows and takes.
@Composable
internal fun InfoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    error: DomainError? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val errorText = error?.let { errorText(it) }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().semantics { if (errorText != null) error(errorText) },
        enabled = enabled,
        label = { Text(label) },
        supportingText =
            (errorText ?: supporting)?.let {
                {
                    // An error is announced as it appears, a hint is not (`REQUIREMENTS.md` §6.3).
                    Text(it, Modifier.semantics { if (errorText != null) liveRegion = LiveRegionMode.Polite })
                }
            },
        isError = errorText != null,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else MULTILINE_MIN_LINES,
        textStyle = LocalTextStyle.current.userContent,
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/** The lines a description's field shows at least, as Edit profile's bio. */
private const val MULTILINE_MIN_LINES = 3
