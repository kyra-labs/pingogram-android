package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets

/**
 * The commands of the chat's bots (`REQUIREMENTS.md` FR-C5, `docs/DESIGN_SYSTEM.md` §6 "Bot commands sheet"), a
 * [ModalBottomSheet] opened from the composer's "/" button, as the conversation's other sheets: "Commands" in
 * `titleLargeEmphasized`, then, with more than [SEARCH_THRESHOLD] of them, the search bar's input field, which filters
 * them by name and description as the user types ([searching]); then the commands as list items inset like the chat
 * list's rows (`docs/DESIGN_SYSTEM.md` §4): the command over what it does (two lines at most), in a group its bot's
 * avatar at the start, and the insert button at the end.
 *
 * A tap sends the command at once ([BotEvent.SendCommand]), as Telegram's apps do, since most commands take no
 * arguments; the insert button puts it into the field instead, with a space after it, for its arguments
 * ([BotEvent.InsertCommand]). Either closes the sheet. Dragging it down, the scrim and back close it too
 * ([BotEvent.DismissCommands]). The list scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BotCommandsBottomSheet(
    sheet: BotCommandsSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(BotEvent.DismissCommands) }) {
        BotCommandsContent(sheet, currentOnEvent)
    }
}

/** The title, the search field where there are many commands, and the commands found. */
@Composable
internal fun BotCommandsContent(
    sheet: BotCommandsSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val query = rememberTextFieldState()
    val commands by remember(sheet.commands) { derivedStateOf { sheet.commands.searching(query.text.toString()) } }
    Column(Modifier.padding(bottom = spacing.large)) {
        Text(
            text = stringResource(R.string.bot_commands_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier =
                Modifier
                    .padding(horizontal = spacing.large, vertical = spacing.small)
                    .semantics { heading() },
        )
        if (sheet.commands.size > SEARCH_THRESHOLD) {
            ChatSearchField(query, placeholder = stringResource(R.string.bot_commands_search))
        }
        if (commands.isEmpty()) {
            Text(
                text = stringResource(R.string.bot_commands_none_found),
                modifier = Modifier.fillMaxWidth().padding(spacing.extraLarge),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            LazyColumn(Modifier.testTag(COMMANDS_TEST_TAG)) {
                items(commands, key = { it.text }, contentType = { CONTENT_TYPE_COMMAND }) { item ->
                    BotCommandRow(
                        item = item,
                        onSend = { onEvent(BotEvent.SendCommand(item.text)) },
                        onInsert = { onEvent(BotEvent.InsertCommand(item.text)) },
                    )
                }
            }
        }
    }
}

/**
 * One command in the sheet, a list item on the sheet's color inset from its sides: the command ([CommandText]), what
 * it does, in a group its bot's avatar, and the insert button; one item for screen readers ([commandSemantics]).
 */
@Composable
private fun BotCommandRow(
    item: BotCommandItem,
    onSend: () -> Unit,
    onInsert: () -> Unit,
) {
    val insets = sheetRowInsets()
    val sheetColor = MaterialTheme.colorScheme.surfaceContainerLow
    ListItem(
        onClick = onSend,
        modifier = Modifier.padding(insets).commandSemantics(item, onInsert),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = item.bot?.let { bot -> { CommandBotAvatar(bot, sheetColor) } },
        trailingContent = { InsertCommandButton(onInsert) },
        supportingContent =
            if (item.description.isNotBlank()) {
                { CommandDescription(item, maxLines = 2) }
            } else {
                null
            },
    ) {
        CommandText(item)
    }
}

/** More commands than this get a search field. */
private const val SEARCH_THRESHOLD = 8

private const val CONTENT_TYPE_COMMAND = "command"

/** The sheet's commands, for UI tests. */
internal const val COMMANDS_TEST_TAG = "bot_commands"

@PingogramPreviews
@Composable
private fun BotCommandsSheetPreview() {
    PingogramPreview { BotCommandsContent(BotPreviewData.sheet, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun BotCommandsSheetGroupPreview() {
    PingogramPreview { BotCommandsContent(BotPreviewData.groupSheet, onEvent = {}) }
}
