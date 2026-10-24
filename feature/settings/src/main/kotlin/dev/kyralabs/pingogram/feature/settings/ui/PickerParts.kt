package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.flow.drop
import dev.kyralabs.pingogram.core.ui.R as UiR

// The parts of the sheets that pick chats or people (a folder's chats, a privacy setting's exceptions): a title, the
// search bar's field, headers, toggleable rows with avatars, and "Done".

/** A picker's title in `titleLargeEmphasized`, a heading for screen readers. */
@Composable
internal fun PickerTitle(text: String) {
    Text(
        text = text,
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large)
                .padding(bottom = MaterialTheme.spacing.medium)
                .semantics { heading() },
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
}

/** "Done" ([label]) at the end, which closes the picker with what was picked. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PickerDone(
    label: String,
    onDone: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.spacing.large),
        horizontalArrangement = Arrangement.End,
    ) {
        Button(onClick = onDone, shapes = ButtonDefaults.shapes()) {
            Text(label, style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/** The search bar's input field on its container, with [placeholder] and a clear button while there is text. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PickerSearchField(
    query: String,
    placeholder: String,
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
            placeholder = { Text(placeholder) },
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

/** A header of a picker's rows in `titleSmallEmphasized` `primary`, a heading for screen readers. */
@Composable
internal fun PickerHeader(text: String) {
    Text(
        text = text,
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                .semantics { heading() },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.titleSmallEmphasized,
    )
}

/**
 * A chat or person to pick: the avatar of [id] (a check while [selected]), [title] as user content and [supporting]
 * text below it if any; the whole row toggles it.
 */
@Composable
internal fun PickerAvatarRow(
    id: Long,
    title: String,
    photo: ProfilePhoto?,
    selected: Boolean,
    supporting: String? = null,
    onToggle: () -> Unit,
) {
    ListItem(
        modifier = Modifier.pickerRow(MaterialTheme.spacing.small, pickerRowLook(selected), selected, onToggle),
        leadingContent = {
            Avatar(
                photo = photo,
                name = title,
                id = id,
                size = MaterialTheme.sizes.avatarRow,
                selected = selected,
                onSelection = selected,
            )
        },
        supportingContent = supporting?.let { { Text(it) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    ) {
        Text(title, style = LocalTextStyle.current.userContent, maxLines = 1)
    }
}

/** The look of a picker row: `large` in `secondaryContainer` while [selected]. */
@Composable
internal fun pickerRowLook(selected: Boolean): Pair<Shape, Color> =
    if (selected) {
        MaterialTheme.shapes.large to MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.shapes.extraSmall to Color.Transparent
    }

/** A row inset by [inset] from the sheet's sides, in [look], that toggles as a checkbox. */
internal fun Modifier.pickerRow(
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
