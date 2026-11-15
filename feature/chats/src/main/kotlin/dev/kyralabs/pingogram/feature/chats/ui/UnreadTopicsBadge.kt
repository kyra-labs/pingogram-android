package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A count of topics (a forum's) or chats (a community's) with unread messages, rather than of messages (owner decision
 * 2026-11-15, `docs/DESIGN_SYSTEM.md` §6 "Chat list row"): Material's `Badge` with its sizes and the count in its
 * style, its container a speech bubble (a pill whose bottom start corner is `extraSmall`, mirrored right to left), the
 * topic symbol (`ic_mode_comment`, [sizes.badgeIcon]) before the number, so it does not read as a message count. In
 * [container] and [content]: `primary` / `onPrimary`, or the muted colors. Decorative: the row's description says it.
 */
@Composable
internal fun UnreadTopicsBadge(
    count: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Badge(
        modifier = modifier.background(container, speechBubbleShape()),
        containerColor = Color.Transparent,
        contentColor = content,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall / 2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_mode_comment),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.badgeIcon),
            )
            Text(unreadCountText(count))
        }
    }
}

/** A pill whose bottom start corner is the shape scale's `extraSmall`: the tail of a speech bubble. */
@Composable
private fun speechBubbleShape(): Shape {
    val shapes = MaterialTheme.shapes
    return shapes.extraLarge.copy(bottomStart = shapes.extraSmall.bottomStart)
}
