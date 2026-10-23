package dev.kyralabs.pingogram.feature.settings.ui.stickers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import dev.kyralabs.pingogram.core.ui.component.windowMargin
import dev.kyralabs.pingogram.core.ui.sticker.StickerCover
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.ListReorderState
import dev.kyralabs.pingogram.feature.settings.ui.PageItem
import dev.kyralabs.pingogram.feature.settings.ui.SettingsFooter
import dev.kyralabs.pingogram.feature.settings.ui.SettingsListRowStyle
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSectionHeader
import dev.kyralabs.pingogram.feature.settings.ui.dragAfterLongPress
import dev.kyralabs.pingogram.feature.settings.ui.dragHandle
import dev.kyralabs.pingogram.feature.settings.ui.rememberListReorderState
import dev.kyralabs.pingogram.feature.settings.ui.settingsListRow
import dev.kyralabs.pingogram.feature.settings.ui.settingsRowColors
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The list of Settings → Stickers: Trending stickers and Archived stickers, then the user's sets in their order
// (dragged into another one by a handle or a long press), each opening its menu.

/** The page's rows; [onOpenList] opens Trending stickers (false) or Archived stickers (true). */
@Suppress("LongParameterList") // The page's state, its events, its pages, the menu's actions and the list's state.
@Composable
internal fun StickersList(
    state: StickersUiState.Content,
    onEvent: (StickersEvent) -> Unit,
    onOpenList: (archived: Boolean) -> Unit,
    actions: StickerSetActions,
    listState: LazyListState,
    contentPadding: PaddingValues,
) {
    val layoutDirection = LocalLayoutDirection.current
    val spacing = MaterialTheme.spacing
    val style = SettingsListRowStyle(MaterialTheme.sizes.contentMaxWidth, windowMargin(), MaterialTheme.motionScheme)
    val reorder =
        rememberListReorderState(listState, state.installed.map { it.id }, ::stickerSetRowKey) {
            onEvent(StickersEvent.Reorder(it))
        }
    val byId = state.installed.associateBy { it.id }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection),
                end = contentPadding.calculateEndPadding(layoutDirection),
                top = contentPadding.calculateTopPadding() + spacing.small,
                bottom = contentPadding.calculateBottomPadding() + spacing.extraLarge,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
    ) {
        lists(state, style, onOpenList)
        item(key = HEADER, contentType = HEADER) {
            SettingsSectionHeader(
                stringResource(R.string.settings_stickers_section_yours),
                // A group's distance from the one above it, less the gap every item keeps.
                Modifier
                    .settingsListRow(this, style)
                    .padding(top = MaterialTheme.spacing.extraLarge - ListItemDefaults.SegmentedGap),
            )
        }
        if (reorder.order.isEmpty()) {
            item(key = NONE, contentType = FOOTER) {
                SettingsFooter(stringResource(R.string.settings_stickers_none), Modifier.settingsListRow(this, style))
            }
        } else {
            sets(reorder, byId, style, onEvent, actions)
        }
    }
}

/** Trending stickers and Archived stickers, with their counts where they are known. */
private fun LazyListScope.lists(
    state: StickersUiState.Content,
    style: SettingsListRowStyle,
    onOpenList: (archived: Boolean) -> Unit,
) {
    item(key = TRENDING, contentType = PAGE) {
        val count = state.newTrendingCount
        Box(Modifier.settingsListRow(this, style)) {
            PageItem(
                title = stringResource(R.string.settings_stickers_trending),
                summary =
                    if (count != null && count > 0) {
                        pluralStringResource(R.plurals.settings_stickers_trending_new, count, formatCount(count))
                    } else {
                        stringResource(R.string.settings_stickers_trending_summary)
                    },
                icon = R.drawable.ic_trending_up,
                shapes = ListItemDefaults.segmentedShapes(0, 2),
                onClick = { onOpenList(false) },
            )
        }
    }
    item(key = ARCHIVED, contentType = PAGE) {
        val count = state.archivedCount
        Box(Modifier.settingsListRow(this, style)) {
            PageItem(
                title = stringResource(R.string.settings_stickers_archived),
                summary =
                    if (count != null && count > 0) {
                        pluralStringResource(R.plurals.settings_stickers_set_count, count, formatCount(count))
                    } else {
                        stringResource(R.string.settings_stickers_archived_summary)
                    },
                icon = UiR.drawable.ic_archive,
                shapes = ListItemDefaults.segmentedShapes(1, 2),
                onClick = { onOpenList(true) },
            )
        }
    }
}

/** The user's sets under their heading, in the order the finger leaves them while one is dragged. */
private fun LazyListScope.sets(
    reorder: ListReorderState<Long>,
    byId: Map<Long, StickerSetItem>,
    style: SettingsListRowStyle,
    onEvent: (StickersEvent) -> Unit,
    actions: StickerSetActions,
) {
    val order = reorder.order
    itemsIndexed(order, key = { _, id -> stickerSetRowKey(id) }, contentType = { _, _ -> ROW }) { index, id ->
        val set = byId[id] ?: return@itemsIndexed
        val dragged = reorder.draggedId == id
        InstalledSetRow(
            set = set,
            shapes = ListItemDefaults.segmentedShapes(index, order.size),
            dragged = dragged,
            onMove = { up -> onEvent(StickersEvent.Move(id, up)) },
            canMoveUp = index > 0,
            canMoveDown = index < order.lastIndex,
            actions = actions,
            modifier =
                Modifier
                    .settingsListRow(this, style, animatePlacement = !dragged)
                    .zIndex(if (dragged) 1f else 0f)
                    .graphicsLayer { translationY = if (dragged) reorder.offset else 0f }
                    .dragAfterLongPress(reorder, id),
            handleModifier = Modifier.dragHandle(reorder, id),
        )
    }
    item(key = FOOTER, contentType = FOOTER) {
        SettingsFooter(stringResource(R.string.settings_stickers_footer), Modifier.settingsListRow(this, style))
    }
}

