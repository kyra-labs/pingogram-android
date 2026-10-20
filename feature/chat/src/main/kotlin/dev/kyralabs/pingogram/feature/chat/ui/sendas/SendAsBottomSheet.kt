package dev.kyralabs.pingogram.feature.chat.ui.sendas

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.SendAsEvent
import dev.kyralabs.pingogram.feature.chat.ui.preview.SendAsPreviewData

/**
 * The send-as picker (`REQUIREMENTS.md` FR-C5), a [ModalBottomSheet] opened from the composer's avatar: "Send messages
 * as" in `titleLargeEmphasized`, then everyone the user can send as, a single choice ([SendAsRow]), or the delayed
 * loading indicator while Telegram is asked; the list crossfades in on the motion scheme's default effects spring while
 * the sheet resizes on its default spatial spring. While a new choice goes to TDLib ([SendAsSheet.isChanging]) it shows
 * chosen and a wavy progress indicator shows under the title; the view model closes the sheet once it is done.
 *
 * Dragging it down, tapping the scrim and the back gesture (Material's predictive back) close it
 * ([SendAsEvent.Dismiss]). The list scrolls where the window is too short.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendAsBottomSheet(
    sheet: SendAsSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val sheetState =
        rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val currentOnEvent by rememberUpdatedState(onEvent)
    ModalBottomSheet(onDismissRequest = { currentOnEvent(SendAsEvent.Dismiss) }, sheetState = sheetState) {
        SendAsContent(sheet, currentOnEvent)
    }
}

/** The title, the progress while a choice is on its way, and the senders or the loading indicator. */
@Composable
private fun SendAsContent(
    sheet: SendAsSheet,
    onEvent: (ChatEvent) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val spacing = MaterialTheme.spacing
    Column(Modifier.padding(bottom = spacing.large)) {
        SendAsTitle()
        if (sheet.isChanging) {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.large, vertical = spacing.small),
            )
        }
        AnimatedContent(
            targetState = sheet.options,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
            },
            contentKey = { it == null },
            label = "sendAsOptions",
        ) { options ->
            if (options == null) {
                Box(Modifier.fillMaxWidth().padding(spacing.extraLarge), contentAlignment = Alignment.Center) {
                    DelayedLoadingIndicator()
                }
            } else {
                LazyColumn(Modifier.selectableGroup()) {
                    items(options, key = { it.id }, contentType = { CONTENT_TYPE_SENDER }) { option ->
                        SendAsRow(option = option, onClick = { onEvent(SendAsEvent.Choose(option.sender)) })
                    }
                }
            }
        }
    }
}

/** "Send messages as", a heading in `titleLargeEmphasized`. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SendAsTitle() {
    Text(
        text = stringResource(R.string.send_as_title),
        style = MaterialTheme.typography.titleLargeEmphasized,
        modifier =
            Modifier
                .padding(horizontal = MaterialTheme.spacing.large, vertical = MaterialTheme.spacing.small)
                .semantics { heading() },
    )
}

/**
 * One sender: its avatar, name and what it is (the user's account, a channel, the group for an anonymous admin) in a
 * list item inset from the sheet's sides like the chat list's rows (`docs/DESIGN_SYSTEM.md` §4). The chosen one takes
 * the list item's selected container (`secondaryContainer`, `large`), its placeholder avatar in `primary`. A sender that
 * needs Telegram Premium is disabled and says so, without an offer.
 */
@Composable
private fun SendAsRow(
    option: SendAsOption,
    onClick: () -> Unit,
) {
    val name = option.name.resolve()
    val insets = rowInsets()
    ListItem(
        selected = option.selected,
        onClick = onClick,
        enabled = !option.needsPremium,
        modifier = Modifier.padding(insets),
        // A disabled row stays on the sheet's color: only its content dims.
        colors =
            ListItemDefaults.colors(
                containerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
        contentPadding = insetContentPadding(insets),
        leadingContent = {
            Avatar(
                photo = option.photo,
                name = name,
                id = option.id,
                size = MaterialTheme.sizes.avatarSmall,
                onSelection = option.selected,
            )
        },
        supportingContent = {
            Text(stringResource(if (option.needsPremium) R.string.send_as_needs_premium else option.kind.label))
        },
    ) {
        Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** What a sender's second line says it is. */
private val SenderKind.label: Int
    get() =
        when (this) {
            SenderKind.Account -> R.string.send_as_account
            SenderKind.Channel -> R.string.send_as_channel
            SenderKind.Anonymous -> R.string.send_as_anonymous
            SenderKind.Group -> R.string.send_as_group
        }

/** A row's inset in the sheet: `spacing.small` from its sides, half the segmented gap from the rows around. */
@Composable
private fun rowInsets(): PaddingValues =
    PaddingValues(horizontal = MaterialTheme.spacing.small, vertical = ListItemDefaults.SegmentedGap / 2)

/** The list item's content padding less [insets], so a row's content stays where the list item puts it. */
private fun insetContentPadding(insets: PaddingValues): PaddingValues {
    val padding = ListItemDefaults.ContentPadding
    val direction = LayoutDirection.Ltr
    return PaddingValues(
        start = padding.calculateStartPadding(direction) - insets.calculateStartPadding(direction),
        top = padding.calculateTopPadding() - insets.calculateTopPadding(),
        end = padding.calculateEndPadding(direction) - insets.calculateEndPadding(direction),
        bottom = padding.calculateBottomPadding() - insets.calculateBottomPadding(),
    )
}

private const val CONTENT_TYPE_SENDER = "sender"

@PingogramPreviews
@Composable
private fun SendAsSheetPreview() {
    PingogramPreview { SendAsContent(SendAsPreviewData.sheet, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SendAsSheetChangingPreview() {
    PingogramPreview { SendAsContent(SendAsPreviewData.changing, onEvent = {}) }
}

@PingogramPreviews
@Composable
private fun SendAsSheetLoadingPreview() {
    PingogramPreview { SendAsContent(SendAsPreviewData.loading, onEvent = {}) }
}
