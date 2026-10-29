package dev.kyralabs.pingogram.feature.chat.ui.topics

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.insetContentPadding
import dev.kyralabs.pingogram.feature.chat.ui.senderPreview
import dev.kyralabs.pingogram.feature.chat.ui.sheetRowInsets
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * One topic (`docs/DESIGN_SYSTEM.md` §6): Material's two-line list item inset in the sheet like the chat list's rows,
 * the topic's icon in a 48 dp circle ([TopicIconBadge]), its name (`titleMediumEmphasized` while anything is unread,
 * with a lock when the topic is closed, an eye crossed out when General is hidden), one line of its last message, and
 * at the end its time over the pin, the mention badge and the unread count. Screen readers hear it as one: name,
 * state, message, time, counts.
 *
 * Where the user may manage it ([TopicRow.menu]), a long press opens its menu ([TopicActionsMenu], events to
 * [onTopicEvent]), the row in the list item's selected colors and shape while it shows; screen readers get its items
 * as custom actions. A pinned topic that may be reordered ([reorder] not null) ends in a drag handle
 * ([handleModifier] starts the drag, 48 dp wide), takes the list item's dragged shape while [dragged], and moves up
 * and down for screen readers.
 */
@Suppress("LongParameterList", "LongMethod") // A row's content, its menu and the ways to move it, in one list item.
@Composable
internal fun TopicRowItem(
    topic: TopicRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onTopicEvent: (TopicEvent) -> Unit = {},
    reorder: TopicReorder? = null,
    dragged: Boolean = false,
    handleModifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val insets = sheetRowInsets()
    val resources = LocalResources.current
    val preview = topic.preview?.let { senderPreview(topic.sender, it) }
    val unread = topic.unreadCount > 0
    val menu = topic.menu
    Box(modifier) {
        ListItem(
            onClick = onClick,
            onLongClick = menu?.let { { menuOpen = true } },
            onLongClickLabel = menu?.let { stringResource(R.string.topic_menu) },
            modifier =
                Modifier.padding(insets).semantics(mergeDescendants = true) {
                    contentDescription = topicDescription(resources, topic, preview?.text)
                    val actions = menu?.customActions(resources, onTopicEvent).orEmpty() + reorder.moves(resources)
                    if (actions.isNotEmpty()) customActions = actions
                },
            colors = rowColors(dragged = dragged, raised = menuOpen),
            shapes = rowShapes(dragged = dragged, raised = menuOpen),
            contentPadding = insetContentPadding(insets),
            leadingContent = { TopicIconBadge(topic.icon, onSelection = menuOpen) },
            supportingContent =
                preview?.let {
                    {
                        Text(
                            text = it,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium.userContent,
                        )
                    }
                },
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TopicMeta(topic)
                    if (reorder != null) {
                        // Screen readers move the topic with the row's custom actions instead.
                        Box(
                            modifier = handleModifier.minimumInteractiveComponentSize().clearAndSetSemantics {},
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(UiR.drawable.ic_drag_indicator), contentDescription = null)
                        }
                    }
                }
            },
        ) {
            TopicName(
                topic,
                if (unread) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
            )
        }
        if (menu != null) {
            TopicActionsMenu(expanded = menuOpen, menu = menu, onEvent = onTopicEvent, onDismiss = { menuOpen = false })
        }
    }
}

/** What screen readers hear of [topic]'s row: its name, state, last message ([preview]), time and counts. */
private fun topicDescription(
    resources: Resources,
    topic: TopicRow,
    preview: String?,
): String =
    spokenList(
        resources,
        listOf(
            topic.name,
            resources.getString(R.string.topic_hidden).takeIf { topic.isHidden },
            resources.getString(R.string.topic_closed).takeIf { topic.isClosed },
            resources.getString(R.string.topic_pinned).takeIf { topic.isPinned },
            preview,
            topic.time,
            topic.unreadCount.takeIf { it > 0 }?.let { resources.getQuantityString(R.plurals.topic_unread, it, it) },
            topic.unreadMentionCount.takeIf { it > 0 }?.let {
                resources.getQuantityString(R.plurals.topic_mentions, it, it)
            },
        ),
    )

