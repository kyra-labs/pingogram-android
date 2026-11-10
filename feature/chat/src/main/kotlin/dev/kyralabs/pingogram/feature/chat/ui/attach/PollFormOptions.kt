package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.zIndex
import dev.kyralabs.pingogram.core.domain.model.NewPoll
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The answers (boards 5a, 5b): "Options" with "4 of 12" (Telegram's `poll_answer_count_max`), then a row per option
 * ([OptionRow]), "Add an option" while there is room, and in quiz mode the explanation. Options reorder by their drag
 * handles ([OptionDrag]): the dragged row follows the finger in its graphics layer above the others, which slide on
 * the motion scheme's default spatial spring, a `SegmentTick` at each place passed; screen readers move an option with
 * "Move up" and "Move down".
 */
internal fun LazyListScope.pollOptionItems(
    editor: PollEditor,
    listState: LazyListState,
) {
    item(key = OPTIONS) {
        FormSectionHeader(
            title = stringResource(R.string.poll_options),
            note =
                stringResource(
                    R.string.poll_choose_correct,
                ).takeIf { editor.isQuiz && editor.correctOptions.isEmpty() },
            trailing = stringResource(R.string.poll_option_count, editor.options.size, editor.limits.maxOptions),
        )
    }
    itemsIndexed(editor.options, key = { _, option -> option.key }) { index, option ->
        val drag = rememberOptionDrag(editor, listState)
        OptionRow(editor, option, index, drag, optionPlacement(drag, option.key))
    }
    if (editor.canAddOption) {
        item(key = ADD) {
            TextButton(
                onClick = { editor.addOption() },
                modifier = springPlacement().padding(horizontal = MaterialTheme.spacing.small),
                shapes = ButtonDefaults.shapes(),
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
    if (editor.isQuiz) {
        item(key = EXPLANATION) {
            OutlinedTextField(
                state = editor.explanation,
                modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large),
                label = { Text(stringResource(R.string.poll_explanation_field)) },
                supportingText = { Text(stringResource(R.string.poll_explanation_note)) },
                inputTransformation = InputTransformation.maxLength(NewPoll.MAX_EXPLANATION_LENGTH),
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = EXPLANATION_LINES),
            )
        }
    }
}

/** The row being dragged follows the finger above the rest; the others slide into their places on springs. */
@Composable
private fun LazyItemScope.optionPlacement(
    drag: OptionDrag,
    key: Long,
): Modifier =
    if (drag.draggedKey == key) {
        Modifier.zIndex(1f).graphicsLayer { translationY = drag.offset }
    } else {
        springPlacement()
    }

/** Comes, goes and moves on the motion scheme's springs. */
@Composable
private fun LazyItemScope.springPlacement(): Modifier {
    val motionScheme = MaterialTheme.motionScheme
    return Modifier.animateItem(
        fadeInSpec = motionScheme.defaultEffectsSpec(),
        placementSpec = motionScheme.defaultSpatialSpec(),
        fadeOutSpec = motionScheme.fastEffectsSpec(),
    )
}

/**
 * Option [index]: its drag handle, in quiz mode a check box that marks it as a correct answer, its text field
 * ("Option 1"), with a note in `error` when it repeats an option above it, a link button that adds or takes away its
 * link (the field below it, "Link · goodreads.com" once typed) and its remove button while more than two are left.
 */
@Composable
private fun OptionRow(
    editor: PollEditor,
    option: PollOptionField,
    index: Int,
    drag: OptionDrag,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    val number = index + 1
    val duplicate = editor.isDuplicate(option.key)
    Column(
        modifier = modifier.fillMaxWidth().padding(start = spacing.small, end = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(editor, option, index, drag)
            if (editor.isQuiz) {
                val correctLabel = stringResource(R.string.poll_correct_option, number)
                Checkbox(
                    checked = option.key in editor.correctOptions,
                    onCheckedChange = { editor.setCorrect(option.key, it) },
                    modifier = Modifier.semantics { contentDescription = correctLabel },
                )
            }
            OutlinedTextField(
                state = option.text,
                modifier = Modifier.weight(1f),
                label = { Text(stringResource(R.string.poll_option, number)) },
                isError = duplicate,
                supportingText = if (duplicate) ({ Text(stringResource(R.string.poll_option_repeated)) }) else null,
                inputTransformation = InputTransformation.maxLength(NewPoll.MAX_OPTION_LENGTH),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
            IconToggleButton(
                checked = option.link != null,
                onCheckedChange = { option.toggleLink() },
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_link),
                    contentDescription = stringResource(R.string.poll_option_link, number),
                )
            }
            if (editor.canRemoveOption) {
                IconButton(onClick = { editor.removeOption(option.key) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.poll_remove_option, number),
                    )
                }
            }
        }
        option.link?.let { link ->
            OutlinedTextField(
                state = link,
                modifier = Modifier.fillMaxWidth().padding(start = spacing.extraExtraLarge, end = spacing.small),
                label = { Text(stringResource(R.string.poll_option_link_field)) },
                textStyle = MaterialTheme.typography.bodyLarge.numericContent,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                lineLimits = TextFieldLineLimits.SingleLine,
            )
        }
    }
}

