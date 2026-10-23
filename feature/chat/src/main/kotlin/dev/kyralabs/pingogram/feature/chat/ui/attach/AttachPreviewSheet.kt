package dev.kyralabs.pingogram.feature.chat.ui.attach

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.kyralabs.pingogram.core.domain.model.Attachment
import dev.kyralabs.pingogram.core.domain.model.AttachmentKind
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerField
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.LinkDialog
import dev.kyralabs.pingogram.feature.chat.ui.composer.ScheduleDialog
import dev.kyralabs.pingogram.feature.chat.ui.composer.SendButton
import dev.kyralabs.pingogram.feature.chat.ui.content.MediaLabel
import dev.kyralabs.pingogram.feature.chat.ui.content.PlayBadge
import dev.kyralabs.pingogram.feature.chat.ui.content.documentKind
import dev.kyralabs.pingogram.feature.chat.ui.content.fileSizeText
import dev.kyralabs.pingogram.feature.chat.ui.content.mediaDurationText
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The picked photos, videos or files before they are sent (`REQUIREMENTS.md` FR-C5): a [ModalBottomSheet] with their
 * number as its title, the items, and the caption field with the send split button below.
 *
 * - **Photos and videos** ([AttachmentKind.Media]): a row of square thumbnails, decoded by Coil at their size, in
 *   `medium` corners; a video with its length and a play badge; an item being prepared as a [LoadingIndicator] on a
 *   tonal square. Each has a remove button on a `mediaOverlay` circle.
 * - **Files** ([AttachmentKind.Document]): one row each, its kind's icon, its name (shortened in the middle, so the
 *   extension stays) and size, and a remove button; the list scrolls where the window is short.
 * - **Caption** ([caption], the view model's): the composer's text field, formatting included; Enter on a hardware
 *   keyboard sends. The send button waits until every item is ready; its menu sends without sound or at a time
 *   ([ScheduleDialog]) where the chat allows it.
 *
 * Sending confirms with a haptic, hides the sheet on its spring, then [onSend] gets the options. Removing the last
 * item, dragging the sheet down, the scrim and the back gesture (Material's predictive back) dismiss it
 * ([ChatEvent.DismissAttachments]), which drops what was picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachPreviewSheet(
    preview: AttachPreview,
    caption: ComposerText,
    onEvent: (ChatEvent) -> Unit,
    onSend: (SendOptions) -> Unit,
    now: () -> Instant = Instant::now,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentOnSend by rememberUpdatedState(onSend)
    val currentPreview by rememberUpdatedState(preview)
    val send: (SendOptions) -> Unit =
        remember(sheetState, scope, haptics) {
            { options ->
                if (currentPreview.isReady) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    scope.launch { sheetState.hide() }.invokeOnCompletion { currentOnSend(options) }
                }
            }
        }
    var scheduling by rememberSaveable { mutableStateOf(false) }
    var linking by remember { mutableStateOf<TextRange?>(null) }
    ModalBottomSheet(onDismissRequest = { onEvent(ChatEvent.DismissAttachments) }, sheetState = sheetState) {
        AttachPreviewContent(
            preview = preview,
            caption = caption,
            onRemove = { onEvent(ChatEvent.RemoveAttachment(it)) },
            onSend = send,
            onSchedule = { scheduling = true },
            onLink = { linking = it },
        )
    }
    linking?.let { selection ->
        LinkDialog(
            currentUrl = caption.linkAt(selection),
            onDismiss = { linking = null },
            onConfirm = { url ->
                caption.setLink(url, selection)
                linking = null
            },
        )
    }
    if (scheduling) {
        ScheduleDialog(
            now = now,
            onDismiss = { scheduling = false },
            onSchedule = { at ->
                scheduling = false
                send(SendOptions(scheduledAt = at.epochSecond))
            },
        )
    }
}

/** What the sheet shows: the title, the items and the caption row. */
@Suppress("LongParameterList") // The preview, its caption, and what the sheet does with them.
@Composable
internal fun ColumnScope.AttachPreviewContent(
    preview: AttachPreview,
    caption: ComposerText,
    onRemove: (key: Long) -> Unit,
    onSend: (SendOptions) -> Unit,
    onSchedule: () -> Unit,
    onLink: (TextRange) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Text(
        text = previewTitle(preview),
        modifier = Modifier.padding(horizontal = spacing.large).padding(bottom = spacing.medium),
        style = MaterialTheme.typography.titleLargeEmphasized,
    )
    val count = preview.items.size
    when (preview.kind) {
        AttachmentKind.Media -> {
            val motionScheme = MaterialTheme.motionScheme
            LazyRow(
                contentPadding = PaddingValues(horizontal = spacing.large),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                itemsIndexed(preview.items, key = { _, item -> item.key }) { index, item ->
                    MediaPreviewItem(
                        item = item,
                        position = index + 1,
                        count = count,
                        onRemove = { onRemove(item.key) },
                        modifier =
                            Modifier.animateItem(
                                fadeInSpec = motionScheme.defaultEffectsSpec(),
                                placementSpec = motionScheme.defaultSpatialSpec(),
                                fadeOutSpec = motionScheme.fastEffectsSpec(),
                            ),
                    )
                }
            }
        }

        AttachmentKind.Document -> {
            // Bounded by the sheet, so the caption row stays in view while many files scroll.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                preview.items.forEachIndexed { index, item ->
                    FilePreviewRow(item, position = index + 1, count = count, onRemove = { onRemove(item.key) })
                }
            }
        }
    }
    CaptionRow(preview, caption, onSend, onSchedule, onLink)
}

