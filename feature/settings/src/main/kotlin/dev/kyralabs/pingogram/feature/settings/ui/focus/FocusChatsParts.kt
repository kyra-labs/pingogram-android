package dev.kyralabs.pingogram.feature.settings.ui.focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.PickerAvatarRow
import dev.kyralabs.pingogram.feature.settings.ui.PickerDone
import dev.kyralabs.pingogram.feature.settings.ui.PickerSearchField
import dev.kyralabs.pingogram.feature.settings.ui.PickerTitle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsGroup
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSegment
import dev.kyralabs.pingogram.core.ui.R as UiR

// "Also allow" of the Focus profile editor (FocusEditScreen), board 31.

/**
 * "Also allow": the chats the profile lets through whatever their folder, each an `InputChip` with its avatar that a
 * tap removes ("Remove Ada" for screen readers), then "Add chats", an `AssistChip` that opens the picker.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FocusChatsGroup(
    state: FocusEditUiState,
    onEvent: (FocusEditEvent) -> Unit,
) {
    SettingsGroup(
        title = stringResource(R.string.settings_focus_also_allow),
        footer = stringResource(R.string.settings_focus_also_allow_footer),
    ) {
        item { shapes ->
            SettingsSegment(shapes) {
                FlowRow(
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.medium),
                    horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                ) {
                    state.allowedChats.forEach { chat ->
                        AllowedChatChip(chat, enabled = !state.isBusy) { onEvent(FocusChatEvent.Remove(chat.id)) }
                    }
                    AssistChip(
                        onClick = { onEvent(FocusChatEvent.Add) },
                        label = { Text(stringResource(R.string.settings_focus_add_chats)) },
                        enabled = !state.isBusy,
                        leadingIcon = {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_add),
                                contentDescription = null,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** An allowed chat: its avatar and title, user content; a tap removes it. */
@Composable
private fun AllowedChatChip(
    chat: FocusChatChoice,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    val removeLabel = stringResource(R.string.settings_focus_remove_chat, chat.title)
    // Wider with the font scale, so a large font shows as much of the name.
    val maxWidth = MaterialTheme.sizes.mediaMinSize * LocalDensity.current.fontScale.coerceAtLeast(1f)
    InputChip(
        selected = true,
        onClick = onRemove,
        enabled = enabled,
        label = {
            Text(
                text = chat.title,
                modifier = Modifier.widthIn(max = maxWidth),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = LocalTextStyle.current.userContent,
            )
        },
        shapes = InputChipDefaults.shapes(),
        modifier =
            Modifier.semantics {
                contentDescription = removeLabel
                onClick(label = removeLabel) {
                    onRemove()
                    true
                }
            },
        avatar = { Avatar(photo = chat.photo, name = chat.title, id = chat.id, size = InputChipDefaults.AvatarSize) },
        trailingIcon = {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = null,
                modifier = Modifier.size(InputChipDefaults.IconSize),
            )
        },
    )
}

/**
 * The picker of chats to also allow: a `ModalBottomSheet` with its title, the search bar's field, the main list's
 * other chats as toggleable rows (a picked one `large` in `secondaryContainer`, its avatar turned into a check) and
 * "Done", which adds them, as dismissing does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FocusChatPicker(
    picker: FocusChatPickerUiState,
    onEvent: (FocusEditEvent) -> Unit,
) {
    PingogramBottomSheet(
        onDismissRequest = { onEvent(FocusChatEvent.Close) },
        sheetState =
            rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        FocusChatPickerContent(picker, onEvent)
    }
}

@Composable
internal fun FocusChatPickerContent(
    picker: FocusChatPickerUiState,
    onEvent: (FocusEditEvent) -> Unit,
) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item(key = TITLE) { PickerTitle(stringResource(R.string.settings_focus_chat_picker_title)) }
        item(key = SEARCH) {
            PickerSearchField(
                query = picker.query,
                placeholder = stringResource(R.string.settings_privacy_exceptions_search),
            ) { onEvent(FocusChatEvent.Query(it)) }
        }
        if (picker.candidates.isEmpty()) {
            item(key = NO_RESULTS) {
                Text(
                    text = stringResource(R.string.settings_chat_folder_picker_no_results),
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.large),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(picker.candidates, key = { it.chat.id }) { candidate ->
            PickerAvatarRow(
                id = candidate.chat.id,
                title = candidate.chat.title,
                photo = candidate.chat.photo,
                selected = candidate.selected,
            ) { onEvent(FocusChatEvent.Toggle(candidate.chat.id)) }
        }
        item(key = DONE) {
            PickerDone(stringResource(R.string.settings_chat_folder_picker_done)) { onEvent(FocusChatEvent.Close) }
        }
    }
}

private const val TITLE = "title"
private const val SEARCH = "search"
private const val NO_RESULTS = "noResults"
private const val DONE = "done"
