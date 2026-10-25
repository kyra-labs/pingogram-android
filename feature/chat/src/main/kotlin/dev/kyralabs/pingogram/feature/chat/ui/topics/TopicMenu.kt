package dev.kyralabs.pingogram.feature.chat.ui.topics

import android.content.res.Resources
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** One action of a topic's menu: its label, its outlined symbol, what it sends, and whether it destroys. */
private data class TopicMenuItem(
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
    val event: TopicEvent,
    val isDestructive: Boolean = false,
)

/**
 * What [this] topic's menu offers, in its order: rename, close or reopen, pin or unpin, hide or show General, then
 * delete, each only where [TopicMenuState.actions] allows it.
 */
private fun TopicMenuState.items(): List<TopicMenuItem> {
    val actions = actions
    return buildList {
        if (actions.canEdit) add(TopicMenuItem(R.string.topic_edit, UiR.drawable.ic_edit, TopicEvent.Edit(topicId)))
        if (actions.canClose) {
            add(
                if (isClosed) {
                    TopicMenuItem(
                        R.string.topic_reopen,
                        UiR.drawable.ic_lock_open,
                        TopicEvent.SetClosed(topicId, false),
                    )
                } else {
                    TopicMenuItem(R.string.topic_close, UiR.drawable.ic_lock, TopicEvent.SetClosed(topicId, true))
                },
            )
        }
        if (actions.canPin) {
            add(
                if (isPinned) {
                    TopicMenuItem(R.string.topic_unpin, UiR.drawable.ic_keep_off, TopicEvent.SetPinned(topicId, false))
                } else {
                    TopicMenuItem(R.string.topic_pin, UiR.drawable.ic_push_pin, TopicEvent.SetPinned(topicId, true))
                },
            )
        }
        if (actions.canHideGeneral) {
            add(
                if (isHidden) {
                    TopicMenuItem(
                        R.string.topic_show_general,
                        UiR.drawable.ic_visibility,
                        TopicEvent.SetGeneralHidden(false),
                    )
                } else {
                    TopicMenuItem(
                        R.string.topic_hide_general,
                        UiR.drawable.ic_visibility_off,
                        TopicEvent.SetGeneralHidden(true),
                    )
                },
            )
        }
        if (actions.canDelete) {
            add(
                TopicMenuItem(
                    R.string.topic_delete,
                    UiR.drawable.ic_delete,
                    TopicEvent.Delete(topicId),
                    isDestructive = true,
                ),
            )
        }
    }
}

/** The topic's menu items as screen readers' custom actions, built only when something reads the semantics tree. */
internal fun TopicMenuState.customActions(
    resources: Resources,
    onEvent: (TopicEvent) -> Unit,
): List<CustomAccessibilityAction> =
    items().map { item ->
        CustomAccessibilityAction(resources.getString(item.label)) {
            onEvent(item.event)
            true
        }
    }

/**
 * A topic's menu (`docs/DESIGN_SYSTEM.md` §6 "Forum topics"), an expressive [DropdownMenuPopup] anchored to what
 * opened it, as the Contacts rows' menu: the topic's actions in one group, Delete in `error` in a second, as the
 * message actions keep destructive ones apart. A tap closes it ([onDismiss]) before its event.
 */
@Composable
internal fun TopicActionsMenu(
    expanded: Boolean,
    menu: TopicMenuState,
    onEvent: (TopicEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val (destructive, everyday) = menu.items().partition { it.isDestructive }
    val groups = listOf(everyday, destructive).filter { it.isNotEmpty() }
    DropdownMenuPopup(expanded = expanded, onDismissRequest = onDismiss) {
        groups.forEachIndexed { groupIndex, items ->
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = groupIndex, count = groups.size)) {
                items.forEachIndexed { index, item ->
                    val error = MaterialTheme.colorScheme.error
                    DropdownMenuItem(
                        onClick = {
                            onDismiss()
                            onEvent(item.event)
                        },
                        text = { Text(stringResource(item.label)) },
                        shape = MenuDefaults.itemShape(index, items.size).shape,
                        leadingIcon = { Icon(painter = painterResource(item.icon), contentDescription = null) },
                        colors =
                            if (item.isDestructive) {
                                MenuDefaults.itemColors(textColor = error, leadingIconColor = error)
                            } else {
                                MenuDefaults.itemColors()
                            },
                    )
                }
            }
        }
    }
}

/**
 * "Manage topic" in the top bar of a topic's conversation (`docs/DESIGN_SYSTEM.md` §6 "Conversation top bar", after
 * search, at most two): a filled tonal icon button with the expressive press shapes, which opens the topic's menu
 * ([TopicActionsMenu]) anchored to it, as the call button opens its menu.
 */
@Composable
internal fun TopicActionButton(
    menu: TopicMenuState,
    onEvent: (TopicEvent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(UiR.drawable.ic_edit),
                contentDescription = stringResource(R.string.topic_manage),
            )
        }
        TopicActionsMenu(expanded = open, menu = menu, onEvent = onEvent, onDismiss = { open = false })
    }
}

/** "New topic" in the topic list's top bar, a filled tonal icon button like the conversation's actions. */
@Composable
internal fun NewTopicButton(onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, shapes = IconButtonDefaults.shapes()) {
        Icon(painter = painterResource(UiR.drawable.ic_add), contentDescription = stringResource(R.string.topics_new))
    }
}
