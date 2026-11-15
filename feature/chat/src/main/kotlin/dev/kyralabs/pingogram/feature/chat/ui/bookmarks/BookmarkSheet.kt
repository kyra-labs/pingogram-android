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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BookmarkEvent
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The bookmark sheet (`docs/DESIGN_SYSTEM.md` §6 "Bookmarks", the owner's board 34a): a `ModalBottomSheet` that opens
 * expanded, at most `contentMaxWidth`: the bookmark on a `MaterialShapes.Cookie9Sided` badge in `primaryContainer`
 * beside "Bookmark" in `titleLargeEmphasized` (a heading); what the message says on `surfaceContainerHigh` with `large`
 * corners; "Tags" over the tags as `FilterChip`s (chosen ones filled in the tag's hue with a check, the others with a
 * dot of it, so color is never the only signal; screen readers hear checkboxes named by the tag) and "New tag"; the
 * note in the rounded tonal field; that bookmarks stay on this phone and that Telegram's own Saved Messages tags are
 * separate (Telegram Premium: information, not an offer); then "Cancel" or "Remove bookmark" and "Save".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BookmarkSheet(
    state: BookmarkSheetUiState,
    onEvent: (BookmarkEvent) -> Unit,
) {
    PingogramBottomSheet(
        onDismissRequest = { onEvent(BookmarkEvent.Dismiss) },
        sheetState =
            rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        BookmarkSheetContent(state, onEvent)
    }
}

/** The sheet's content, apart from its window for previews and tests. */
@Composable
internal fun BookmarkSheetContent(
    state: BookmarkSheetUiState,
    onEvent: (BookmarkEvent) -> Unit,
) {
    val note = rememberSaveable(state.messageId, saver = TextFieldState.Saver) { TextFieldState(state.note) }
    val resources = LocalResources.current
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier =
                Modifier
                    .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = MaterialTheme.spacing.large)
                    .padding(bottom = MaterialTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            SheetHeader()
            MessagePreview(state)
            TagChips(state, onEvent)
            TextField(
                state = note,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.bookmark_note)) },
                textStyle = MaterialTheme.typography.bodyLarge.userContent,
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = NOTE_LINES),
                shape = TextFieldDefaults.roundedShape,
                colors = TextFieldDefaults.tonalColors(),
            )
            Text(
                text = stringResource(R.string.bookmark_on_this_phone),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.isSaving) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            SheetButtons(
                state = state,
                onEvent = onEvent,
                onSave = {
                    onEvent(
                        BookmarkEvent.Save(
                            note = note.text.toString(),
                            sender = state.sender?.resolve(resources).orEmpty(),
                            text = state.text?.resolve(resources).orEmpty(),
                        ),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SheetHeader() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.avatarRow)
                    .clip(MaterialShapes.Cookie9Sided.toShape())
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_bookmark_filled),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            text = stringResource(R.string.bookmark_title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
    }
}

/** Who sent the message and what it says, on `surfaceContainerHigh`. */
@Composable
private fun MessagePreview(state: BookmarkSheetUiState) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.large)
                .padding(MaterialTheme.spacing.medium),
    ) {
        state.sender?.let {
            Text(
                text = it.resolve(),
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.userContent,
            )
        }
        Text(
            text = state.text?.resolve().orEmpty(),
            maxLines = PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium.userContent,
        )
    }
}

/** "Tags" over the tags and "New tag", which opens a field for the new tag's name. */
@Composable
private fun TagChips(
    state: BookmarkSheetUiState,
    onEvent: (BookmarkEvent) -> Unit,
) {
    var adding by rememberSaveable(state.messageId) { mutableStateOf(false) }
    Text(
        text = stringResource(R.string.bookmark_tags),
        modifier = Modifier.semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        state.tags.forEach { choice -> TagChip(choice, onClick = { onEvent(BookmarkEvent.ToggleTag(choice.tag.id)) }) }
        AssistChip(
            onClick = { adding = true },
            label = { Text(stringResource(R.string.bookmark_new_tag)) },
            leadingIcon = {
                Icon(
                    painter = painterResource(UiR.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            },
        )
    }
    if (adding) {
        NewTagField(
            onAdd = { name ->
                onEvent(BookmarkEvent.NewTag(name))
                adding = false
            },
            onCancel = { adding = false },
        )
    }
}

/** A tag: chosen, filled in its hue with a check; else outlined with a dot of its hue. */
@Composable
private fun TagChip(
    choice: TagChoice,
    onClick: () -> Unit,
) {
    val hues = MaterialTheme.pingogramColors.senderHues
    val hue = hues[choice.tag.color.mod(hues.size)]
    FilterChip(
        selected = choice.isChosen,
        onClick = onClick,
        label = { Text(choice.tag.name, style = MaterialTheme.typography.labelLarge.userContent) },
        leadingIcon = {
            if (choice.isChosen) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            } else {
                Box(Modifier.size(MaterialTheme.sizes.inlineIconSmall), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(MaterialTheme.sizes.badgeIcon).background(hue.ink, CircleShape))
                }
            }
        },
        shapes = FilterChipDefaults.shapes(),
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = hue.vivid.container,
                selectedLabelColor = hue.vivid.content,
                selectedLeadingIconColor = hue.vivid.content,
            ),
    )
}

/** The new tag's name, with "Add" and "Cancel". */
@Composable
private fun NewTagField(
    onAdd: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val name = rememberTextFieldState()
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
        TextField(
            state = name,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.bookmark_tag_name)) },
            textStyle = MaterialTheme.typography.bodyLarge.userContent,
            lineLimits = TextFieldLineLimits.SingleLine,
            shape = TextFieldDefaults.roundedShape,
            colors = TextFieldDefaults.tonalColors(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = onCancel,
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.bookmark_cancel)) }
            TextButton(
                onClick = { onAdd(name.text.toString()) },
                enabled = name.text.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) { Text(stringResource(R.string.bookmark_add_tag)) }
        }
    }
}

/** "Cancel" or "Remove bookmark" at the start, "Save" at the end. */
@Composable
private fun SheetButtons(
    state: BookmarkSheetUiState,
    onEvent: (BookmarkEvent) -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.isBookmarked) {
            TextButton(
                onClick = { onEvent(BookmarkEvent.Remove) },
                enabled = !state.isSaving,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.bookmark_remove)) }
        } else {
            TextButton(onClick = { onEvent(BookmarkEvent.Dismiss) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.bookmark_cancel))
            }
        }
        Button(onClick = onSave, enabled = !state.isSaving && state.text != null, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.bookmark_save), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

private const val NOTE_LINES = 4
private const val PREVIEW_LINES = 3
