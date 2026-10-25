package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.domain.model.TopicColor
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
 * with a lock when the topic is closed), one line of its last message, and at the end its time over the pin, the
 * mention badge and the unread count. Screen readers hear it as one: name, state, message, time, counts.
 */
@Composable
internal fun TopicRowItem(
    topic: TopicRow,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val insets = sheetRowInsets()
    val resources = LocalResources.current
    val preview = topic.preview?.let { senderPreview(topic.sender, it) }
    val unread = topic.unreadCount > 0
    ListItem(
        onClick = onClick,
        modifier =
            modifier.padding(insets).semantics(mergeDescendants = true) {
                contentDescription =
                    spokenList(
                        resources,
                        listOf(
                            topic.name,
                            resources.getString(R.string.topic_closed).takeIf { topic.isClosed },
                            resources.getString(R.string.topic_pinned).takeIf { topic.isPinned },
                            preview?.text,
                            topic.time,
                            topic.unreadCount.takeIf { it > 0 }?.let {
                                resources.getQuantityString(R.plurals.topic_unread, it, it)
                            },
                            topic.unreadMentionCount.takeIf { it > 0 }?.let {
                                resources.getQuantityString(R.plurals.topic_mentions, it, it)
                            },
                        ),
                    )
            },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        contentPadding = insetContentPadding(insets),
        leadingContent = { TopicIconBadge(topic.icon) },
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
        trailingContent = { TopicMeta(topic) },
    ) {
        TopicName(
            topic,
            if (unread) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
        )
    }
}

/** The topic's name, after a lock when it is closed. */
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

/**
 * A topic's icon in a circle as large as the chat list's avatars (`sizes.avatarRow`): the General topic's "#" and the
 * other topics' bubble, in the tonal pair of the topic's color ([topicTone]), never Telegram's own colors.
 */
@Composable
internal fun TopicIconBadge(
    icon: TopicIcon,
    modifier: Modifier = Modifier,
) {
    val (container, content) = topicTone(icon)
    Box(
        modifier = modifier.size(MaterialTheme.sizes.avatarRow).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(if (icon.isGeneral) UiR.drawable.ic_tag else UiR.drawable.ic_mode_comment_filled),
            contentDescription = null,
            tint = content,
        )
    }
}

/**
 * The container and content colors of a topic's icon (`docs/DESIGN_SYSTEM.md` §2.2): Telegram's six topic colors by
 * the nearest of the theme's tonal pairs, blue `primaryContainer`, green and yellow `secondaryContainer`, violet, rose
 * and red `tertiaryContainer`; the General topic `surfaceContainerHighest`, as Telegram shows it grey.
 */
@Composable
private fun topicTone(icon: TopicIcon): Pair<Color, Color> {
    val colors = MaterialTheme.colorScheme
    return when {
        icon.isGeneral -> {
            colors.surfaceContainerHighest to colors.onSurfaceVariant
        }

        icon.color == TopicColor.Blue -> {
            colors.primaryContainer to colors.onPrimaryContainer
        }

        icon.color == TopicColor.Green || icon.color == TopicColor.Yellow -> {
            colors.secondaryContainer to colors.onSecondaryContainer
        }

        else -> {
            colors.tertiaryContainer to colors.onTertiaryContainer
        }
    }
}

private const val TABULAR_FIGURES = "tnum"