/** The caption field and the send split button, which waits until every item of [preview] is ready. */
@Composable
private fun CaptionRow(
    preview: AttachPreview,
    caption: ComposerText,
    onSend: (SendOptions) -> Unit,
    onSchedule: () -> Unit,
    onLink: (TextRange) -> Unit,
) {
    val spacing = MaterialTheme.spacing
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium, vertical = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.Bottom,
    ) {
        ComposerField(
            text = caption,
            onSend = { onSend(SendOptions()) },
            onLink = onLink,
            onFocusChanged = {},
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.attach_caption),
        )
        SendButton(
            canSchedule = preview.canSchedule,
            onSend = { onSend(SendOptions()) },
            onSendSilently = { onSend(SendOptions(silent = true)) },
            onSchedule = onSchedule,
            enabled = preview.isReady,
        )
    }
}

/** "3 photos", "2 videos", "4 items" (photos and videos, or not all ready yet), "2 files". */
@Composable
private fun previewTitle(preview: AttachPreview): String {
    val count = preview.items.size
    val attachments = preview.attachments
    val allReady = preview.isReady
    return when {
        preview.kind == AttachmentKind.Document -> {
            pluralStringResource(R.plurals.attach_preview_files, count, count)
        }

        allReady && attachments.all { it is Attachment.Photo } -> {
            pluralStringResource(R.plurals.attach_preview_photos, count, count)
        }

        allReady && attachments.all { it is Attachment.Video } -> {
            pluralStringResource(R.plurals.attach_preview_videos, count, count)
        }

        else -> {
            pluralStringResource(R.plurals.attach_preview_items, count, count)
        }
    }
}

/**
 * A file of the preview, [position] of [count]: its kind's icon on a `secondaryContainer` circle (a loading indicator
 * while it is prepared), its name and size, and its remove button.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FilePreviewRow(
    item: PreviewItem,
    position: Int,
    count: Int,
    onRemove: () -> Unit,
) {
    val spacing = MaterialTheme.spacing
    val document = (item as? PreviewItem.Ready)?.attachment
    val name =
        when (document) {
            is Attachment.Document -> document.fileName
            null -> stringResource(R.string.attach_item_preparing, position, count)
            else -> stringResource(UiR.string.preview_file)
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = spacing.large, end = spacing.small),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(MaterialTheme.sizes.contentIcon)
                    .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (document is Attachment.Document) {
                Icon(
                    painter = painterResource(documentKind(document.fileName, document.mimeType).icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                LoadingIndicator(Modifier.size(MaterialTheme.sizes.contentIcon))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.extraSmall)) {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
                style = MaterialTheme.typography.titleSmall.userContent,
            )
            document?.let {
                Text(
                    text = fileSizeText(it.size),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        IconButton(onClick = onRemove, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.attach_remove, name),
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun AttachPreviewMediaPreview() {
    PingogramPreview {
        Column(Modifier.padding(vertical = MaterialTheme.spacing.large)) {
            AttachPreviewContent(AttachPreviewData.media, remember { ComposerText() }, {}, {}, {}, {})
        }
    }
}

@PingogramPreviews
@Composable
private fun AttachPreviewFilesPreview() {
    PingogramPreview {
        Column(Modifier.padding(vertical = MaterialTheme.spacing.large)) {
            AttachPreviewContent(AttachPreviewData.files, remember { ComposerText() }, {}, {}, {}, {})
        }
    }
}
