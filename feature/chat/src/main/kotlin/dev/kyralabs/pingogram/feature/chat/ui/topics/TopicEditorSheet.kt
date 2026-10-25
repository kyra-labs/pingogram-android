package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ForumTopicLimits
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.rememberMorphShape
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The editor and the question before deleting while [state] has them (`docs/DESIGN_SYSTEM.md` §6 "Forum topics"), for
 * the topic list and a topic's conversation alike; what the user does goes to [onEvent].
 */
@Composable
internal fun TopicManagementDialogs(
    state: TopicManagementUiState,
    onEvent: (TopicEvent) -> Unit,
) {
    state.editor?.let { TopicEditorSheet(it, onEvent) }
    state.deleting?.let { DeleteTopicDialog(it, onEvent) }
}

/**
 * The editor of a new topic or of a topic's name, a [ModalBottomSheet] that opens expanded and scrolls where the window
 * is short: "New topic" or "Edit topic" in `titleLargeEmphasized`; the topic's icon in the color picked beside the
 * name in the rounded tonal `TextField`, its length against Telegram's limit below it ("4 / 128"), or why the name is
 * refused (a polite live region); for a new topic, Telegram's six colors as named chips ([TopicColorChip]) and why
 * they show in the theme's colors; for an existing one, that its color stays. While Telegram is asked, a wavy linear
 * progress indicator; then "Cancel" at the start and "Create" or "Save" at the end, which the keyboard's action
 * sends too. Dragging it down, the scrim and back close it ([TopicEvent.DismissEditor]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TopicEditorSheet(
    editor: TopicEditorUiState,
    onEvent: (TopicEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(TopicEvent.DismissEditor) }, sheetState = sheetState) {
        TopicEditorContent(editor, currentOnEvent)
    }
}

/** The editor's content, apart from its sheet for previews and tests. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TopicEditorContent(
    editor: TopicEditorUiState,
    onEvent: (TopicEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text =
                stringResource(
                    if (editor.isNew) R.string.topic_editor_new_title else R.string.topic_editor_edit_title,
                ),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier = Modifier.semantics { heading() },
        )
        NameRow(editor, onEvent)
        if (editor.isNew) {
            ColorPicker(editor.color, enabled = !editor.isSaving, onPick = { onEvent(TopicEvent.ColorPicked(it)) })
            Note(stringResource(R.string.topic_editor_color_note))
        } else if (!editor.isGeneral) {
            Note(stringResource(R.string.topic_editor_color_fixed))
        }
        if (editor.isSaving) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { onEvent(TopicEvent.DismissEditor) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.topic_editor_cancel))
            }
            Button(
                onClick = { onEvent(TopicEvent.Save) },
                enabled = editor.canSave,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    stringResource(if (editor.isNew) R.string.topic_editor_create else R.string.topic_editor_save),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}

/** The topic's icon in the color picked, then the name field with its length or its error below it. */
@Composable
private fun NameRow(
    editor: TopicEditorUiState,
    onEvent: (TopicEvent) -> Unit,
) {
    val colorName = stringResource(editor.color.label)
    val preview = stringResource(R.string.topic_editor_preview, colorName)
    val error =
        when {
            editor.isTooLong -> stringResource(R.string.topic_editor_too_long)
            editor.error != null -> errorText(editor.error)
            else -> null
        }
    val counter =
        stringResource(R.string.topic_editor_counter, editor.name.trim().length, ForumTopicLimits.MAX_NAME_LENGTH)
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.Top,
    ) {
        TopicIconBadge(
            icon = TopicIcon(editor.color, editor.isGeneral),
            size = MaterialTheme.sizes.avatarMedium,
            modifier = Modifier.semantics { contentDescription = preview },
        )
        TextField(
            value = editor.name,
            onValueChange = { onEvent(TopicEvent.NameChanged(it)) },
            modifier = Modifier.weight(1f),
            enabled = !editor.isSaving,
            textStyle = MaterialTheme.typography.bodyLarge.userContent,
            label = { Text(stringResource(R.string.topic_editor_name)) },
            supportingText = {
                Text(
                    text = error ?: counter,
                    modifier = Modifier.semantics { if (error != null) liveRegion = LiveRegionMode.Polite },
                )
            },
            isError = error != null,
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions { onEvent(TopicEvent.Save) },
            singleLine = true,
            shape = TextFieldDefaults.roundedShape,
            colors = TextFieldDefaults.tonalColors(),
        )
    }
}

