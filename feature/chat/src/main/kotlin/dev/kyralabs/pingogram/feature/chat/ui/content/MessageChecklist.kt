package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import dev.kyralabs.pingogram.core.domain.model.Checklist
import dev.kyralabs.pingogram.core.domain.model.ChecklistTask
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.NewChecklist
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.SenderAvatar
import dev.kyralabs.pingogram.feature.chat.ui.checklist.ChecklistUi
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A checklist (`REQUIREMENTS.md` FR-C4; `docs/DESIGN_SYSTEM.md` §6 "Checklists"), padded in its bubble and
 * `mediaMaxWidth` wide: the "Checklist" kicker with its symbol, the title, the tasks, how many are done, and "Add
 * task" where the user may add one.
 *
 * - **Tasks**: each a row of at least 48 dp with a round check before its text; where the user may mark tasks
 *   ([Checklist.canMarkTasksAsDone]) a tap ticks or unticks it ([ChecklistAction.Mark]) with a toggle haptic, and
 *   while the change goes to Telegram ([ChecklistUi.pending]) the check shows a [LoadingIndicator] and takes no tap.
 *   A done task is struck through in the secondary content color, with the avatar of who ticked it at its end
 *   ([ChecklistUi.completers]).
 * - **Progress**: a flat determinate bar of the done share (a level, not work under way) and "2 of 5 done", one
 *   item for screen readers.
 * - **Adding** ([Checklist.canAddTasks]): "Add task", a tonal pill on the bubble's block color, opens an inline
 *   field in its place ([ChecklistAction.OpenAddTask]); its text goes out with the check button or the keyboard's
 *   Done ([ChecklistAction.AddTask]), and the close button puts the field away ([ChecklistAction.DismissAddTask]).
 *   The text is the field's own state, kept while the bubble is in the list.
 *
 * The bubble reads the checklist to screen readers as one with its message (`checklistDescription`) and offers
 * ticking and adding as its custom actions (`checklistActions`), as `docs/DESIGN_SYSTEM.md` §9 reads every bubble.
 */
@Composable
internal fun MessageChecklist(
    content: MessageContent.Checklist,
    modifier: Modifier = Modifier,
    colors: BubbleColors = MaterialTheme.pingogramColors.incomingBubble,
    ui: ChecklistUi = ChecklistUi(),
    onAction: (ChecklistAction) -> Unit = {},
) {
    val list = content.list
    Column(
        modifier = modifier.width(MaterialTheme.sizes.mediaMaxWidth),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        ChecklistKicker(colors)
        Text(
            text = list.title.text,
            color = colors.content,
            style = MaterialTheme.typography.titleMediumEmphasized.userContent,
        )
        Column {
            list.tasks.forEach { task ->
                TaskRow(
                    task = task,
                    canMark = list.canMarkTasksAsDone,
                    isPending = task.id in ui.pending,
                    completer = ui.completers[task.id],
                    colors = colors,
                    onMark = { done -> onAction(ChecklistAction.Mark(task.id, done)) },
                )
            }
        }
        ChecklistProgress(list, colors)
        when {
            !list.canAddTasks || list.tasks.size >= NewChecklist.MAX_TASKS -> Unit
            ui.isAddingTask -> NewTaskField(ui.isSendingTask, colors, onAction)
            else -> AddTaskButton(colors) { onAction(ChecklistAction.OpenAddTask) }
        }
    }
}