/** The handle that drags option [option] to another place, also offering "Move up" and "Move down". */
@Composable
private fun DragHandle(
    editor: PollEditor,
    option: PollOptionField,
    index: Int,
    drag: OptionDrag,
) {
    val haptics = LocalHapticFeedback.current
    val label = stringResource(R.string.poll_reorder_option, index + 1)
    val up = stringResource(R.string.poll_move_up)
    val down = stringResource(R.string.poll_move_down)
    val count = editor.options.size
    Icon(
        painter = painterResource(UiR.drawable.ic_drag_indicator),
        contentDescription = null,
        modifier =
            Modifier
                .padding(MaterialTheme.spacing.medium)
                .semantics {
                    contentDescription = label
                    customActions =
                        listOfNotNull(
                            CustomAccessibilityAction(up) { editor.moveOption(index, index - 1).let { true } }
                                .takeIf { index > 0 },
                            CustomAccessibilityAction(down) { editor.moveOption(index, index + 1).let { true } }
                                .takeIf { index < count - 1 },
                        )
                }.pointerInput(option.key) {
                    detectDragGestures(
                        onDragStart = { drag.start(option.key, haptics) },
                        onDragEnd = drag::stop,
                        onDragCancel = drag::stop,
                        onDrag = { change, amount ->
                            change.consume()
                            drag.by(amount.y)
                        },
                    )
                },
    )
}

@Composable
private fun rememberOptionDrag(
    editor: PollEditor,
    listState: LazyListState,
): OptionDrag = remember(editor, listState) { OptionDrag(editor, listState) }

/**
 * The option being dragged ([draggedKey]) and how far it moved from its place ([offset], read in the graphics
 * layer). Passing the middle of a neighbouring option moves it there ([PollEditor.moveOption]), the offset taking
 * back what the new place moved, with a `SegmentTick`.
 */
@Stable
private class OptionDrag(
    private val editor: PollEditor,
    private val listState: LazyListState,
) {
    var draggedKey by mutableStateOf<Long?>(null)
        private set

    var offset by mutableFloatStateOf(0f)
        private set

    private var haptics: HapticFeedback? = null

    fun start(
        key: Long,
        feedback: HapticFeedback,
    ) {
        draggedKey = key
        offset = 0f
        haptics = feedback
    }

    fun stop() {
        draggedKey = null
        offset = 0f
    }

    fun by(delta: Float) {
        val key = draggedKey ?: return
        offset += delta
        val items = listState.layoutInfo.visibleItemsInfo
        val current = items.firstOrNull { it.key == key }
        val keys = editor.options.map { it.key }
        val target =
            current?.let {
                val middle = (it.offset + offset + it.size / 2).toInt()
                items.firstOrNull { item ->
                    item.key != key && item.key in keys &&
                        middle in item.offset..(item.offset + item.size)
                }
            }
        if (current != null && target != null) {
            editor.moveOption(keys.indexOf(key), keys.indexOf(target.key))
            offset += current.offset - target.offset
            haptics?.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }
}

private const val OPTIONS = "options"
private const val ADD = "add"
private const val EXPLANATION = "explanation"

/** The lines the explanation grows to before it scrolls. */
private const val EXPLANATION_LINES = 3
