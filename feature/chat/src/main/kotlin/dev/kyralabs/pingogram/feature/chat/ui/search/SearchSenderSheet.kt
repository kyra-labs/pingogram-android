package dev.kyralabs.pingogram.feature.chat.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ChatSearchEvent
import dev.kyralabs.pingogram.feature.chat.ui.forward.ChatSearchField
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatSearchPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import kotlinx.collections.immutable.ImmutableList

/**
 * The members whose messages search finds (`docs/DESIGN_SYSTEM.md` §6, "In-chat search"), a [ModalBottomSheet] from
 * the search bar's sender button, as the conversation's other sheets: "Search messages from" in
 * `titleLargeEmphasized`, the search bar's input field ([query], which the view model searches the members with),
 * then the members as a single choice of list items inset like the chat list's rows, the picked one in the list item's
 * selected container. While they load, the delayed loading indicator; none found, or a failure with "Try again", in
 * `bodyMedium` `onSurfaceVariant`; each crossfades on the motion scheme's effects springs as the sheet resizes on its
 * default spatial spring. A tap picks the member and closes the sheet ([ChatSearchEvent.PickSender]); dragging it down,
 * the scrim and back close it ([ChatSearchEvent.DismissSenders]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchSenderSheet(
    sheet: SenderSheet,
    query: TextFieldState,
    onEvent: (ChatEvent) -> Unit,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(ChatSearchEvent.DismissSenders) }) {
        SearchSenderContent(sheet, query, currentOnEvent)
    }
}

/** The title, the search field, and the members found or why there are none. */
@Composable
internal fun SearchSenderContent(
    sheet: SenderSheet,
    query: TextFieldState,
    onEvent: (ChatEvent) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val motion = MaterialTheme.motionScheme
    Column(Modifier.padding(bottom = spacing.large)) {
        Text(
            text = stringResource(R.string.search_sender_title),
            style = MaterialTheme.typography.titleLargeEmphasized,
            modifier =
                Modifier
                    .padding(horizontal = spacing.large, vertical = spacing.small)
                    .semantics { heading() },
        )
        ChatSearchField(query, placeholder = stringResource(R.string.search_sender_search))
        AnimatedContent(
            targetState = sheet,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            contentKey = { it.kind },
            label = "searchSenders",
        ) { shown ->
            val options = shown.options
            when {
                options == null -> {
                    Box(Modifier.fillMaxWidth().padding(spacing.extraLarge), contentAlignment = Alignment.Center) {
                        DelayedLoadingIndicator()
                    }
                }

                shown.error != null -> {
                    SendersFailed(onRetry = { onEvent(ChatSearchEvent.RetrySenders) })
                }

                options.isEmpty() -> {
                    SheetNote(stringResource(R.string.search_sender_none))
                }

                else -> {
                    SenderList(options, onPick = { onEvent(ChatSearchEvent.PickSender(it.sender)) })
                }
            }
        }
    }
}

@Composable
private fun SenderList(
    options: ImmutableList<SenderOption>,
    onPick: (SenderOption) -> Unit,
) {
    LazyColumn(Modifier.selectableGroup().testTag(SENDERS_TEST_TAG)) {
        items(options, key = { it.id }, contentType = { CONTENT_TYPE_SENDER }) { option ->
            SenderRow(option, onClick = { onPick(option) })
        }
    }
}

/**
 * One member: their avatar and name, "Owner" or "Admin" below it, in a list item on the sheet's color inset like the
 * chat list's rows; the picked one in the list item's selected container (`secondaryContainer`, `large`), its
 * placeholder avatar in `primary`.
 */
@Composable
private fun SenderRow(
    option: SenderOption,
    onClick: () -> Unit,
) {
    val name = option.name.resolve()
    val insets = sheetRowInsets()
    ListItem(
        selected = option.selected,
        onClick = onClick,
        modifier = Modifier.padding(insets),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = option.photo,
                name = name,
                id = option.id,
                size = MaterialTheme.sizes.avatarSmall,
                placeholderIcon = option.placeholderIcon,
                onSelection = option.selected,
            )
        },
        supportingContent = option.subtitle?.let { subtitle -> { Text(subtitle.resolve()) } },
    ) {
        Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = LocalTextStyle.current.userContent)
    }
}

/** That the members could not be listed, and "Try again". */
@Composable
private fun SendersFailed(onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        SheetNote(stringResource(R.string.search_sender_error))
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.search_retry)) }
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/** What the sheet shows, for its crossfade: loading, a failure, or the list (which changes in place). */
private val SenderSheet.kind: Int
    get() =
        when {
            options == null -> KIND_LOADING
            error != null -> KIND_FAILED
            options.isEmpty() -> KIND_NONE
            else -> KIND_LIST
        }

private const val KIND_LOADING = 0
private const val KIND_FAILED = 1
private const val KIND_NONE = 2
private const val KIND_LIST = 3

private const val CONTENT_TYPE_SENDER = "sender"

/** The sheet's members, for UI tests. */
internal const val SENDERS_TEST_TAG = "search_senders"

@PingogramPreviews
@Composable
private fun SearchSenderSheetPreview() {
    PingogramPreview { SearchSenderContent(ChatSearchPreviewData.senders, TextFieldState(), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SearchSenderSheetLoadingPreview() {
    PingogramPreview { SearchSenderContent(SenderSheet(options = null), TextFieldState(), onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SearchSenderSheetFailedPreview() {
    PingogramPreview {
        SearchSenderContent(ChatSearchPreviewData.sendersFailed, TextFieldState("ada"), onEvent = {})
    }
}
