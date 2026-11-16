package dev.kyralabs.pingogram.widget

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.navigation.WidgetReplyRoute
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.NoDialogWindowDim
import dev.kyralabs.pingogram.core.ui.component.PingogramBottomSheet
import dev.kyralabs.pingogram.core.ui.component.SheetDialogProperties
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The reply sheet a Conversation widget's Reply opens ([WidgetReplyRoute]), a dialog destination whose own window dims
 * nothing. [onClose] once it closes; once the reply is sent, the app goes to the background too, back to the home
 * screen it came from.
 */
internal fun NavGraphBuilder.widgetReply(onClose: () -> Unit) {
    dialog<WidgetReplyRoute>(dialogProperties = SheetDialogProperties) {
        NoDialogWindowDim()
        val viewModel: WidgetReplyViewModel = hiltViewModel()
        val state by viewModel.state.collectAsStateWithLifecycle()
        val currentOnClose by rememberUpdatedState(onClose)
        val activity = LocalActivity.current
        val haptics = LocalHapticFeedback.current
        LaunchedEffect(viewModel) {
            viewModel.sent.collect {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                currentOnClose()
                activity?.moveTaskToBack(true)
            }
        }
        WidgetReplySheet(state, rememberTextFieldState(), viewModel::send, onClose)
    }
}

/**
 * The small reply sheet (board 33, owner decision 2026-11-15): a `ModalBottomSheet` that opens expanded, at most
 * `contentMaxWidth`: the chat's avatar (in its kind's shape, §13.2) beside "Reply to" in `labelLarge` `primary` over
 * the chat's title in `titleLargeEmphasized` (one heading); the rounded tonal field ("Message", up to five lines, user
 * content), which takes the focus so the keyboard comes up, beside a `FilledIconButton` with the send symbol (a
 * `LoadingIndicator` in its place while Telegram takes the message), disabled while the field is blank; why the message
 * was not sent in `error` below (a polite live region), the text kept.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WidgetReplySheet(
    state: WidgetReplyState,
    text: TextFieldState,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    PingogramBottomSheet(
        onDismissRequest = onDismiss,
        sheetState =
            rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        WidgetReplyContent(state, text, onSend)
    }
}

/**
 * What the reply sheet holds: the header, the field with its send button, and why the last reply failed. The field
 * takes the focus as it first shows, unless [requestFocus] is false (goldens, without a blinking cursor).
 */
@Composable
internal fun WidgetReplyContent(
    state: WidgetReplyState,
    text: TextFieldState,
    onSend: (String) -> Unit,
    requestFocus: Boolean = true,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        ReplyHeader(state)
        ReplyField(state, text, onSend, requestFocus)
        state.error?.let { error ->
            Text(
                text = errorText(error),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The chat's avatar beside "Reply to" over its title: one heading for screen readers. */
@Composable
private fun ReplyHeader(state: WidgetReplyState) {
    val title = state.titleRes?.let { stringResource(it) } ?: state.title
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = state.photo,
            name = title,
            id = state.chatId,
            size = MaterialTheme.sizes.avatarSmall,
            placeholderIcon = state.placeholderIcon,
            shape = state.shape,
        )
        Column {
            Text(
                text = stringResource(R.string.widget_reply_to),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLargeEmphasized.userContent,
            )
        }
    }
}

/** The field, with the focus from the start, and the send button. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReplyField(
    state: WidgetReplyState,
    text: TextFieldState,
    onSend: (String) -> Unit,
    requestFocus: Boolean,
) {
    val focus = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(focus) { focus.requestFocus() }
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        TextField(
            state = text,
            modifier = Modifier.weight(1f).focusRequester(focus),
            placeholder = { Text(stringResource(R.string.widget_reply_placeholder)) },
            textStyle = MaterialTheme.typography.bodyLarge.userContent,
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = FIELD_LINES),
            shape = TextFieldDefaults.roundedShape,
            colors = TextFieldDefaults.tonalColors(),
        )
        FilledIconButton(
            onClick = { onSend(text.text.toString()) },
            enabled = !state.isSending && text.text.isNotBlank(),
            modifier = Modifier.padding(bottom = MaterialTheme.spacing.small),
            shapes = IconButtonDefaults.shapes(),
        ) {
            if (state.isSending) {
                LoadingIndicator()
            } else {
                Icon(
                    painter = painterResource(UiR.drawable.ic_send_filled),
                    contentDescription = stringResource(R.string.widget_reply_send),
                )
            }
        }
    }
}

private const val FIELD_LINES = 5

@Preview
@Composable
private fun WidgetReplySheetPreview() {
    PingogramPreview {
        WidgetReplySheet(
            WidgetReplyState(chatId = 1, title = "Trip — Coorg 2026", shape = AvatarShape.Group),
            rememberTextFieldState("Booked it!"),
            {},
            {},
        )
    }
}

@Preview
@Composable
private fun WidgetReplySheetErrorPreview() {
    PingogramPreview {
        WidgetReplySheet(
            WidgetReplyState(chatId = 1, title = "Meera", error = DomainError.Network),
            rememberTextFieldState("On my way"),
            {},
            {},
        )
    }
}
