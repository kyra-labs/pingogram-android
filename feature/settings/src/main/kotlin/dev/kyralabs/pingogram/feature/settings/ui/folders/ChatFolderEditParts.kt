package dev.kyralabs.pingogram.feature.settings.ui.folders

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import dev.kyralabs.pingogram.core.domain.model.ChatFolderExcludedType
import dev.kyralabs.pingogram.core.domain.model.ChatFolderSettings
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialog
import dev.kyralabs.pingogram.feature.settings.ui.DestructiveDialogTexts
import dev.kyralabs.pingogram.feature.settings.ui.RadioItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroupScope
import dev.kyralabs.pingogram.feature.settings.ui.SettingsRow
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the folder editor (ChatFolderEditScreen).

/**
 * The name in the rounded tonal field, user content, with how many of its 12 characters are used, or why it is
 * missing.
 */
@Composable
internal fun FolderNameField(
    state: ChatFolderEditUiState.Content,
    onEvent: (ChatFolderEditEvent) -> Unit,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    TextField(
        value = state.name,
        onValueChange = { onEvent(ChatFolderEditEvent.NameChanged(it)) },
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.isBusy,
        label = { Text(stringResource(R.string.settings_chat_folder_name)) },
        supportingText = {
            Text(
                if (state.nameMissing) {
                    stringResource(R.string.settings_chat_folder_name_required)
                } else {
                    stringResource(
                        R.string.settings_chat_folder_name_count,
                        numbers.format(state.name.length),
                        numbers.format(ChatFolderSettings.MAX_TITLE_LENGTH),
                    )
                },
            )
        },
        isError = state.nameMissing,
        singleLine = true,
        textStyle = LocalTextStyle.current.userContent,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        shape = TextFieldDefaults.roundedShape,
        colors = TextFieldDefaults.tonalColors(),
    )
}

/**
 * The folder's icon, one choice: "Automatic" (Telegram picks one from the folder's chats) as a radio row, then the
 * symbols as toggle buttons that wrap to the width, the chosen one in `secondaryContainer`, each a radio button named
 * for screen readers.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderIconGroup(
    state: ChatFolderEditUiState.Content,
    onEvent: (ChatFolderEditEvent) -> Unit,
) {
    val colors =
        IconButtonDefaults.iconToggleButtonColors(
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    val chosen = FolderIcon.of(state.iconName)
    SettingsGroup(
        title = stringResource(R.string.settings_chat_folder_section_icon),
        rowsModifier = Modifier.selectableGroup(),
    ) {
        item(key = AUTOMATIC) { shapes ->
            RadioItem(
                label = stringResource(R.string.settings_chat_folder_icon_automatic),
                selected = chosen == null,
                enabled = !state.isBusy,
                shapes = shapes,
                onClick = { onEvent(ChatFolderEditEvent.SelectIcon(null)) },
            )
        }
        item(key = ICONS) { shapes ->
            SettingsSegment(shapes) {
                FlowRow(
                    modifier = Modifier.padding(MaterialTheme.spacing.small),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                ) {
                    FolderIcon.entries.forEach { icon ->
                        val label = stringResource(icon.label)
                        val checked = icon == chosen
                        IconToggleButton(
                            checked = checked,
                            onCheckedChange = { onEvent(ChatFolderEditEvent.SelectIcon(icon.tdlibName)) },
                            enabled = !state.isBusy,
                            shapes = IconButtonDefaults.toggleableShapes(),
                            colors = colors,
                            modifier =
                                Modifier.semantics {
                                    role = Role.RadioButton
                                    selected = checked
                                    contentDescription = label
                                },
                        ) {
                            Icon(painterResource(icon.icon), contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One side of the folder: "Add chats" or "Exclude chats", which opens the picker, then its kinds of chats and its
 * chats, each with a remove button that names it; why "Save" was refused below the included side.
 */