/**
 * How a pinned topic moves among the pinned ones: up where [canMoveUp], down where [canMoveDown], through [onMove]
 * (`up` true for up).
 */
internal class TopicReorder(
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val onMove: (up: Boolean) -> Unit,
)

/** "Move up" and "Move down" for screen readers, where the topic can move; none without [this]. */
private fun TopicReorder?.moves(resources: Resources): List<CustomAccessibilityAction> {
    if (this == null) return emptyList()
    return buildList {
        if (canMoveUp) {
            add(
                CustomAccessibilityAction(resources.getString(R.string.topic_move_up)) {
                    onMove(true)
                    true
                },
            )
        }
        if (canMoveDown) {
            add(
                CustomAccessibilityAction(resources.getString(R.string.topic_move_down)) {
                    onMove(false)
                    true
                },
            )
        }
    }
}

/** A row's colors: transparent on the sheet, the dragged ones while [dragged], the selected ones while [raised]. */
@Composable
private fun rowColors(
    dragged: Boolean,
    raised: Boolean,
): ListItemColors {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    return when {
        dragged -> colors.dragged()
        raised -> colors.selected()
        else -> colors
    }
}

/** A row's shapes: the dragged shape while [dragged], the selected one (`large`) while [raised]. */
@Composable
private fun rowShapes(
    dragged: Boolean,
    raised: Boolean,
): ListItemShapes {
    val shapes = ListItemDefaults.shapes()
    return when {
        dragged -> shapes.copy(shape = shapes.draggedShape)
        raised -> shapes.copy(shape = shapes.selectedShape)
        else -> shapes
    }
}

/** The list item's dragged colors in place of its resting ones, so a dragged row never shows the rows below it. */
private fun ListItemColors.dragged(): ListItemColors =
    copy(
        containerColor = draggedContainerColor,
        contentColor = draggedContentColor,
        leadingContentColor = draggedLeadingContentColor,
        trailingContentColor = draggedTrailingContentColor,
        supportingContentColor = draggedSupportingContentColor,
    )

/** The list item's selected colors in place of its resting ones, as a row whose menu is open shows them. */
private fun ListItemColors.selected(): ListItemColors =
    copy(
        containerColor = selectedContainerColor,
        contentColor = selectedContentColor,
        leadingContentColor = selectedLeadingContentColor,
        trailingContentColor = selectedTrailingContentColor,
        supportingContentColor = selectedSupportingContentColor,
    )

/** The topic's name, after an eye crossed out when it is hidden and a lock when it is closed. */
@Composable
private fun TopicName(
    topic: TopicRow,
    style: TextStyle,
) {
    Row(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.isHidden) {
            Icon(
                painter = painterResource(UiR.drawable.ic_visibility_off),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (topic.isClosed) {
            Icon(
                painter = painterResource(UiR.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(text = topic.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = style.userContent)
    }
}

/** The time over the pin, the mention badge and the unread count; nothing for screen readers, the row says it. */
@Composable
private fun TopicMeta(topic: TopicRow) {
    Column(
        modifier = Modifier.clearAndSetSemantics {},
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        topic.time?.let {
            Text(
                text = it,
                style =
                    MaterialTheme.typography.labelSmall
                        .copy(fontFeatureSettings = TABULAR_FIGURES)
                        .userContent,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (topic.isPinned && topic.unreadCount == 0) {
                Icon(
                    painter = painterResource(UiR.drawable.ic_push_pin),
                    contentDescription = null,
                    modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (topic.unreadMentionCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("@") }
            }
            if (topic.unreadCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(topic.unreadCount.toString()) }
            }
        }
    }
}

private const val TABULAR_FIGURES = "tnum"
