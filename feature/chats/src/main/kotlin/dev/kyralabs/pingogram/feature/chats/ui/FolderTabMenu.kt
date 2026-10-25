package dev.kyralabs.pingogram.feature.chats.ui

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.round
import dev.kyralabs.pingogram.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * What a folder tab's menu offers (`docs/DESIGN_SYSTEM.md` §6 "Folder tabs"): editing the folder (or the folders, on
 * "All chats") where the app has a folder editor, and marking every chat of its list as read while any is unread.
 */
internal enum class FolderTabAction(
    @param:DrawableRes val icon: Int,
) {
    EditFolder(UiR.drawable.ic_edit),
    MarkAllAsRead(UiR.drawable.ic_mark_chat_read),
}

/** The label of [action] on [tab]: "Edit folders" on "All chats", which edits them all. */
@StringRes
internal fun FolderTab.labelOf(action: FolderTabAction): Int =
    when (action) {
        FolderTabAction.EditFolder -> {
            if (this is FolderTab.AllChats) R.string.chat_list_edit_folders else R.string.chat_list_edit_folder
        }

        FolderTabAction.MarkAllAsRead -> {
            R.string.chat_list_mark_all_as_read
        }
    }

/** The actions of [tab]'s menu, in their order; empty means no menu. */
internal fun FolderTab.actions(canEditFolders: Boolean): ImmutableList<FolderTabAction> =
    listOfNotNull(
        FolderTabAction.EditFolder.takeIf { canEditFolders },
        FolderTabAction.MarkAllAsRead.takeIf { hasUnread },
    ).toImmutableList()

/**
 * Which tab's menu is open ([openIndex], read by composition only where the menu shows), and where each tab is
 * ([anchors], written in the layout phase and read only when a menu opens), so the menu shows below the tab without
 * a layout around each of the group's buttons.
 */
@Stable
internal class FolderTabMenuState {
    var openIndex by mutableStateOf<Int?>(null)

    /** The tabs' places, by index; not observed. */
    val anchors = HashMap<Int, LayoutCoordinates>()

    /** The tabs' container, which the menu's anchor is placed in; not observed. */
    var container: LayoutCoordinates? = null
}

/**
 * The actions of one tab's menu ([actions], none while the tab is disabled), their names for screen readers
 * ([labels], read only when the semantics are) and what runs one ([onAction]).
 */
internal class FolderTabActions(
    val actions: ImmutableList<FolderTabAction>,
    val labels: () -> List<String>,
    val onAction: (FolderTabAction) -> Unit,
)

/**
 * The tab at [index] of [state]: its place for the menu's anchor, and, while it has [actions], its menu on a long press
 * (with the `LongPress` haptic, from [haptics]) or a right click ([opensMenuOnLongPress]), and the actions as screen
 * readers' custom actions. Modifies the tab's outermost node, so it keeps whatever semantics the tab sets on itself.
 */
internal fun Modifier.folderTabMenu(
    state: FolderTabMenuState,
    index: Int,
    actions: FolderTabActions,
    haptics: HapticFeedback,
): Modifier {
    val placed = onPlaced { state.anchors[index] = it }
    if (actions.actions.isEmpty()) return placed
    return placed
        .opensMenuOnLongPress(state, index, haptics)
        .semantics {
            val names = actions.labels()
            customActions =
                actions.actions.mapIndexed { position, action ->
                    CustomAccessibilityAction(names[position]) {
                        actions.onAction(action)
                        true
                    }
                }
        }
}

/** Keeps the container of the tabs in [state], for the menu's anchor ([FolderTabMenuAnchor]). */
internal fun Modifier.folderTabMenuContainer(state: FolderTabMenuState): Modifier = onPlaced { state.container = it }

/**
 * The open menu of [tabs] in [state], if any: an expressive [DropdownMenuPopup] below its tab, one group of the tab's
 * [FolderTabAction]s with their icons. Choosing one closes the menu and calls [onAction] with the tab. Placed in the
 * tabs' container over the open tab ([FolderTabMenuState.anchors]).
 */
