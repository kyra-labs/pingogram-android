package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import android.icu.text.ListFormatter
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.ArchiveRowStyle
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.ChatListPreviewData
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The row's styles for screen readers, as its menu offers them: compact or full, and hide. */
internal fun styleActions(
    entry: ArchiveEntry,
    resources: Resources,
    onStyle: (ArchiveRowStyle) -> Unit,
): List<CustomAccessibilityAction> =
    archiveMenuItems(entry.isCompact).map { item ->
        CustomAccessibilityAction(resources.getString(item.label)) {
            onStyle(item.style)
            true
        }
    }

/** An entry of the archive row's menu: what it is called, its symbol and the style it shows the row in. */
private class ArchiveMenuItem(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val style: ArchiveRowStyle,
)

/** "Make compact" (or "Show chat names" on a compact row), then "Hide from chat list". */
private fun archiveMenuItems(isCompact: Boolean): List<ArchiveMenuItem> =
    listOf(
        if (isCompact) {
            ArchiveMenuItem(R.string.chat_list_archive_row_expand, R.drawable.ic_unfold_more, ArchiveRowStyle.Full)
        } else {
            ArchiveMenuItem(R.string.chat_list_archive_row_collapse, R.drawable.ic_unfold_less, ArchiveRowStyle.Compact)
        },
        ArchiveMenuItem(R.string.chat_list_archive_row_hide, UiR.drawable.ic_visibility_off, ArchiveRowStyle.Hidden),
    )

/** The archive row's menu, an expressive [DropdownMenuPopup] of one group ([archiveMenuItems]). */
@Composable
internal fun ArchiveRowMenu(
    expanded: Boolean,
    isCompact: Boolean,
    onDismiss: () -> Unit,
    onStyle: (ArchiveRowStyle) -> Unit,
) {
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        val items = archiveMenuItems(isCompact)
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
            items.forEachIndexed { index, item ->
                DropdownMenuItem(
                    onClick = { onStyle(item.style) },
                    text = { Text(stringResource(item.label)) },
                    shape = MenuDefaults.itemShape(index, items.size).shape,
                    leadingIcon = { Icon(painter = painterResource(item.icon), contentDescription = null) },
                )
            }
        }
    }
}

/**
 * The archive row's swipe: a [SwipeToDismissBox] that hides the row ([onHide]) when swiped away either way (mirrored
 * in right-to-left layouts), revealing "Hide" on `secondaryContainer` as the chats' swipes reveal their actions
 * ([SwipeBackground]), with a `TextHandleMove` haptic at the threshold and a `Confirm` haptic on Android 14 and later
 * as it hides. The row then leaves the list, and "Undo" on the snackbar brings it back at rest: its swipe state is
 * not saved. Without [onHide] (selection mode), or while swiping changes the folder ([ChatSwipes.changesFolder]), it
 * does not move. [content] is told while the row is away from its place, to show its container.
 */
@Composable
internal fun ArchiveRowSwipe(
    onHide: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (dragged: Boolean) -> Unit,
) {
    val threshold = SwipeToDismissBoxDefaults.positionalThreshold
    val state = remember { SwipeToDismissBoxState(SwipeToDismissBoxValue.Settled, threshold) }
    val iconScale = remember { Animatable(SWIPE_ICON_RESTING_SCALE) }
    val dragged by remember(state) { derivedStateOf { state.dismissDirection != SwipeToDismissBoxValue.Settled } }
    val iconSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val haptics by rememberUpdatedState(LocalHapticFeedback.current)
    val currentOnHide by rememberUpdatedState(onHide)
    // One instance for the row's life, so one swipe hides it once (see ChatRow's rememberOnSwiped).
    val onDismiss =
        remember(state) {
            { direction: SwipeToDismissBoxValue ->
                if (direction != SwipeToDismissBoxValue.Settled) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    currentOnHide?.invoke()
                }
            }
        }
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            SwipeBackground(state, HideReveal, HideReveal, iconScale, SwipeFeedback(threshold, iconSpec))
        },
        modifier = modifier,
        gesturesEnabled = onHide != null && !LocalChatSwipes.current.changesFolder,
        onDismiss = onDismiss,
    ) { content(dragged) }
}

/** What swiping the archive row reveals on either side. */
private val HideReveal =
    SwipeReveal(UiR.drawable.ic_visibility_off, R.string.chat_list_archive_row_swipe_hide, SwipeTone.Secondary)
