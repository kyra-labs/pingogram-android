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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
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
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The form of a new poll (`REQUIREMENTS.md` FR-C5): a [ModalBottomSheet] that scrolls, with the question, the answer
 * options (two to ten, each removable while more than two are left, "Add an option" below them), and switches for
 * anonymous voting, multiple answers and quiz mode, then the send split button.
 *
 * - The fields edit [editor] (the view model's), limited to Telegram's lengths; an option that repeats one above it
 *   says so in `error`.
 * - In quiz mode a radio button before each option marks the correct one, and multiple answers turn off. In a channel
 *   ([PollSheet.isChannel]) voting is always anonymous, and the switch says why.
 * - Send waits until the poll is complete ([PollEditor.poll]); it confirms with a haptic, hides the sheet on its
 *   spring, then sends ([ChatEvent.SendPoll]); its menu sends without sound or at a time where the chat allows it.
 * - Options come and go on the motion scheme's springs. Dragging the sheet down, the scrim and the back gesture
 *   (Material's predictive back) dismiss it ([ChatEvent.DismissPoll]), which empties the form.
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
    val canSend by remember(editor, sheet.isChannel) { derivedStateOf { editor.poll(sheet.isChannel) != null } }
    val send: (SendOptions) -> Unit =
        remember(sheetState, scope, haptics, editor, sheet.isChannel) {
            { options ->
                if (editor.poll(sheet.isChannel) != null) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope
                        .launch { sheetState.hide() }
                        .invokeOnCompletion { currentOnEvent(ChatEvent.SendPoll(options)) }
                }
            }
        }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = { onEvent(ChatEvent.DismissPoll) }, sheetState = sheetState) {
        PollForm(
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ColumnScope.PollForm(
    sheet: PollSheet,
    editor: PollEditor,
    canSend: Boolean,
    onSend: (SendOptions) -> Unit,
    onSchedule: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val motionScheme = MaterialTheme.motionScheme
    val fieldModifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large)
    LazyColumn(
        modifier = Modifier.weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        item(key = TITLE) {
            Text(
                text = stringResource(R.string.poll_title),
                modifier = fieldModifier,
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        item(key = QUESTION) {
            OutlinedTextField(
                state = editor.question,
                modifier = fieldModifier,
                label = { Text(stringResource(R.string.poll_question)) },
                inputTransformation = InputTransformation.maxLength(NewPoll.MAX_QUESTION_LENGTH),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = QUESTION_LINES),
            )
        }
        item(key = OPTIONS) {
            SectionHeader(
                title = stringResource(R.string.poll_options),
                note =
                    stringResource(R.string.poll_choose_correct)
                        .takeIf { editor.isQuiz && editor.correctOption == null },
            )
        }
        itemsIndexed(editor.options, key = { _, option -> option.key }) { index, option ->
            OptionRow(
                editor = editor,
                option = option,
                number = index + 1,
                modifier =
                    Modifier.animateItem(
                        fadeInSpec = motionScheme.defaultEffectsSpec(),
                        placementSpec = motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = motionScheme.fastEffectsSpec(),
                    ),
            )
        }
        if (editor.canAddOption) {
            item(key = ADD) {
                TextButton(
                    onClick = { editor.addOption() },
                    modifier =
                        Modifier.padding(horizontal = spacing.small).animateItem(
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
                        text = stringResource(R.string.poll_add_option),
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
        item(key = SETTINGS) { SectionHeader(stringResource(R.string.poll_settings)) }
        item(key = ANONYMOUS) {
            SwitchRow(
                title = stringResource(R.string.poll_anonymous),
                checked = sheet.isChannel || editor.isAnonymous,
                onCheckedChange = { editor.isAnonymous = it },
                enabled = !sheet.isChannel,
                note = stringResource(R.string.poll_anonymous_channel).takeIf { sheet.isChannel },
            )
        }
        item(key = MULTIPLE) {
            SwitchRow(
                title = stringResource(R.string.poll_multiple),
                checked = !editor.isQuiz && editor.allowsMultipleAnswers,
                onCheckedChange = { editor.allowsMultipleAnswers = it },
                enabled = !editor.isQuiz,
            )
        }
        item(key = QUIZ) {
            SwitchRow(
                title = stringResource(R.string.poll_quiz),
                checked = editor.isQuiz,
                onCheckedChange = { editor.isQuiz = it },
                note = stringResource(R.string.poll_quiz_note),
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

/**
 * Option [number]: in quiz mode a radio button that marks it as the correct answer, its text field ("Option 1"), with
 * a note in `error` when it repeats an option above it, and its remove button while more than two are left.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun OptionRow(
    editor: PollEditor,
    option: PollOptionField,
    number: Int,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val duplicate = editor.isDuplicate(option.key)
    val label = stringResource(R.string.poll_option, number)
    Row(
        modifier = modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editor.isQuiz) {
            val correctLabel = stringResource(R.string.poll_correct_option, number)
            RadioButton(
                selected = editor.correctOption == option.key,
                onClick = { editor.correctOption = option.key },
                modifier = Modifier.semantics { contentDescription = correctLabel },
            )
        }
        OutlinedTextField(
            state = option.text,
            modifier = Modifier.weight(1f),
            label = { Text(label) },
            isError = duplicate,
            supportingText = if (duplicate) ({ Text(stringResource(R.string.poll_option_repeated)) }) else null,
            inputTransformation = InputTransformation.maxLength(NewPoll.MAX_OPTION_LENGTH),
            lineLimits = TextFieldLineLimits.SingleLine,
        )
        if (editor.canRemoveOption) {
            IconButton(onClick = { editor.removeOption(option.key) }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_close),
                    contentDescription = stringResource(R.string.poll_remove_option, number),
                )
            }
        }
    }
}

/** A section's title in `titleSmallEmphasized` and `primary`, as in settings, with an optional [note] below. */
@Composable
private fun SectionHeader(
    title: String,
    note: String? = null,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(top = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Text(title, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleSmallEmphasized)
        note?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** A setting as a list item with a [Switch], the whole row toggling it, on the sheet's color. */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    note: String? = null,
) {
    ListItem(
        modifier =
            Modifier.toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        enabled = enabled,
        trailingContent = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) },
        supportingContent = note?.let { { Text(it) } },
        colors =
            ListItemDefaults.colors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent),
    ) {
        Text(title)
    }
}

private const val TITLE = "title"
private const val QUESTION = "question"
private const val OPTIONS = "options"
private const val ADD = "add"
private const val SETTINGS = "settings"
private const val ANONYMOUS = "anonymous"
private const val MULTIPLE = "multiple"
private const val QUIZ = "quiz"
private const val SEND = "send"

/** The lines the question grows to before it scrolls. */
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
                onSend = {},
                onSchedule = {},
            )
        }
    }
}
