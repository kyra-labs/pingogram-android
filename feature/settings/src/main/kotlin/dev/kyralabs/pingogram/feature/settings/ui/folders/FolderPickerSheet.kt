package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderIncludedType
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.flow.drop
import dev.kyralabs.pingogram.core.ui.R as UiR

// The sheet that picks a folder's chats: the kinds of chats of its side, then the chats, toggled in place.

/** The picker as a `ModalBottomSheet`; dismissing it keeps what was picked, as "Done" does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FolderPickerSheet(
    picker: FolderPickerUiState,
    onEvent: (ChatFolderEditEvent) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = { onEvent(ChatFolderEditEvent.ClosePicker) },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        FolderPickerContent(picker, onEvent)
    }
}

/**
 * The picker's content: its title in `titleLargeEmphasized`, the search bar's input field, the kinds of chats with
 * checkboxes, the chats as toggleable rows inset like the chat list's (a picked one `large` in `secondaryContainer`, its
 * avatar turned into a check), and "Done".
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderPickerContent(
    picker: FolderPickerUiState,
    onEvent: (ChatFolderEditEvent) -> Unit,
) {
    val included = picker.side == FolderPickerSide.Included
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = TITLE) {
            Text(
                text =
                    stringResource(
                        pick(
                            included,
                            R.string.settings_chat_folder_picker_include,
                            R.string.settings_chat_folder_picker_exclude,
                        ),
                    ),
                modifier =
                    Modifier
                        .padding(horizontal = MaterialTheme.spacing.large)
                        .padding(bottom = MaterialTheme.spacing.medium)
                        .semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
        }
        item(key = SEARCH) {
            PickerSearchField(picker.query) { onEvent(ChatFolderEditEvent.PickerQueryChanged(it)) }
        }
        item(key = TYPES) { PickerHeader(R.string.settings_chat_folder_picker_types) }
        if (included) {
            items(ChatFolderIncludedType.entries, key = { it.name }) { type ->
                TypeRow(type.label, type.icon, type in picker.includedTypes) {
                    onEvent(ChatFolderEditEvent.ToggleIncludedType(type))
                }
            }
        } else {
            items(ChatFolderExcludedType.entries, key = { it.name }) { type ->
                TypeRow(type.label, type.icon, type in picker.excludedTypes) {
                    onEvent(ChatFolderEditEvent.ToggleExcludedType(type))
                }
            }
        }
        item(key = CHATS) { PickerHeader(R.string.settings_chat_folder_picker_chats) }
        if (picker.chats.isEmpty()) {
            item(key = NO_RESULTS) {
                Text(
                    text = stringResource(R.string.settings_chat_folder_picker_no_results),
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(picker.chats, key = { it.chatId }) { chat ->
            PickerChatRow(chat) { onEvent(ChatFolderEditEvent.TogglePickedChat(chat)) }
        }
        item(key = DONE) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(MaterialTheme.spacing.large),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(onClick = { onEvent(ChatFolderEditEvent.ClosePicker) }, shapes = ButtonDefaults.shapes()) {
                    Text(
                        stringResource(R.string.settings_chat_folder_picker_done),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                    )
                }
            }
        }
    }
}

/** The search bar's input field on its container, with a clear button while there is text. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.small),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.settings_chat_folder_picker_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.settings_chat_folder_picker_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
    }
}

@Composable
private fun PickerHeader(text: Int) {
    Text(
        text = stringResource(text),
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/** A kind of chats: its icon and name, and a checkbox; the whole row toggles it. */
@Composable
private fun TypeRow(
    label: Int,
    @DrawableRes icon: Int,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.pickerRow(MaterialTheme.spacing.small, pickerRowLook(false), checked, onToggle),
        leadingContent = {
            Icon(
                painterResource(icon),
                contentDescription = null,
                modifier = Modifier.padding(MaterialTheme.spacing.small),
            )
        },
        trailingContent = { Checkbox(checked = checked, onCheckedChange = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(stringResource(label))
    }
}

/** A chat: its avatar (a check while picked) and title as user content; the whole row toggles it. */
@Composable
private fun PickerChatRow(
    chat: FolderChatItem,
    onToggle: () -> Unit,
) {
    ListItem(
        modifier =
            Modifier.pickerRow(MaterialTheme.spacing.small, pickerRowLook(chat.selected), chat.selected, onToggle),
        leadingContent = {
            Avatar(
                photo = chat.photo,
                name = chat.title,
                id = chat.chatId,
                size = MaterialTheme.sizes.avatarRow,
                selected = chat.selected,
                onSelection = chat.selected,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(chat.title, style = LocalTextStyle.current.userContent, maxLines = 1)
    }
}

/** The look of a picker row: `large` in `secondaryContainer` while [selected]. */
@Composable
private fun pickerRowLook(selected: Boolean): Pair<Shape, Color> =
    if (selected) {
        MaterialTheme.shapes.large to MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.shapes.extraSmall to Color.Transparent
    }

/** A row inset by [inset] from the sheet's sides, in [look], that toggles as a checkbox. */
private fun Modifier.pickerRow(
    inset: Dp,
    look: Pair<Shape, Color>,
    checked: Boolean,
    onToggle: () -> Unit,
): Modifier =
    fillMaxWidth()
        .padding(horizontal = inset)
        .clip(look.first)
        .background(look.second)
        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle() })

private const val TITLE = "title"
private const val SEARCH = "search"
private const val TYPES = "types"
private const val CHATS = "chats"
private const val NO_RESULTS = "noResults"
private const val DONE = "done"