/**
 * An installed set: its cover, its title (user content) over how many stickers it has, and a drag handle at the end
 * ([handleModifier] starts the drag), in the list item's dragged shape while [dragged]. A tap opens its menu (Show
 * stickers, Share link, Archive; Remove in `error`); screen readers get the menu's items and "Move up" / "Move down" as custom
 * actions.
 */
@Suppress("LongParameterList") // A row's content, its states, its menu and the two ways to move it.
@Composable
private fun InstalledSetRow(
    set: StickerSetItem,
    shapes: ListItemShapes,
    dragged: Boolean,
    onMove: (up: Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    actions: StickerSetActions,
    modifier: Modifier = Modifier,
    handleModifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val labels =
        RowLabels(
            show = stringResource(R.string.settings_stickers_show),
            moveUp = stringResource(R.string.settings_stickers_move_up),
            moveDown = stringResource(R.string.settings_stickers_move_down),
            share = stringResource(R.string.settings_stickers_share),
            archive = stringResource(R.string.settings_stickers_archive),
            remove = stringResource(R.string.settings_stickers_remove),
        )
    val rowShapes = if (dragged) shapes.copy(shape = shapes.draggedShape) else shapes
    Box(modifier) {
        SegmentedListItem(
            onClick = { menuOpen = true },
            shapes = rowShapes,
            modifier =
                Modifier.semantics {
                    customActions = rowActions(labels, set, canMoveUp, canMoveDown, onMove, actions)
                },
            leadingContent = { StickerCover(set.cover, MaterialTheme.sizes.leadingIconContainer) },
            supportingContent = { Text(stickerCount(set.count)) },
            trailingContent = {
                // Screen readers move the set with the row's custom actions instead.
                Box(modifier = handleModifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_drag_indicator), contentDescription = null)
                }
            },
            colors = settingsRowColors(),
        ) {
            Text(text = set.title, style = LocalTextStyle.current.userContent)
        }
        SetMenu(menuOpen, onDismiss = { menuOpen = false }, set, actions)
    }
}

/** A set's menu: Show stickers, Share link and Archive, then Remove in `error` in a group of its own. */
@Composable
private fun SetMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    set: StickerSetItem,
    actions: StickerSetActions,
) {
    DropdownMenuPopup(expanded = open, onDismissRequest = onDismiss) {
        val everyday =
            listOf(
                R.string.settings_stickers_show to actions.onOpen,
                R.string.settings_stickers_share to actions.onShare,
                R.string.settings_stickers_archive to actions.onArchive,
            )
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 2)) {
            everyday.forEachIndexed { index, (label, action) ->
                DropdownMenuItem(
                    onClick = {
                        onDismiss()
                        action(set)
                    },
                    text = { Text(stringResource(label)) },
                    shape = MenuDefaults.itemShape(index, everyday.size).shape,
                )
            }
        }
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 1, count = 2)) {
            DropdownMenuItem(
                onClick = {
                    onDismiss()
                    actions.onRemove(set)
                },
                text = { Text(stringResource(R.string.settings_stickers_remove)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
            )
        }
    }
}

/** The strings of a row's custom actions, read in composition and used where the semantics are read. */
private class RowLabels(
    val show: String,
    val moveUp: String,
    val moveDown: String,
    val share: String,
    val archive: String,
    val remove: String,
)

@Suppress("LongParameterList") // The labels, the set, where it may move and what each action does.
private fun rowActions(
    labels: RowLabels,
    set: StickerSetItem,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMove: (up: Boolean) -> Unit,
    actions: StickerSetActions,
): List<CustomAccessibilityAction> {
    fun action(
        label: String,
        run: () -> Unit,
    ) = CustomAccessibilityAction(label) {
        run()
        true
    }
    return buildList {
        if (canMoveUp) add(action(labels.moveUp) { onMove(true) })
        if (canMoveDown) add(action(labels.moveDown) { onMove(false) })
        add(action(labels.show) { actions.onOpen(set) })
        add(action(labels.share) { actions.onShare(set) })
        add(action(labels.archive) { actions.onArchive(set) })
        add(action(labels.remove) { actions.onRemove(set) })
    }
}

/** "24 stickers", in the screen's language. */
@Composable
internal fun stickerCount(count: Int): String =
    pluralStringResource(R.plurals.settings_stickers_sticker_count, count, formatCount(count))

@Composable
private fun formatCount(count: Int): String =
    NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(count)

/** The key of set [id]'s row in the page's lazy list. */
private fun stickerSetRowKey(id: Long): String = "set-$id"

private const val TRENDING = "trending"
private const val ARCHIVED = "archived"
private const val PAGE = "page"
private const val HEADER = "header"
private const val NONE = "none"
private const val FOOTER = "footer"
private const val ROW = "row"
