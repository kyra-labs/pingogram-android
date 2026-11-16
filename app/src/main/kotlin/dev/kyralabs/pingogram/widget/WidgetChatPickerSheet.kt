package dev.kyralabs.pingogram.widget

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSearchBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.navigation.WidgetChatPickerRoute
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.drop
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Conversation widget's chat picker ([WidgetChatPickerRoute]), a dialog destination whose own window dims nothing,
 * so the screen below stays as it is. [onClose] once it closes; once a chat is kept in the widget, the app goes to the
 * background too, so the home screen shows the widget with it.
 */
internal fun NavGraphBuilder.widgetChatPicker(onClose: () -> Unit) {
    dialog<WidgetChatPickerRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        val viewModel: WidgetChatPickerViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val currentOnClose by rememberUpdatedState(onClose)
        val activity = LocalActivity.current
        LaunchedEffect(viewModel) {
            viewModel.closed.collect {
                currentOnClose()
                activity?.moveTaskToBack(true)
            }
        }
        WidgetChatPickerSheet(state, viewModel::onQueryChange, viewModel::onPick, onClose)
    }
}

/**
 * A `ModalBottomSheet` that opens expanded: "Choose a chat for the widget" in `titleLargeEmphasized` (a heading), the
 * search bar's field on its container, then the chats as `ListItem`s inset like the chat list's rows, each its
 * avatar in its kind's shape (§13.2) and its title (user content); a tap keeps it in the widget. A
 * `DelayedLoadingIndicator` while the chats load, "No chats found" when none match.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetChatPickerSheet(
    state: WidgetChatPickerState,
    onQueryChange: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    PingogramBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(
                SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
    ) {
        Text(
            text = stringResource(R.string.widget_conversation_picker_title),
            modifier = Modifier.padding(horizontal = spacing.large).semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        PickerSearchField(state.query, onQueryChange)
        val chats = state.chats
        when {
            chats == null -> {
                Box(Modifier.fillMaxWidth().heightIn(min = MaterialTheme.sizes.avatarRow), Alignment.Center) {
                    DelayedLoadingIndicator()
                }
            }

            chats.isEmpty() -> {
                Text(
                    text = stringResource(R.string.widget_conversation_picker_none),
                    modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.medium),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            else -> {
                LazyColumn(contentPadding = PaddingValues(bottom = spacing.large)) {
                    items(chats, key = { it.id }) { chat -> PickerRow(chat, onPick) }
                }
            }
        }
    }
}

/** One chat: its avatar and title; a tap keeps it in the widget. */
@Composable
private fun PickerRow(
    chat: WidgetPickerChat,
    onPick: (Long) -> Unit,
) {
    val title = chat.titleRes?.let { stringResource(it) } ?: chat.title
    ListItem(
        onClick = { onPick(chat.id) },
        modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Avatar(
                photo = chat.photo,
                name = title,
                id = chat.id,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = chat.placeholderIcon,
                shape = chat.shape,
            )
        },
    ) {
        Text(
            text = title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

/** The search bar's input field on its container, with a clear button while it holds text. */
@Composable
private fun PickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val text = rememberTextFieldState(query)
    val currentOnQueryChange by rememberUpdatedState(onQueryChange)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.drop(1).collect { currentOnQueryChange(it) } }
    val keyboard = LocalSoftwareKeyboardController.current
    val spacing = MaterialTheme.spacing
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.medium),
        shape = SearchBarDefaults.inputFieldShape,
        color = SearchBarDefaults.colors().containerColor,
    ) {
        SearchBarDefaults.InputField(
            textFieldState = text,
            searchBarState = rememberSearchBarState(),
            onSearch = { keyboard?.hide() },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.widget_conversation_picker_search)) },
            leadingIcon = { Icon(painterResource(UiR.drawable.ic_search), contentDescription = null) },
            trailingIcon =
                if (text.text.isNotEmpty()) {
                    {
                        IconButton(onClick = { text.clearText() }, shapes = IconButtonDefaults.shapes()) {
                            Icon(
                                painter = painterResource(UiR.drawable.ic_close),
                                contentDescription = stringResource(R.string.widget_conversation_picker_clear),
                            )
                        }
                    }
                } else {
                    null
                },
            keyboardOptions =
                KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Search),
        )
    }
}

/** The picker's preview data: the board's chats. */
internal val WidgetPickerPreviewChats =
    persistentListOf(
        WidgetPickerChat(1, "Trip — Coorg 2026", null, AvatarShape.Group),
        WidgetPickerChat(2, "Meera Iyer", null, AvatarShape.Circle),
        WidgetPickerChat(3, "Pingogram Beta", null, AvatarShape.Channel),
        WidgetPickerChat(4, "Me", null, AvatarShape.SavedMessages, UiR.drawable.ic_bookmark_filled),
    )

@Preview
@Composable
private fun WidgetChatPickerSheetPreview() {
    PingogramPreview {
        WidgetChatPickerSheet(WidgetChatPickerState(WidgetPickerPreviewChats), {}, {}, {})
    }
}
