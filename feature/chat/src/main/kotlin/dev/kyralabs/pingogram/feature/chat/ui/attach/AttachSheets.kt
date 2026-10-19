package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.toImmutableList

/**
 * The attach menu's sheets over the conversation: the menu ([AttachMenuSheet]) while [menuOpen] and the chat takes
 * attachments, closing through [onCloseMenu]; the preview of picked items ([AttachPreviewSheet]) and the poll form
 * ([PollFormSheet]) while [state] has them. [onSent] gets the options of what they send.
 */
@Composable
internal fun AttachSheets(
    state: ChatUiState,
    inputs: AttachInputs,
    menuOpen: Boolean,
    onCloseMenu: () -> Unit,
    onEvent: (ChatEvent) -> Unit,
    onSent: (SendOptions) -> Unit = {},
) {
    val options = (state.composer as? ComposerUiState.Available)?.attachOptions.orEmpty()
    if (menuOpen && options.isNotEmpty()) {
        AttachMenuSheet(
            options = options.filter { inputs.hasCamera || it != AttachOption.Camera }.toImmutableList(),
            onPick = { option ->
                onCloseMenu()
                inputs.onPick(option)
            },
            onDismiss = onCloseMenu,
        )
    }
    state.attachPreview?.let { preview ->
        AttachPreviewSheet(
            preview = preview,
            caption = inputs.caption,
            onEvent = onEvent,
            onSend = { sendOptions ->
                onEvent(ChatEvent.SendAttachments(sendOptions))
                onSent(sendOptions)
            },
        )
    }
    state.poll?.let { sheet ->
        PollFormSheet(
            sheet = sheet,
            editor = inputs.pollEditor,
            onEvent = { event ->
                onEvent(event)
                if (event is ChatEvent.SendPoll) onSent(event.options)
            },
        )
    }
}
