package dev.kyralabs.pingogram.feature.chat.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.SenderHues
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A tag's row (the owner's board 34c), on `surfaceContainerLow` with `large` corners (`surfaceContainerHighest` while
 * dragged): a drag handle (Pingogram's six-dot symbol, 48 dp; screen readers move the tag with "Move up" / "Move
 * down" instead), a dot of the tag's sender hue, its name (user content) over how many bookmarks have it, then "Rename"
 * and "Delete" `IconButton`s named with it.
 */
@Composable
internal fun BookmarkTagRow(
    tag: TagRow,
    moves: TagMoves,
    dragged: Boolean,
    handleModifier: Modifier,
    onEvent: (BookmarkTagsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val count = pluralStringResource(R.plurals.bookmark_tag_count, tag.count, tag.count)
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = if (dragged) colors.surfaceContainerHighest else colors.surfaceContainerLow,
    ) {
        Row(
            modifier =
                Modifier
                    .padding(end = MaterialTheme.spacing.extraSmall)
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${tag.name}, $count"
                        customActions = moveActions(resources, moves)
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = handleModifier.minimumInteractiveComponentSize().clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(UiR.drawable.ic_drag_indicator), contentDescription = null)
            }
            HueDot(tag.color)
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = MaterialTheme.spacing.medium),
            ) {
                Text(
                    text = tag.name,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge.userContent,
                )
                Text(
                    text = count,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TagRowActions(tag, onEvent)
        }
    }
}

/** A tag's "Rename" and "Delete" buttons, named with it. */
@Composable
private fun TagRowActions(
    tag: TagRow,
    onEvent: (BookmarkTagsEvent) -> Unit,
) {
    IconButton(
        onClick = { onEvent(BookmarkTagsEvent.StartEdit(tag.id)) },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(painterResource(UiR.drawable.ic_edit), stringResource(R.string.bookmark_tag_rename, tag.name))
    }
    IconButton(
        onClick = { onEvent(BookmarkTagsEvent.AskDelete(tag.id)) },
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painterResource(UiR.drawable.ic_delete),
            stringResource(R.string.bookmark_tag_delete, tag.name),
            tint = MaterialTheme.colorScheme.error,
        )
    }
}

private fun moveActions(
    resources: android.content.res.Resources,
    moves: TagMoves,
): List<CustomAccessibilityAction> =
    listOfNotNull(
        CustomAccessibilityAction(resources.getString(R.string.bookmark_tag_move_up)) {
            moves.onMove(true)
            true
        }.takeIf { moves.canMoveUp },
        CustomAccessibilityAction(resources.getString(R.string.bookmark_tag_move_down)) {
            moves.onMove(false)
            true
        }.takeIf { moves.canMoveDown },
    )

/** A tag's hue as a dot, decorative: the name and the count say what the row is. */
@Composable
private fun HueDot(color: Int) {
    Box(
        Modifier
            .size(MaterialTheme.sizes.inlineIcon)
            .background(MaterialTheme.pingogramColors.senderHues[color].ink, CircleShape),
    )
}

/**
 * A tag being written (board 34c, shown with its colors open): on `surfaceContainerHigh` with `large` corners, the
 * name in a filled one-line `TextField` (focused as it opens, at most [BookmarkTagsLimits.NAME_LENGTH_MAX] characters,
 * the keyboard's Done saving), "Cancel" and "Save" `IconButton`s, then the sender hues as swatches in a `FlowRow`, one
 * radio group named by the hues ("Color: Green"), the chosen one ringed and checked, a `SegmentTick` per pick.
 */
@Composable
internal fun BookmarkTagEditor(
    draft: TagDraft,
    onEvent: (BookmarkTagsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(focus) { focus.requestFocus() }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = draft.name,
                    onValueChange = { name ->
                        onEvent(BookmarkTagsEvent.EditName(name.take(BookmarkTagsLimits.NAME_LENGTH_MAX)))
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    label = { Text(stringResource(R.string.bookmark_tag_name)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.userContent,
                    keyboardOptions =
                        KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onEvent(BookmarkTagsEvent.SaveDraft) }),
                )
                IconButton(onClick = { onEvent(BookmarkTagsEvent.CancelDraft) }, shapes = IconButtonDefaults.shapes()) {
                    Icon(painterResource(UiR.drawable.ic_close), stringResource(R.string.bookmark_cancel))
                }
                IconButton(
                    onClick = { onEvent(BookmarkTagsEvent.SaveDraft) },
                    enabled = draft.canSave,
                    shapes = IconButtonDefaults.shapes(),
                ) {
                    Icon(painterResource(UiR.drawable.ic_check), stringResource(R.string.bookmark_save))
                }
            }
            HueSwatches(draft.color) { onEvent(BookmarkTagsEvent.EditColor(it)) }
        }
    }
}

@Composable
private fun HueSwatches(
    chosen: Int,
    onChoose: (Int) -> Unit,
) {
    val hues = MaterialTheme.pingogramColors.senderHues
    val haptics = LocalHapticFeedback.current
    val names = LocalResources.current.getStringArray(R.array.bookmark_tag_colors)
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        repeat(SenderHues.COUNT) { index ->
            val selected = index == chosen
            Box(
                modifier =
                    Modifier
                        .minimumInteractiveComponentSize()
                        .selectable(selected = selected, role = Role.RadioButton) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onChoose(index)
                        }.semantics {
                            contentDescription = names.getOrElse(index) { "" }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(MaterialTheme.sizes.avatarExtraSmall)
                            .background(hues[index].vivid.container, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            painterResource(UiR.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
                            tint = hues[index].vivid.content,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The question before deleting [tag] (board 34c): an `AlertDialog` with the delete symbol, "Delete “Recipes”?", what
 * becomes of the bookmarks that have it (those with other tags keep them, those with only it will have none, none is
 * removed), "Cancel", which takes the focus, and "Delete tag" in `error`.
 */
@Composable
internal fun DeleteTagDialog(
    tag: TagRow,
    onEvent: (BookmarkTagsEvent) -> Unit,
) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(cancelFocus) { cancelFocus.requestFocus() }
    AlertDialog(
        onDismissRequest = { onEvent(BookmarkTagsEvent.CancelDelete) },
        icon = { Icon(painterResource(UiR.drawable.ic_delete), contentDescription = null) },
        title = { Text(stringResource(R.string.bookmark_tag_delete_title, tag.name)) },
        text = {
            val others = tag.count - tag.alone
            val text =
                if (tag.count == 0) {
                    stringResource(R.string.bookmark_tag_delete_unused)
                } else {
                    listOfNotNull(
                        pluralStringResource(
                            R.plurals.bookmark_tag_delete_others,
                            others,
                            others,
                        ).takeIf { others > 0 },
                        pluralStringResource(R.plurals.bookmark_tag_delete_alone, tag.alone, tag.alone)
                            .takeIf { tag.alone > 0 },
                        stringResource(R.string.bookmark_tag_delete_kept),
                    ).joinToString(" ")
                }
            Text(text)
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(BookmarkTagsEvent.ConfirmDelete) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.bookmark_tag_delete_confirm)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(BookmarkTagsEvent.CancelDelete) },
                modifier = Modifier.focusRequester(cancelFocus),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.bookmark_cancel)) }
        },
    )
}
