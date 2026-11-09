package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.BubbleColors
import dev.kyralabs.pingogram.core.ui.theme.chatStyle
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.senderNameColor
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

// The typing bubble (`docs/DESIGN_SYSTEM.md` §13.6).

/**
 * The typing bubble (§13.6), over the bottom of the messages: the typer's avatar, a small bubble in
 * their hue with the typing dots, and what the top bar says beside it ("Fadi is typing"), which screen readers hear
 * there; this is decorative. It grows in and shrinks away on the default spatial spring.
 */
@Composable
internal fun TypingBubbleRow(
    typing: TypingBubble?,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = typing != null,
        modifier = modifier,
        enter = expandVertically(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
        exit = shrinkVertically(motion.defaultSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
    ) {
        // The last typer stays while the bubble shrinks away: a plain holder, not state, as nothing recomposes on it.
        val last = remember { arrayOfNulls<TypingBubble>(1) }
        if (typing != null) last[0] = typing
        val current = last[0] ?: return@AnimatedVisibility
        val hue = MaterialTheme.pingogramColors.senderHues[current.hue]
        val spacing = MaterialTheme.spacing
        Row(
            modifier = Modifier.clearAndSetSemantics {}.padding(horizontal = spacing.medium, vertical = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(
                photo = current.photo,
                name = current.name.resolve(),
                id = current.userId,
                size = MaterialTheme.sizes.avatarExtraSmall,
            )
            Box(
                modifier =
                    Modifier
                        .background(hue.vivid.container, MaterialTheme.shapes.large)
                        .padding(horizontal = spacing.medium, vertical = spacing.small),
            ) {
                TypingDots(color = hue.vivid.content, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = current.text.resolve(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium.userContent,
            )
        }
    }
}

/**
 * These paddings with room for the typing bubble below the newest message while someone is
 * [typing] (§13.6): the avatar's height and the row's padding.
 */
@Composable
internal fun PaddingValues.withTypingRoom(typing: TypingBubble?): PaddingValues {
    if (typing == null) return this
    val direction = LocalLayoutDirection.current
    val room = MaterialTheme.sizes.avatarExtraSmall + MaterialTheme.spacing.medium
    return PaddingValues(
        start = calculateStartPadding(direction),
        top = calculateTopPadding(),
        end = calculateEndPadding(direction),
        bottom = calculateBottomPadding() + room,
    )
}

/** The typing bubble over the messages, above the composer ([contentPadding]'s bottom). */
@Composable
internal fun TypingRowSlot(
    typing: TypingBubble?,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    TypingBubbleRow(typing, modifier.padding(bottom = contentPadding.calculateBottomPadding()))
}
