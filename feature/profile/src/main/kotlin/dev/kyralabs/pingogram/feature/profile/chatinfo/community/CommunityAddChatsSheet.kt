package dev.kyralabs.pingogram.feature.profile.chatinfo.community

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.CheckSwitch
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.profile.R
import dev.kyralabs.pingogram.feature.profile.chatinfo.SavingIndicator
import java.text.NumberFormat

/**
 * The sheet that adds chats to the community, or suggests them to its administrators ([isDirect] false), a
 * `ModalBottomSheet` that opens expanded: [AddChatsContent]. Back, dragging it away and the scrim close it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CommunityAddChatsSheet(
    sheet: AddChatsSheet,
    isDirect: Boolean,
    onEvent: (CommunityEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(CommunityEvent.DismissAddChats) }, sheetState = sheetState) {
        AddChatsContent(sheet, isDirect, onEvent)
    }
}

/**
 * What the sheet shows, scrolling where it does not fit:
 * - "Add chats" ("Suggest a chat" for members) in `titleLargeEmphasized`, a heading, over what adding does in
 *   `bodyMedium` `onSurfaceVariant`;
 * - the user's groups and channels that may be added, as one segmented group of rows that pick a chat each (its avatar
 *   in its kind's shape, turned into a check once picked, its title as user content and what it is), or a
 *   [DelayedLoadingIndicator] while they load, why they could not be read with "Try again", or that there are none;
 * - "Visible to admins only" as a switch row (Material's `ListItem` with [CheckSwitch]);
 * - a [LinearWavyProgressIndicator] while the chats go, then [TextButton] "Cancel" and the default [Button] "Add"
 *   ("Suggest"), disabled until a chat is picked.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AddChatsContent(
    sheet: AddChatsSheet,
    isDirect: Boolean,
    onEvent: (CommunityEvent) -> Unit,
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
        Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
            Text(
                text = stringResource(if (isDirect) R.string.community_add_title else R.string.community_suggest_title),
                modifier = Modifier.semantics { heading() },
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            Text(
                text = stringResource(if (isDirect) R.string.community_add_text else R.string.community_suggest_text),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        AddChatsList(sheet, onEvent)
        HiddenRow(sheet, onEvent)
        SavingIndicator(sheet.isSending)
        AddChatsButtons(sheet, isDirect, onEvent)
    }
}

/** The chats to pick, or why there are none to show yet. */
@Composable
private fun AddChatsList(
    sheet: AddChatsSheet,
    onEvent: (CommunityEvent) -> Unit,
) {
    val chats = sheet.chats
    val error = sheet.error
    when {
        error != null -> {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small)) {
                Text(
                    text = errorText(error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = { onEvent(CommunityEvent.RetryAddChats) }, shapes = ButtonDefaults.shapes()) {
                    Text(stringResource(R.string.profile_retry))
                }
            }
        }

        chats == null -> {
            val description = stringResource(R.string.community_add_loading)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
            }
        }

        chats.isEmpty() -> {
            Text(
                text = stringResource(R.string.community_add_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }

        else -> {
            val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
            Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                chats.forEachIndexed { index, chat ->
                    PickableChat(
                        chat = chat,
                        kind = chat.kindText(numbers),
                        sheet = sheet,
                        shapes = ListItemDefaults.segmentedShapes(index, chats.size),
                        onEvent = onEvent,
                    )
                }
            }
        }
    }
}

/** One chat to pick: picked, it takes the selected colors and shape and its avatar turns into a check. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PickableChat(
    chat: CommunityChatItem,
    kind: String,
    sheet: AddChatsSheet,
    shapes: ListItemShapes,
    onEvent: (CommunityEvent) -> Unit,
) {
    val isPicked = chat.chatId in sheet.picked
    SegmentedListItem(
        selected = isPicked,
        onClick = { onEvent(CommunityEvent.PickChat(chat.chatId)) },
        enabled = !sheet.isSending,
        shapes = shapes,
        colors = ListItemDefaults.segmentedColors(),
        leadingContent = {
            Avatar(
                photo = chat.photo,
                name = chat.title,
                id = chat.chatId,
                size = MaterialTheme.sizes.avatarSmall,
                shape = chat.shape,
                selected = isPicked,
                onSelection = isPicked,
            )
        },
        supportingContent = { Text(kind, maxLines = 1, overflow = TextOverflow.Ellipsis) },
    ) {
        Text(
            text = chat.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyLarge.userContent,
        )
    }
}

/** "Visible to admins only": a switch row; the whole row toggles it. */
@Composable
private fun HiddenRow(
    sheet: AddChatsSheet,
    onEvent: (CommunityEvent) -> Unit,
) {
    ListItem(
        modifier =
            Modifier.toggleable(
                value = sheet.isHidden,
                enabled = !sheet.isSending,
                role = Role.Switch,
                onValueChange = { onEvent(CommunityEvent.HiddenChanged(it)) },
            ),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = { Text(stringResource(R.string.community_add_hidden)) },
        supportingContent = { Text(stringResource(R.string.community_add_hidden_text)) },
        trailingContent = { CheckSwitch(sheet.isHidden, enabled = !sheet.isSending) },
    )
}

/** "Cancel" at the start and "Add" ("Suggest") at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AddChatsButtons(
    sheet: AddChatsSheet,
    isDirect: Boolean,
    onEvent: (CommunityEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().widthIn(max = MaterialTheme.sizes.contentMaxWidth),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = { onEvent(CommunityEvent.DismissAddChats) },
            enabled = !sheet.isSending,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(stringResource(R.string.community_cancel))
        }
        Box(Modifier.weight(1f))
        Button(
            onClick = { onEvent(CommunityEvent.SendChats) },
            enabled = sheet.picked.isNotEmpty() && !sheet.isSending,
            shapes = ButtonDefaults.shapes(),
        ) {
            Text(
                text = stringResource(if (isDirect) R.string.community_add_send else R.string.community_add_suggest),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}
