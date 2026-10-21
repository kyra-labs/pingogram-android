package dev.kyralabs.pingogram.feature.chat.ui.secret

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.SecretChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.preview.SecretChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets

/**
 * The sheets of a secret chat's top bar (`REQUIREMENTS.md` FR-E1), a [ModalBottomSheet] each: the self-destruct timer
 * ([TimerContent]) and the encryption key ([EncryptionKeyContent]), with the confirmation before the chat ends
 * ([EndSecretChatDialog]). Dragging a sheet down, tapping the scrim and the back gesture (Material's predictive back)
 * close it ([SecretChatEvent.Dismiss]); its content scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SecretChatBottomSheet(
    sheet: SecretChatSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(SecretChatEvent.Dismiss) }, sheetState = sheetState) {
        when (sheet) {
            is SecretChatSheet.Timer -> TimerContent(sheet, currentOnEvent)
            is SecretChatSheet.Key -> EncryptionKeyContent(sheet, currentOnEvent)
        }
    }
    if (sheet is SecretChatSheet.Key && sheet.confirmingEnd) EndSecretChatDialog(currentOnEvent)
}

/**
 * "Self-destruct timer" in `titleLargeEmphasized` and what it does in `bodyMedium` `onSurfaceVariant`, then the timers
 * as a single choice of list items inset like the chat list's rows, the chosen one in the list item's selected
 * container (`secondaryContainer`, `large`, `docs/DESIGN_SYSTEM.md` §4). While a new timer goes to TDLib
 * ([SecretChatSheet.Timer.isChanging]) it shows chosen with a wavy progress indicator under the title, as the send-as
 * picker does; the view model closes the sheet once it is set.
 */
@Composable
private fun TimerContent(
    sheet: SecretChatSheet.Timer,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val resources = LocalResources.current
    Column(Modifier.padding(bottom = spacing.large)) {
        SheetTitle(stringResource(R.string.secret_timer_title), Modifier.padding(horizontal = spacing.large))
        Text(
            text = stringResource(R.string.secret_timer_body),
            modifier = Modifier.padding(horizontal = spacing.large).padding(bottom = spacing.small),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (sheet.isChanging) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
            )
        }
        LazyColumn(Modifier.selectableGroup()) {
            items(sheet.options, key = { it.seconds }, contentType = { CONTENT_TYPE_TIMER }) { option ->
                val insets = sheetRowInsets()
                ListItem(
                    selected = option.selected,
                    onClick = { onEvent(SecretChatEvent.ChooseTimer(option.seconds)) },
                    modifier = Modifier.padding(insets),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    contentPadding = insetContentPadding(insets),
                ) {
                    Text(selfDestructText(resources, option.seconds))
                }
            }
        }
    }
}

/**
 * "Encryption key" in `titleLargeEmphasized`, the key's image in its frame ([EncryptionKeyImage]) and its first 32
 * bytes in hexadecimal ([KeyFingerprint]), then what to compare with the other person's screen in `bodyMedium`
 * `onSurfaceVariant`; while the other user's app has not joined, only that it will show then. Last, while the chat
 * has not ended, "End secret chat" as a `TextButton` in `error`, which asks first.
 */
@Composable
private fun EncryptionKeyContent(
    sheet: SecretChatSheet.Key,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val name = sheet.name.bidiIsolated().resolve()
    Column(
        modifier =
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        SheetTitle(stringResource(R.string.secret_key_title))
        val keyHash = sheet.keyHash
        if (!keyHash.isEmpty) {
            EncryptionKeyImage(keyHash, Modifier.align(Alignment.CenterHorizontally))
            KeyFingerprint(keyHash, Modifier.align(Alignment.CenterHorizontally))
        }
        Text(
            text =
                stringResource(
                    if (keyHash.isEmpty) R.string.secret_key_pending else R.string.secret_key_compare,
                    name,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        if (sheet.canEnd) {
            TextButton(
                onClick = { onEvent(SecretChatEvent.EndChat) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.secret_end))
            }
        }
    }
}

/** The confirmation before a secret chat ends for both sides: an [AlertDialog], as for every destructive action. */
@Composable
private fun EndSecretChatDialog(onEvent: (ChatEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(SecretChatEvent.DismissEnd) },
        title = { Text(stringResource(R.string.secret_end_title)) },
        text = { Text(stringResource(R.string.secret_end_body)) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(SecretChatEvent.ConfirmEnd) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.secret_end_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SecretChatEvent.DismissEnd) }, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.secret_end_cancel))
            }
        },
    )
}

/** A sheet's title, a heading in `titleLargeEmphasized`. */
@Composable
private fun SheetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier = modifier.padding(vertical = MaterialTheme.spacing.small).semantics { heading() },
    )
}

private const val CONTENT_TYPE_TIMER = "timer"

@PingogramPreviews
@Composable
private fun TimerSheetPreview() {
    PingogramPreview { TimerContent(SecretChatPreviewData.timer, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun TimerSheetChangingPreview() {
    PingogramPreview { TimerContent(SecretChatPreviewData.timerChanging, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun EncryptionKeySheetPreview() {
    PingogramPreview { EncryptionKeyContent(SecretChatPreviewData.key, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun EncryptionKeySheetPendingPreview() {
    PingogramPreview { EncryptionKeyContent(SecretChatPreviewData.keyPending, onEvent = {}) }
}