@Composable
internal fun FolderChatsGroup(
    state: ChatFolderEditUiState.Content,
    side: FolderPickerSide,
    onEvent: (ChatFolderEditEvent) -> Unit,
) {
    val included = side == FolderPickerSide.Included
    val footer =
        when {
            included && state.nothingIncluded -> null
            included -> stringResource(R.string.settings_chat_folder_included_footer)
            else -> stringResource(R.string.settings_chat_folder_excluded_footer)
        }
    Column {
        SettingsGroup(
            title =
                stringResource(
                    pick(
                        included,
                        R.string.settings_chat_folder_section_included,
                        R.string.settings_chat_folder_section_excluded,
                    ),
                ),
            footer = footer,
        ) {
            item(key = ADD) { shapes ->
                SettingsRow(
                    title =
                        stringResource(
                            pick(
                                included,
                                R.string.settings_chat_folder_add_chats,
                                R.string.settings_chat_folder_exclude_chats,
                            ),
                        ),
                    shapes = shapes,
                    icon = if (included) UiR.drawable.ic_add else UiR.drawable.ic_visibility_off,
                    onClick = { onEvent(ChatFolderEditEvent.OpenPicker(side)) },
                    enabled = !state.isBusy,
                )
            }
            if (included) {
                state.includedTypes.sortedBy { it.ordinal }.forEach { type ->
                    typeRow(type.name, type.label, type.icon, state.isBusy) {
                        onEvent(ChatFolderEditEvent.ToggleIncludedType(type))
                    }
                }
            } else {
                state.excludedTypes.sortedBy { it.ordinal }.forEach { type ->
                    typeRow(type.name, type.label, type.icon, state.isBusy) {
                        onEvent(ChatFolderEditEvent.ToggleExcludedType(type))
                    }
                }
            }
            (if (included) state.includedChats else state.excludedChats).forEach { chat ->
                item(key = chat.chatId) { shapes ->
                    FolderChatRow(
                        chat,
                        shapes,
                        state.isBusy,
                    ) { onEvent(ChatFolderEditEvent.RemoveChat(side, chat.chatId)) }
                }
            }
        }
        if (included && state.nothingIncluded) {
            Text(
                text = stringResource(R.string.settings_chat_folder_no_chats),
                modifier = Modifier.padding(start = MaterialTheme.spacing.large, top = MaterialTheme.spacing.small),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** A kind of chats on one side of the folder, with its remove button. */
private fun SettingsGroupScope.typeRow(
    key: String,
    @StringRes label: Int,
    @DrawableRes icon: Int,
    busy: Boolean,
    onRemove: () -> Unit,
) {
    item(key = key) { shapes ->
        val name = stringResource(label)
        SettingsRow(
            title = name,
            shapes = shapes,
            icon = icon,
            trailingContent = { RemoveButton(name, busy, onRemove) },
        )
    }
}

/** A chat on one side of the folder: its avatar and title, with its remove button. */
@Composable
private fun FolderChatRow(
    chat: FolderChatItem,
    shapes: ListItemShapes,
    busy: Boolean,
    onRemove: () -> Unit,
) {
    SegmentedListItem(
        shapes = shapes,
        leadingContent = {
            Avatar(photo = chat.photo, name = chat.title, id = chat.chatId, size = MaterialTheme.sizes.avatarSmall)
        },
        trailingContent = { RemoveButton(chat.title, busy, onRemove) },
        colors = settingsRowColors(),
    ) {
        Text(chat.title, style = LocalTextStyle.current.userContent)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RemoveButton(
    name: String,
    busy: Boolean,
    onRemove: () -> Unit,
) {
    val label = stringResource(R.string.settings_chat_folder_remove, name)
    IconButton(onClick = onRemove, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
        Icon(painterResource(UiR.drawable.ic_close), contentDescription = label)
    }
}

/**
 * The progress while Telegram is asked, then "Delete folder" (an existing folder's) at the start, in the error role,
 * and "Save" or "Create" at the end; the delete question while it shows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FolderEditActions(
    state: ChatFolderEditUiState.Content,
    onEvent: (ChatFolderEditEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
        if (state.isBusy) {
            val description =
                stringResource(
                    pick(
                        state.isDeleting,
                        R.string.settings_chat_folder_deleting,
                        R.string.settings_chat_folder_saving,
                    ),
                )
            LinearWavyProgressIndicator(Modifier.fillMaxWidth().semantics { contentDescription = description })
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (state.isNew) Arrangement.End else Arrangement.SpaceBetween,
        ) {
            if (!state.isNew) {
                TextButton(
                    onClick = { onEvent(ChatFolderEditEvent.RequestDelete) },
                    enabled = !state.isBusy,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_chat_folder_delete))
                }
            }
            Button(
                onClick = { onEvent(ChatFolderEditEvent.Save) },
                enabled = !state.isBusy,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(
                    text =
                        stringResource(
                            pick(state.isNew, R.string.settings_chat_folder_create, R.string.settings_chat_folder_save),
                        ),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
    if (state.confirmingDelete) {
        DestructiveDialog(
            icon = UiR.drawable.ic_delete,
            texts =
                DestructiveDialogTexts(
                    title = stringResource(R.string.settings_chat_folder_delete_dialog_title),
                    text = stringResource(R.string.settings_chat_folder_delete_dialog_text),
                    confirm = stringResource(R.string.settings_chat_folder_delete_dialog_confirm),
                ),
            onConfirm = { onEvent(ChatFolderEditEvent.ConfirmDelete) },
            onDismiss = { onEvent(ChatFolderEditEvent.DismissDelete) },
        )
    }
}

/** How a kind of chats left out is named in the editor and the picker. */
internal val ChatFolderExcludedType.label: Int
    get() =
        when (this) {
            ChatFolderExcludedType.Muted -> R.string.settings_chat_folders_type_muted
            ChatFolderExcludedType.Read -> R.string.settings_chat_folders_type_read
            ChatFolderExcludedType.Archived -> R.string.settings_chat_folders_type_archived
        }

/** A kind of chats left out, as its row shows it. */
internal val ChatFolderExcludedType.icon: Int
    get() =
        when (this) {
            ChatFolderExcludedType.Muted -> UiR.drawable.ic_notifications_off
            ChatFolderExcludedType.Read -> UiR.drawable.ic_done_all
            ChatFolderExcludedType.Archived -> UiR.drawable.ic_archive
        }

/** [ifTrue] if [condition], else [ifFalse]: the string of one of two states. */
internal fun pick(
    condition: Boolean,
    ifTrue: Int,
    ifFalse: Int,
): Int = if (condition) ifTrue else ifFalse

private const val AUTOMATIC = "automatic"
private const val ICONS = "icons"
private const val ADD = "add"
