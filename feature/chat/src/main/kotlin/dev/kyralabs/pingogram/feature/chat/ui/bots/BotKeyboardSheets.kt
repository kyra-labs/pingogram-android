package dev.kyralabs.pingogram.feature.chat.ui.bots

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.formatPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotKeyboardEvent
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import dev.kyralabs.pingogram.feature.chat.ui.forward.NoTargets
import dev.kyralabs.pingogram.feature.chat.ui.forward.TargetList
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotKeyboardPreviewData

// What follows a tap on a bot's keyboard button that shares something (docs/DESIGN_SYSTEM.md §6 "Bot keyboard").

/**
 * The phone number question ([dialog]) of a keyboard button: an `AlertDialog` "Share your phone number with @bot?",
 * the number (formatted, left to right) and what goes with it, a `LinearWavyProgressIndicator` while it is sent,
 * "Cancel" and "Share". Nothing is sent before "Share" ([BotKeyboardEvent.SharePhone]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LinearWavyProgressIndicator
@Composable
internal fun BotKeyboardDialogHost(
    dialog: BotKeyboardDialog,
    onEvent: (BotKeyboardEvent) -> Unit,
) {
    when (dialog) {
        is BotKeyboardDialog.SharePhone -> {
            val bot = dialog.botName.resolve().bidiIsolated()
            // Kept on one line: no break between its groups of digits.
            val phone =
                formatPhoneNumber(dialog.phoneNumber, LocalConfiguration.current.locales[0].country)
                    .replace(' ', NO_BREAK_SPACE)
            AlertDialog(
                onDismissRequest = { onEvent(BotKeyboardEvent.DismissDialog) },
                confirmButton = {
                    TextButton(
                        onClick = { onEvent(BotKeyboardEvent.SharePhone) },
                        enabled = !dialog.isSharing,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.bot_phone_share))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { onEvent(BotKeyboardEvent.DismissDialog) },
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.bot_dialog_cancel))
                    }
                },
                title = { Text(stringResource(R.string.bot_phone_title, bot)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium)) {
                        Text(
                            text = stringResource(R.string.bot_phone_text, bot, phone.bidiIsolated()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (dialog.isSharing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
                    }
                },
            )
        }
    }
}

/**
 * The users or chat picker of a keyboard button ([sheet], `docs/DESIGN_SYSTEM.md` §6 "Bot keyboard"): a
 * [ModalBottomSheet] with the forward sheet's parts, "Choose up to 3 people" / "Choose a group" / "Choose a channel"
 * (`titleLargeEmphasized`), what the bot gets and what Telegram checks or does on sharing (`bodyMedium`
 * `onSurfaceVariant`), the search field and the candidates as toggleable rows. Once something is picked, "Share"
 * (a default `Button` with its press morph, a `Confirm` haptic) grows in below the list on the default spatial spring,
 * with a `LinearWavyProgressIndicator` while Telegram is asked and why the last try failed (in `error`, a polite live
 * region). Dragging it down, the scrim and the back gesture dismiss it ([BotKeyboardEvent.DismissShare]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KeyboardShareBottomSheet(
    sheet: KeyboardShareSheet,
    onEvent: (BotKeyboardEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    val query = rememberTextFieldState()
    LaunchedEffect(query) {
        snapshotFlow { query.text.toString() }.collect { currentOnEvent(BotKeyboardEvent.SearchTargets(it)) }
    }
    ModalBottomSheet(onDismissRequest = { currentOnEvent(BotKeyboardEvent.DismissShare) }, sheetState = sheetState) {
        KeyboardShareContent(sheet, query, onEvent)
    }
}

/** What the picker shows: the title and notes, the search field, the candidates, and "Share" once one is picked. */
@Composable
internal fun ColumnScope.KeyboardShareContent(
    sheet: KeyboardShareSheet,
    query: TextFieldState,
    onEvent: (BotKeyboardEvent) -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    ShareHeader(sheet)
    ChatSearchField(query)
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if (sheet.targets.isEmpty()) {
            NoTargets(
                isLoading = sheet.isLoading,
                isSearching = sheet.isSearching,
                error = sheet.searchError,
                noChats =
                    stringResource(
                        if (sheet.kind == ShareKind.Users) R.string.bot_share_no_users else R.string.bot_share_no_chats,
                    ),
            )
        } else {
            TargetList(
                targets = sheet.targets,
                selectedIds = sheet.selected,
                isSearching = sheet.isSearching,
                listState = listState,
                onToggle = { onEvent(BotKeyboardEvent.ToggleTarget(it)) },
                onLoadMore = { onEvent(BotKeyboardEvent.LoadMoreTargets) },
            )
        }
    }
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = sheet.selected.isNotEmpty(),
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        ShareFooter(sheet, onShare = { onEvent(BotKeyboardEvent.Share) })
    }
}

/** The title of what the button asks for, then the notes. */
@Composable
private fun ShareHeader(sheet: KeyboardShareSheet) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large).padding(bottom = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.extraSmall),
    ) {
        Text(
            text =
                when (sheet.kind) {
                    ShareKind.Users -> {
                        pluralStringResource(R.plurals.bot_share_users_title, sheet.maxCount, sheet.maxCount)
                    }

                    ShareKind.Group -> {
                        stringResource(R.string.bot_share_group_title)
                    }

                    ShareKind.Channel -> {
                        stringResource(R.string.bot_share_channel_title)
                    }
                },
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        sheet.notes.forEach { note ->
            Text(
                text = note.resolve(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Why the last try failed, the progress while Telegram is asked, and "Share" at the end. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class) // LinearWavyProgressIndicator
@Composable
private fun ShareFooter(
    sheet: KeyboardShareSheet,
    onShare: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        sheet.error?.let { error ->
            Text(
                text = errorText(error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (sheet.isSharing) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    onShare()
                },
                enabled = !sheet.isSharing,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.bot_share), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}

/** The space between a phone number's groups of digits, which lines do not break at. */
private const val NO_BREAK_SPACE = '\u00A0'

@PingogramPreviews
@Composable
private fun KeyboardShareUsersPreview() {
    PingogramPreview {
        Column { KeyboardShareContent(BotKeyboardPreviewData.usersSheet, remember { TextFieldState() }, onEvent = {}) }
    }
}

@PingogramPreviews
@Composable
private fun KeyboardShareGroupPreview() {
    PingogramPreview {
        Column { KeyboardShareContent(BotKeyboardPreviewData.groupSheet, remember { TextFieldState() }, onEvent = {}) }
    }
}

@PingogramPreviews
@Composable
private fun KeyboardShareEmptyPreview() {
    PingogramPreview {
        Column {
            KeyboardShareContent(BotKeyboardPreviewData.emptyGroupSheet, remember { TextFieldState() }, onEvent = {})
        }
    }
}

@PingogramPreviews
@Composable
private fun BotPhoneDialogPreview() {
    PingogramPreview { BotKeyboardDialogHost(BotKeyboardPreviewData.phoneDialog, onEvent = {}) }
}
