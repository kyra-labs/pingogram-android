package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendButton
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * The date and time pickers for a custom closing moment, at the one chosen before; a moment later than Telegram's
 * `poll_open_period_max` allows is moved back to the latest it does.
 */
@Composable
private fun CloseDateDialog(
    editor: PollEditor,
    now: () -> Instant,
    onDismiss: () -> Unit,
) {
    ScheduleDialog(
        now = now,
        onDismiss = onDismiss,
        onSchedule = { at ->
            onDismiss()
            val latest = now().epochSecond + editor.limits.maxOpenPeriod
            editor.customCloseDate = minOf(at.epochSecond, latest)
            editor.closing = PollClosing.Custom
        },
        initial = editor.customCloseDate?.let(Instant::ofEpochSecond),
    )
}

/**
 * The form of a new poll (`REQUIREMENTS.md` FR-C5; `docs/DESIGN_SYSTEM.md` §6 "New poll", boards 5a and 5b): a
 * [ModalBottomSheet] that opens expanded, "New poll" with the send split button pinned above what scrolls: the
 * question, the photo ([pollMediaItem]), an optional description, the answer options ([pollOptionItems]: links,
 * reordering, up to Telegram's limit), a quiz's explanation, the settings and when the poll closes
 * ([pollSettingItems]).
 *
 * - The fields edit [editor] (the view model's), limited to Telegram's lengths; an option that repeats one above it
 *   says so in `error`.
 * - "Add media" opens the system Photo Picker (no permission), whose photo the view model prepares
 *   ([ChatEvent.PickPollPhoto]).
 * - Send waits until the poll is complete ([PollEditor.poll]); it confirms with a haptic, hides the sheet on its
 *   spring, then sends ([ChatEvent.SendPoll]); its menu sends without sound or at a time where the chat allows it.
 * - A custom closing time comes from the composer's date and time pickers ([ScheduleDialog]), within Telegram's limit.
 * - Dragging the sheet down, the scrim and the back gesture (Material's predictive back) dismiss it
 *   ([ChatEvent.DismissPoll]), which empties the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PollFormSheet(
    sheet: PollSheet,
    editor: PollEditor,
    onEvent: (ChatEvent) -> Unit,
    now: () -> Instant = Instant::now,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val canSend by remember(editor, sheet.isChannel) {
        derivedStateOf { editor.poll(sheet.isChannel, now().epochSecond) != null }
    }
    val send: (SendOptions) -> Unit =
        remember(sheetState, scope, haptics, editor, sheet.isChannel) {
            { options ->
                if (editor.poll(sheet.isChannel, now().epochSecond) != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope
                        .launch { sheetState.hide() }
                        .invokeOnCompletion { currentOnEvent(ChatEvent.SendPoll(options)) }
                }
            }
        }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    var pickingCloseDate by rememberSaveable { mutableStateOf(false) }
    val photoPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            uri?.let { currentOnEvent(ChatEvent.PickPollPhoto(it.toString())) }
        }
    val actions =
        remember(send) {
            PollFormActions(
                onSend = send,
                onSchedule = { scheduling = true },
                onPickPhoto = {
                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                onRemovePhoto = { currentOnEvent(ChatEvent.RemovePollPhoto) },
                onPickCloseDate = { pickingCloseDate = true },
            )
        }
    ModalBottomSheet(onDismissRequest = { onEvent(ChatEvent.DismissPoll) }, sheetState = sheetState) {
        PollForm(sheet = sheet, editor = editor, canSend = canSend, actions = actions, now = now)
    }
    if (scheduling) {
        ScheduleDialog(
            now = now,
            onDismiss = { scheduling = false },
            onSchedule = { at ->
                scheduling = false
                send(SendOptions(scheduledAt = at.epochSecond))
            },
        )
    }
    if (pickingCloseDate) CloseDateDialog(editor, now) { pickingCloseDate = false }
}

/** What the form's buttons do, which the sheet decides. */
@Immutable
internal class PollFormActions(
    val onSend: (SendOptions) -> Unit = {},
    val onSchedule: () -> Unit = {},
    val onPickPhoto: () -> Unit = {},
    val onRemovePhoto: () -> Unit = {},
    val onPickCloseDate: () -> Unit = {},
)

/** The form itself: its title and the send button over a lazy column bounded by the sheet. */
@Composable
internal fun ColumnScope.PollForm(
    sheet: PollSheet,
    editor: PollEditor,
    canSend: Boolean,
    actions: PollFormActions,
    now: () -> Instant = Instant::now,
) {
    val spacing = MaterialTheme.spacing
    val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.poll_title),
            modifier = Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        SendButton(
            canSchedule = sheet.canSchedule,
            onSend = { actions.onSend(SendOptions()) },
            onSendSilently = { actions.onSend(SendOptions(silent = true)) },
            onSchedule = actions.onSchedule,
            enabled = canSend,
        )
    }
    val listState = rememberLazyListState()
    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        item(key = QUESTION) {
            OutlinedTextField(
                state = editor.question,
                modifier = fieldModifier,
                label = { Text(stringResource(R.string.poll_question)) },
                inputTransformation = InputTransformation.maxLength(NewPoll.MAX_QUESTION_LENGTH),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = QUESTION_LINES),
            )
        }
        pollMediaItem(editor, actions)
        item(key = DESCRIPTION) {
            OutlinedTextField(
                state = editor.description,
                modifier = fieldModifier,
                label = { Text(stringResource(R.string.poll_description)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = QUESTION_LINES),
            )
        }
        pollOptionItems(editor, listState)
        pollSettingItems(sheet, editor, actions, now)
    }
}

private const val QUESTION = "question"
private const val DESCRIPTION = "description"

/** The lines the question and the description grow to before they scroll. */
private const val QUESTION_LINES = 4

@PingogramPreviews
@Composable
private fun PollFormPreview() {
    PingogramPreview {
        Column {
            PollForm(
                sheet = PollSheet(),
                editor = remember { PollEditor(AttachPreviewData.quiz) },
                canSend = true,
                actions = PollFormActions(),
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun PollFormFilledPreview() {
    PingogramPreview {
        Column {
            PollForm(
                sheet = PollSheet(),
                editor =
                    remember {
                        PollEditor(
                            AttachPreviewData.bookPoll,
                        ).apply { limits = AttachPreviewData.limits }
                    },
                canSend = true,
                actions = PollFormActions(),
            )
        }
    }
}
