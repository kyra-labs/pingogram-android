package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.export.preview.ChatExportPreviewData

/**
 * The export page of a chat (`REQUIREMENTS.md` §5.9, `docs/DESIGN_SYSTEM.md` §6 "Chat export"), a settings page in
 * look:
 *
 * - **Form**: the chat's name over what happens to the export; "Format" as connected [ToggleButton]s (HTML, JSON);
 *   "Media" as switch rows (photos, videos, voice messages, files) and the largest file to copy on an expressive
 *   [Slider]; "Messages" as radio rows (all, or from a day picked in a [DatePickerDialog]); the "Folder", which
 *   [onChooseFolder] picks through the Storage Access Framework; then "Export" (a default [Button]).
 * - **Running**: how far it is, a [LinearWavyProgressIndicator] (determinate where the chat's size is known), how many
 *   messages so far or Telegram's flood wait, and "Cancel export".
 * - **Done**: what was exported, "Open folder" ([onOpenFolder]) and "Export again"; **failed**: why, and "Try again".
 * - A chat that cannot be exported, or read, says so; loading shows a delayed loading indicator.
 *
 * Steps crossfade on the effects springs, resizing on the default spatial spring.
 */
@Suppress("LongParameterList") // The state, its events, the page's navigation and the platform pickers.
@Composable
internal fun ChatExportScreen(
    state: ChatExportUiState,
    onEvent: (ChatExportEvent) -> Unit,
    onBack: () -> Unit,
    onChooseFolder: () -> Unit,
    onOpenFolder: (folderUri: String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ExportPageScaffold(
        title = stringResource(R.string.chat_export_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    ) { innerPadding ->
        ExportPageBody(innerPadding) { contentPadding ->
            ExportSteps(state, onEvent, onChooseFolder, onOpenFolder, contentPadding)
        }
    }
}

/** What the page shows, one step at a time, crossfading. */
@Composable
private fun ExportSteps(
    state: ChatExportUiState,
    onEvent: (ChatExportEvent) -> Unit,
    onChooseFolder: () -> Unit,
    onOpenFolder: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            fadeIn(motionScheme.defaultEffectsSpec()) togetherWith
                fadeOut(motionScheme.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motionScheme.defaultSpatialSpec() }
        },
        contentKey = { it.step },
        label = "chatExportStep",
    ) { shown ->
        val padded = Modifier.padding(contentPadding)
        when (shown) {
            ChatExportUiState.Loading -> {
                Box(padded.fillMaxSize(), contentAlignment = Alignment.Center) { DelayedLoadingIndicator() }
            }

            is ChatExportUiState.Error -> {
                ErrorState(
                    title = stringResource(R.string.chat_export_error_title),
                    body = errorText(shown.error),
                    action = stringResource(R.string.chat_export_try_again),
                    onAction = { onEvent(ChatExportEvent.Retry) },
                    modifier = padded,
                )
            }

            is ChatExportUiState.NotAllowed -> {
                NotAllowedState(shown.isSecretChat, padded)
            }

            is ChatExportUiState.Content -> {
                when (val run = shown.run) {
                    null -> ExportForm(shown, onEvent, onChooseFolder, padded)
                    is ExportRun.Running -> RunningState(shown, run, onEvent, padded)
                    is ExportRun.Done -> DoneState(run.result, onEvent, onOpenFolder, padded)
                    is ExportRun.Failed -> FailedState(run.error, onEvent, padded)
                }
            }
        }
    }
}

/** Which step a state is, so changes within one (a switch, the progress) do not crossfade. */
private val ChatExportUiState.step: Int
    get() =
        when (this) {
            ChatExportUiState.Loading -> {
                0
            }

            is ChatExportUiState.Error -> {
                1
            }

            is ChatExportUiState.NotAllowed -> {
                2
            }

            is ChatExportUiState.Content -> {
                when (run) {
                    null -> FORM_STEP
                    is ExportRun.Running -> FORM_STEP + 1
                    is ExportRun.Done -> FORM_STEP + 2
                    is ExportRun.Failed -> FORM_STEP + 3
                }
            }
        }

private const val FORM_STEP = 3

@PingogramPreviews
@Composable
private fun ChatExportFormPreview() {
    PingogramPreview {
        ChatExportScreen(ChatExportPreviewData.form, onEvent = {}, onBack = {}, onChooseFolder = {}, onOpenFolder = {})
    }
}

@Preview
@Composable
private fun ChatExportRunningPreview() {
    PingogramPreview {
        ChatExportScreen(
            ChatExportPreviewData.running,
            onEvent = {},
            onBack = {},
            onChooseFolder = {},
            onOpenFolder = {},
        )
    }
}

@Preview
@Composable
private fun ChatExportDonePreview() {
    PingogramPreview {
        ChatExportScreen(ChatExportPreviewData.done, onEvent = {}, onBack = {}, onChooseFolder = {}, onOpenFolder = {})
    }
}

@Preview
@Composable
private fun ChatExportFailedPreview() {
    PingogramPreview {
        ChatExportScreen(
            ChatExportPreviewData.failed,
            onEvent = {},
            onBack = {},
            onChooseFolder = {},
            onOpenFolder = {},
        )
    }
}

@Preview
@Composable
private fun ChatExportNotAllowedPreview() {
    PingogramPreview {
        ChatExportScreen(
            ChatExportUiState.NotAllowed(isSecretChat = true),
            onEvent = {},
            onBack = {},
            onChooseFolder = {},
            onOpenFolder = {},
        )
    }
}

@Preview
@Composable
private fun ChatExportErrorPreview() {
    PingogramPreview {
        ChatExportScreen(
            ChatExportUiState.Error(DomainError.Network),
            onEvent = {},
            onBack = {},
            onChooseFolder = {},
            onOpenFolder = {},
        )
    }
}

@Preview
@Composable
private fun ChatExportLoadingPreview() {
    PingogramPreview {
        ChatExportScreen(ChatExportUiState.Loading, onEvent = {}, onBack = {}, onChooseFolder = {}, onOpenFolder = {})
    }
}
