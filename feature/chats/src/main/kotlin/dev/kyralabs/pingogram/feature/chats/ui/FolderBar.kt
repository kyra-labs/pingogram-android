package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/**
 * The folder tabs as a floating bar over the top of the chat list's sheet (`docs/DESIGN_SYSTEM.md` §13.3; owner
 * decision 2026-11-13): [FolderTabs], inset `spacing.small` from the sheet's sides and top, start-aligned in the rows'
 * width ([MaterialTheme.sizes]' `contentMaxWidth`, centred like them on wide windows). It stays where it is while the
 * lists scroll and slide under it.
 *
 * It must come before the lists in its parent: measured first, it tells [space] how far below the sheet's top the
 * lists start (its inset, its height and `spacing.small` below it), which they read in the same layout pass
 * ([FolderBarSpace.below]); drawn above them; and reached first by screen readers, whatever row scrolled under it.
 */
@Composable
internal fun FolderBar(
    state: ChatListUiState,
    onEvent: (ChatListEvent) -> Unit,
    onEditFolder: ((folderId: Int?) -> Unit)?,
    space: FolderBarSpace,
    modifier: Modifier = Modifier,
) {
    val gap = MaterialTheme.spacing.small
    Box(
        modifier
            .fillMaxWidth()
            .zIndex(1f)
            .testTag(FOLDER_BAR_TEST_TAG)
            .semantics {
                isTraversalGroup = true
                traversalIndex = -1f
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .widthIn(max = MaterialTheme.sizes.contentMaxWidth)
                .fillMaxWidth()
                // Its inset above and its height: the same as its height and the gap below it, where the rows start.
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    space.extra = placeable.height.toDp()
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                }.padding(start = gap, top = gap, end = gap),
        ) {
            FolderTabs(
                tabs = state.folderTabs,
                selectedFolderId = state.selectedFolderId,
                onSelect = { onEvent(ChatListEvent.SelectFolder(it)) },
                // Switching lists, which would drop the selection, waits until the selection is done.
                enabled = state.selection == null,
                onMarkAllAsRead = { onEvent(ChatListEvent.MarkAllAsRead(it)) },
                onEditFolder = onEditFolder,
            )
        }
    }
}

/** The bar's tag, for tests that find where it sits. */
internal const val FOLDER_BAR_TEST_TAG = "folder_bar"

/**
 * How much more room the lists leave at their top for the [FolderBar] above them: written as the bar is measured, read
 * when the lists measure ([below]), so the bar's height reaches them without composing them again.
 */
@Stable
internal class FolderBarSpace {
    var extra: Dp by mutableStateOf(0.dp)

    /** [padding] with the bar's room added to its top, read when asked. */
    fun below(padding: PaddingValues): PaddingValues = BelowFolderBar(padding, this)
}

private class BelowFolderBar(
    private val padding: PaddingValues,
    private val space: FolderBarSpace,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = padding.calculateTopPadding() + space.extra

    override fun calculateBottomPadding(): Dp = padding.calculateBottomPadding()

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateLeftPadding(layoutDirection)

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        padding.calculateRightPadding(layoutDirection)
}