/** The small label over the title: the checklist symbol and "CHECKLIST", in the secondary content color. */
@Composable
private fun ChecklistKicker(colors: BubbleColors) {
    val locale = LocalConfiguration.current.locales[0]
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_checklist),
            contentDescription = null,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
            tint = colors.secondaryContent,
        )
        Text(
            text = stringResource(R.string.checklist_kicker).uppercase(locale),
            color = colors.secondaryContent,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/**
 * One task: its round check (ticked in the bubble's accent, empty with an outline, or its progress), its text (struck
 * through and dimmed once done) and the avatar of who ticked it.
 */
@Suppress("LongParameterList") // The task, whether it takes a tap and is being changed, who ticked it, the colors.
@Composable
private fun TaskRow(
    task: ChecklistTask,
    canMark: Boolean,
    isPending: Boolean,
    completer: SenderAvatar?,
    colors: BubbleColors,
    onMark: (done: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val interaction =
        if (canMark && !isPending) {
            Modifier.toggleable(value = task.isDone, role = Role.Checkbox) { done ->
                haptics.performHapticFeedback(
                    if (done) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
                )
                onMark(done)
            }
        } else {
            Modifier
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.small)
                .then(interaction)
                .minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundCheck(done = task.isDone, isPending = isPending, enabled = canMark, colors = colors)
        Text(
            text = task.text.text,
            modifier = Modifier.weight(1f),
            color = if (task.isDone) colors.secondaryContent else colors.content,
            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
        if (task.isDone && completer != null) {
            Avatar(
                photo = completer.photo,
                name = completer.name.resolve(),
                id = completer.id,
                size = MaterialTheme.sizes.avatarInline,
                placeholderIcon = completer.placeholderIcon,
            )
        }
    }
}

/**
 * The check before a task: a circle outlined in the content color (dimmed while the user may not mark tasks), filled
 * with the accent and a check once done, or the task's progress while its change is on its way.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LoadingIndicator
@Composable
private fun RoundCheck(
    done: Boolean,
    isPending: Boolean,
    enabled: Boolean,
    colors: BubbleColors,
) {
    val size = MaterialTheme.sizes.avatarInline
    when {
        isPending -> {
            Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                LoadingIndicator(
                    modifier = Modifier.size(MaterialTheme.sizes.inlineProgressIndicator),
                    color = colors.accent,
                )
            }
        }

        done -> {
            Box(
                modifier = Modifier.size(size).background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                    tint = colors.onAccent,
                )
            }
        }

        else -> {
            Box(
                Modifier
                    .size(size)
                    .border(
                        width = MaterialTheme.sizes.avatarRing,
                        color = if (enabled) colors.content else colors.secondaryContent,
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** The done share as a flat bar beside "2 of 5 done"; one item for screen readers. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LinearWavyProgressIndicator
@Composable
private fun ChecklistProgress(
    list: Checklist,
    colors: BubbleColors,
) {
    val done = list.doneCount
    val total = list.tasks.size
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LinearWavyProgressIndicator(
            progress = { if (total == 0) 0f else done.toFloat() / total },
            modifier = Modifier.weight(1f),
            color = colors.accent,
            trackColor = colors.track,
            // A level, not work under way: no wave.
            amplitude = { 0f },
        )
        Text(
            text = stringResource(R.string.checklist_progress, done, total),
            color = colors.secondaryContent,
            style = MaterialTheme.typography.labelMediumEmphasized.copy(fontFeatureSettings = "tnum"),
        )
    }
}

/** "Add task", a tonal pill on the bubble's block color. */
@Composable
private fun AddTaskButton(
    colors: BubbleColors,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.block,
                contentColor = colors.blockContent,
            ),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_add),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Text(
            text = stringResource(R.string.checklist_add_task),
            modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
            style = MaterialTheme.typography.labelLargeEmphasized,
        )
    }
}

/** The inline field for a new task, with a close button before it and the check that adds it after it. */
@Composable
private fun NewTaskField(
    isSending: Boolean,
    colors: BubbleColors,
    onAction: (ChecklistAction) -> Unit,
) {
    val text = rememberSaveable(saver = TextFieldState.Saver) { TextFieldState() }
    val submit = {
        val task = text.text.toString().trim()
        if (task.isNotEmpty() && !isSending) onAction(ChecklistAction.AddTask(task))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onAction(ChecklistAction.DismissAddTask) },
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.secondaryContent),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.checklist_cancel_add),
            )
        }
        OutlinedTextField(
            state = text,
            modifier = Modifier.weight(1f),
            enabled = !isSending,
            placeholder = { Text(stringResource(R.string.checklist_new_task)) },
            inputTransformation = InputTransformation.maxLength(NewChecklist.MAX_TASK_LENGTH),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            lineLimits = TextFieldLineLimits.SingleLine,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.content,
                    unfocusedTextColor = colors.content,
                    disabledTextColor = colors.secondaryContent,
                    cursorColor = colors.accent,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.secondaryContent,
                    disabledBorderColor = colors.secondaryContent,
                    focusedPlaceholderColor = colors.secondaryContent,
                    unfocusedPlaceholderColor = colors.secondaryContent,
                    disabledPlaceholderColor = colors.secondaryContent,
                ),
        )
        IconButton(
            onClick = submit,
            enabled = !isSending,
            shapes = IconButtonDefaults.shapes(),
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.accent),
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_check),
                contentDescription = stringResource(R.string.checklist_add),
            )
        }
    }
}

/** What a tap in a checklist asks for, which the bubble turns into its message's event. */
internal sealed interface ChecklistAction {
    /** Mark task [taskId] (`ChecklistTask.id`) as [done], or as not done. */
    data class Mark(
        val taskId: Int,
        val done: Boolean,
    ) : ChecklistAction

    /** "Add task": the inline field opens. */
    data object OpenAddTask : ChecklistAction

    /** The inline field's close button. */
    data object DismissAddTask : ChecklistAction

    /** The inline field's [text] goes out as a new task. */
    data class AddTask(
        val text: String,
    ) : ChecklistAction {
        override fun toString() = "AddTask(…)"
    }
}