@Composable
internal fun FolderTabMenuAnchor(
    state: FolderTabMenuState,
    tabs: ImmutableList<FolderTab>,
    canEditFolders: Boolean,
    onAction: (FolderTab, FolderTabAction) -> Unit,
) {
    val index = state.openIndex ?: return
    val tab = tabs.getOrNull(index) ?: return
    val actions = tab.actions(canEditFolders)
    Box(Modifier.overTab(state, index)) {
        DropdownMenuPopup(expanded = actions.isNotEmpty(), onDismissRequest = { state.openIndex = null }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = 1)) {
                actions.forEachIndexed { position, action ->
                    DropdownMenuItem(
                        onClick = {
                            state.openIndex = null
                            onAction(tab, action)
                        },
                        text = { Text(stringResource(tab.labelOf(action))) },
                        shape = MenuDefaults.itemShape(position, actions.size).shape,
                        leadingIcon = { Icon(painter = painterResource(action.icon), contentDescription = null) },
                    )
                }
            }
        }
    }
}

/**
 * Lays this anchor out over tab [index] of [state], as large as the tab and where it is in the container, read in the
 * layout phase; nothing until the tab is placed.
 */
private fun Modifier.overTab(
    state: FolderTabMenuState,
    index: Int,
): Modifier =
    layout { measurable, _ ->
        val container = state.container?.takeIf { it.isAttached }
        val tab = state.anchors[index]?.takeIf { it.isAttached }
        if (container == null || tab == null) {
            val placeable = measurable.measure(Constraints.fixed(0, 0))
            return@layout layout(0, 0) { placeable.place(0, 0) }
        }
        val position = container.localPositionOf(tab, Offset.Zero).round()
        val placeable = measurable.measure(Constraints.fixed(tab.size.width, tab.size.height))
        layout(placeable.width, placeable.height) { placeable.place(position) }
    }

/**
 * Opens tab [index]'s menu in [state] once the pointer has stayed down, without moving beyond the touch slop, for the
 * long-press timeout (with the `LongPress` haptic), or at once for a mouse's secondary button (a right click), and
 * then consumes the rest of the gesture. It watches the pointer before the tab does (the initial pass), so the tab
 * sees the release consumed and is not selected; a scroll of the tabs that starts first cancels it. Keyed by what it
 * reads only, so recomposing the tab never restarts a gesture under way.
 */
private fun Modifier.opensMenuOnLongPress(
    state: FolderTabMenuState,
    index: Int,
    haptics: HapticFeedback,
): Modifier =
    pointerInput(state, index, haptics) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (currentEvent.buttons.isSecondaryPressed) {
                down.consume()
                state.openIndex = index
                consumeUntilUp()
            } else if (awaitLongPress(down)) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                state.openIndex = index
                consumeUntilUp()
            }
        }
    }

/**
 * Whether the pointer that went [down] stays down, alone and within the touch slop, for the long-press timeout.
 * Watches the initial pass and consumes nothing.
 */
private suspend fun AwaitPointerEventScope.awaitLongPress(down: PointerInputChange): Boolean {
    val ended =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                val holds =
                    change != null &&
                        change.pressed &&
                        !change.isConsumed &&
                        event.changes.size == 1 &&
                        (change.position - down.position).getDistance() <= viewConfiguration.touchSlop
            } while (holds)
        }
    // No end within the timeout: a long press.
    return ended == null
}

/** Consumes every change in the initial pass until all pointers are up, so the tab takes no tap. */
private suspend fun AwaitPointerEventScope.consumeUntilUp() {
    do {
        val event = awaitPointerEvent(PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}

/**
 * What the tabs need besides themselves: whether they are [enabled], and for their menus its [state], whether folders
 * can be edited, the haptics and resources of the screen, and what an action does.
 */
internal class TabMenu(
    val state: FolderTabMenuState,
    val canEditFolders: Boolean,
    val enabled: Boolean,
    val haptics: HapticFeedback,
    val resources: Resources,
    val onAction: (FolderTab, FolderTabAction) -> Unit,
)

/** Tab [index]'s long press, right click and custom actions for its menu ([folderTabMenu]). */
internal fun Modifier.tabMenu(
    menu: TabMenu,
    tab: FolderTab,
    index: Int,
): Modifier {
    // Disabled tabs have no menu.
    val actions = if (menu.enabled) tab.actions(menu.canEditFolders) else persistentListOf()
    return folderTabMenu(
        state = menu.state,
        index = index,
        actions =
            FolderTabActions(
                actions = actions,
                labels = { actions.map { menu.resources.getString(tab.labelOf(it)) } },
                onAction = { menu.onAction(tab, it) },
            ),
        haptics = menu.haptics,
    )
}
