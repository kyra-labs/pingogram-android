package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A topic's icon in a circle as large as the chat list's avatars (`sizes.avatarRow`): the General topic's "#" and the
 * other topics' bubble, in the tonal pair of the topic's color ([topicTone]), never Telegram's own colors; in
 * `primary` [onSelection], on a row in its selected colors.
 */
@Composable
internal fun TopicIconBadge(
    icon: TopicIcon,
    modifier: Modifier = Modifier,
    size: Dp = MaterialTheme.sizes.avatarRow,
    onSelection: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    // On a row in its selected colors (`secondaryContainer`), as avatar placeholders are there (§2.2).
    val (container, content) = if (onSelection) colors.primary to colors.onPrimary else topicTone(icon)
    Box(
        modifier = modifier.size(size).background(container, CircleShape),
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
internal fun topicTone(icon: TopicIcon): Pair<Color, Color> {
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
