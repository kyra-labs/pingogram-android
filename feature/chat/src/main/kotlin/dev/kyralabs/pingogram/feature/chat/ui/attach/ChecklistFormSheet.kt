package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.checklist.ChecklistSheet
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendButton
import kotlinx.coroutines.launch
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The form of a new checklist (`REQUIREMENTS.md` FR-C5; `docs/DESIGN_SYSTEM.md` §6 "Checklists"): a
 * [ModalBottomSheet] that scrolls, with the title, the tasks (one to thirty, each removable while more than one is
 * left, "Add a task" below them), switches for who else may mark tasks as done and add tasks, then the send split
 * button. Built as the poll form is ([PollFormSheet]).
 *
 * - The fields edit [editor] (the view model's), limited to Telegram's lengths; a task that repeats one above it says
 *   so in `error`.
 * - Send waits until the checklist is complete ([ChecklistEditor.checklist]); it confirms with a haptic, hides the
 *   sheet on its spring, then sends ([ChatEvent.SendChecklist]); its menu sends without sound or at a time where the
 *   chat allows it.
 * - Tasks come and go on the motion scheme's springs. Dragging the sheet down, the scrim and the back gesture
 *   (Material's predictive back) dismiss it ([ChatEvent.DismissChecklist]), which empties the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChecklistFormSheet(
    sheet: ChecklistSheet,
    editor: ChecklistEditor,
    onEvent: (ChatEvent) -> Unit,
    now: () -> Instant = Instant::now,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val canSend by remember(editor) { derivedStateOf { editor.checklist() != null } }
    val send: (SendOptions) -> Unit =
        remember(sheetState, scope, haptics, editor) {
            { options ->
                if (editor.checklist() != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope
                        .launch { sheetState.hide() }
                        .invokeOnCompletion { currentOnEvent(ChatEvent.SendChecklist(options)) }
                }
            }
        }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { onEvent(ChatEvent.DismissChecklist) }, sheetState = sheetState) {
        ChecklistForm(
            sheet = sheet,
            editor = editor,
            canSend = canSend,
            onSend = send,
            onSchedule = { scheduling = true },
        )
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
}

/** The form itself, in a lazy column bounded by the sheet. */
@Composable
internal fun ColumnScope.ChecklistForm(
    sheet: ChecklistSheet,
    editor: ChecklistEditor,
    canSend: Boolean,
    onSend: (SendOptions) -> Unit,
    onSchedule: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large)
    LazyColumn(
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        item(key = TITLE) {
            Text(
                text = stringResource(R.string.checklist_form_title),
                modifier = fieldModifier,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        item(key = NAME) {
            OutlinedTextField(
                state = editor.title,
                modifier = fieldModifier,
                label = { Text(stringResource(R.string.checklist_form_name)) },
                inputTransformation = InputTransformation.maxLength(NewChecklist.MAX_TITLE_LENGTH),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = TITLE_LINES),
            )
        }
        taskItems(editor)
        item(key = SETTINGS) { FormSectionHeader(stringResource(R.string.checklist_form_settings)) }
        item(key = OTHERS_MARK) {
            FormSwitchRow(
                title = stringResource(R.string.checklist_form_others_mark),
                checked = editor.othersCanMarkTasksAsDone,
                onCheckedChange = { editor.othersCanMarkTasksAsDone = it },
                note = stringResource(R.string.checklist_form_others_mark_note),
            )
        }
        item(key = OTHERS_ADD) {
            FormSwitchRow(
                title = stringResource(R.string.checklist_form_others_add),
                checked = editor.othersCanAddTasks,
                onCheckedChange = { editor.othersCanAddTasks = it },
                note = stringResource(R.string.checklist_form_others_add_note),
            )
        }
        item(key = SEND) {
            Box(Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small)) {
                Box(Modifier.align(Alignment.CenterEnd)) {
                    SendButton(
                        canSchedule = sheet.canSchedule,
                        onSend = { onSend(SendOptions()) },
                        onSendSilently = { onSend(SendOptions(silent = true)) },
                        onSchedule = onSchedule,
                        enabled = canSend,
                    )
                }
            }
        }
    }
}

/** The tasks' header, a row per task, and the button that adds one while fewer than thirty are there. */
private fun LazyListScope.taskItems(editor: ChecklistEditor) {
    item(key = TASKS) { FormSectionHeader(stringResource(R.string.checklist_form_tasks)) }
    itemsIndexed(editor.tasks, key = { _, task -> task.key }) { index, task ->
        val motionScheme = MaterialTheme.motionScheme
        TaskField(
            editor = editor,
            task = task,
            number = index + 1,
            modifier =
                Modifier.animateItem(
                    fadeInSpec = motionScheme.defaultEffectsSpec(),
                    placementSpec = motionScheme.defaultSpatialSpec(),
                    fadeOutSpec = motionScheme.fastEffectsSpec(),
                ),
        )
    }
    if (editor.canAddTask) {
        item(key = ADD) {
            val motionScheme = MaterialTheme.motionScheme
            TextButton(
                onClick = { editor.addTask() },
                modifier =
                    Modifier.padding(horizontal = MaterialTheme.spacing.small).animateItem(
                        fadeInSpec = motionScheme.defaultEffectsSpec(),
                        placementSpec = motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = motionScheme.fastEffectsSpec(),
                    ),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Text(
                    text = stringResource(R.string.checklist_form_add_task),
                    modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/**
 * Task [number]: its text field ("Task 1"), with a note in `error` when it repeats a task above it, and its remove
 * button while more than one is left.
 */
@Composable
private fun TaskField(
    editor: ChecklistEditor,
    task: ChecklistTaskField,
    number: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val duplicate = editor.isDuplicate(task.key)
    Row(
        modifier = modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            state = task.text,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.checklist_form_task, number)) },
            isError = duplicate,
            supportingText =
                if (duplicate) ({ Text(stringResource(R.string.checklist_form_task_repeated)) }) else null,
            inputTransformation = InputTransformation.maxLength(NewChecklist.MAX_TASK_LENGTH),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        if (editor.canRemoveTask) {
            IconButton(onClick = { editor.removeTask(task.key) }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_close),
                    contentDescription = stringResource(R.string.checklist_form_remove_task, number),
                )
            }
        }
    }
}

private const val TITLE = "title"
private const val NAME = "name"
private const val TASKS = "tasks"
private const val ADD = "add"
private const val SETTINGS = "settings"
private const val OTHERS_MARK = "othersMark"
private const val OTHERS_ADD = "othersAdd"
private const val SEND = "send"

/** The lines the title grows to before it scrolls. */
private const val TITLE_LINES = 3

@PingogramPreviews
@Composable
private fun ChecklistFormPreview() {
    PingogramPreview {
        Column {
            ChecklistForm(
                sheet = ChecklistSheet(),
                editor =
                    remember {
                        ChecklistEditor(
                            ChecklistDraft(
                                title = "Release 1.2",
                                tasks = listOf("Freeze strings", "Update screenshots", "Tag the release"),
                            ),
                        )
                    },
                canSend = true,
                onSend = {},
                onSchedule = {},
            )
        }
    }
}