/**
 * "Color", then Telegram's six topic colors as chips that wrap to the width, one radio group for screen readers. As
 * the theme shows them in three tonal pairs (`docs/DESIGN_SYSTEM.md` §2.2), each chip carries its name below it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPicker(
    picked: TopicColor,
    enabled: Boolean,
    onPick: (TopicColor) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        Text(
            text = stringResource(R.string.topic_editor_color),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() },
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth().selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            TopicColor.entries.forEach { color ->
                TopicColorChip(color, selected = color == picked, enabled = enabled, onClick = { onPick(color) })
            }
        }
    }
}

/**
 * One topic color, as the Theme page's chips: a `sizes.themeChip` circle in the color's tonal pair with the topic
 * symbol, its name below it in `labelMedium`; while [selected] the circle morphs into `MaterialShapes.Cookie9Sided` on
 * the default spatial spring (snapping with reduced motion) and shows a check. A radio button named after the color
 * for screen readers; a pick ticks (`SegmentTick`).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TopicColorChip(
    color: TopicColor,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (container, content) = topicTone(TopicIcon(color, isGeneral = false))
    val shape = rememberMorphShape(MaterialShapes.Circle, MaterialShapes.Cookie9Sided, morphed = selected)
    val haptics = LocalHapticFeedback.current
    val name = stringResource(color.label)
    Column(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onClick()
                }.semantics(mergeDescendants = true) { contentDescription = name }
                .padding(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Box(
            modifier = Modifier.size(MaterialTheme.sizes.themeChip).clip(shape).background(container),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(
                        painter = painterResource(UiR.drawable.ic_check),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .padding(MaterialTheme.spacing.extraSmall)
                                .size(IconButtonDefaults.smallIconSize),
                    )
                }
            } else {
                Icon(
                    painter = painterResource(UiR.drawable.ic_mode_comment_filled),
                    contentDescription = null,
                    tint = content,
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics {},
        )
    }
}

/** A note under the colors, in `bodySmall` `onSurfaceVariant`. */
@Composable
private fun Note(text: String) {
    Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * The question before deleting a topic (`docs/DESIGN_SYSTEM.md` §6: an [AlertDialog], for destructive confirmations
 * only): "Delete topic?", that the topic and its messages go for everyone, and "Delete" in `error`, which waits while
 * Telegram deletes it.
 */
@Composable
internal fun DeleteTopicDialog(
    deleting: TopicDeleteUiState,
    onEvent: (TopicEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(TopicEvent.DismissDelete) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(TopicEvent.ConfirmDelete) },
                enabled = !deleting.isDeleting,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(
                    stringResource(R.string.topic_delete_confirm),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(TopicEvent.DismissDelete) }) {
                Text(
                    stringResource(R.string.topic_editor_cancel),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        },
        icon = { Icon(painter = painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.topic_delete_title)) },
        text = { Text(stringResource(R.string.topic_delete_body, deleting.name)) },
    )
}

/** The name of [this] color, under its chip and for screen readers. */
internal val TopicColor.label: Int
    get() =
        when (this) {
            TopicColor.Blue -> R.string.topic_color_blue
            TopicColor.Yellow -> R.string.topic_color_yellow
            TopicColor.Violet -> R.string.topic_color_violet
            TopicColor.Green -> R.string.topic_color_green
            TopicColor.Rose -> R.string.topic_color_rose
            TopicColor.Red -> R.string.topic_color_red
        }

/** The editor's states for previews: a new topic in each color, a name too long, a refusal, a rename. */
internal class TopicEditorPreviewStates : PreviewParameterProvider<TopicEditorUiState> {
    override val values =
        TopicColor.entries.asSequence().map { TopicEditorUiState(topicId = null, name = "Plans", color = it) } +
            sequenceOf(
                TopicEditorUiState(
                    topicId = null,
                    name = "A".repeat(ForumTopicLimits.MAX_NAME_LENGTH + 1),
                    color = TopicColor.Rose,
                ),
                TopicEditorUiState(
                    topicId = null,
                    name = "Plans",
                    color = TopicColor.Green,
                    error = DomainError.Network,
                ),
                TopicEditorUiState(topicId = PREVIEW_TOPIC, name = "Help", color = TopicColor.Green, isSaving = true),
            )
}

@PingogramPreviews
@Composable
private fun TopicEditorPreview(
    @PreviewParameter(TopicEditorPreviewStates::class) editor: TopicEditorUiState,
) {
    PingogramPreview { TopicEditorContent(editor, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun DeleteTopicDialogPreview() {
    PingogramPreview { DeleteTopicDialog(TopicDeleteUiState(PREVIEW_TOPIC, "Help"), onEvent = {}) }
}

private const val PREVIEW_TOPIC = 12
