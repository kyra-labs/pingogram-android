package dev.kyralabs.pingogram.feature.chats.ui.feed

import android.icu.text.CompactDecimalFormat
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.rememberFormattedTextStyles
import dev.kyralabs.pingogram.core.ui.text.toAnnotatedString
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.preview.FeedPostPreviews
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A post of the Channel feed (`docs/DESIGN_SYSTEM.md` §6 "Channel feed"): a card with the shape scale's `extraLarge`
 * corners, its channel's avatar (`Cookie6Sided`, `AvatarShape.Channel`), name and time, its text and media
 * ([FeedPostMedia]), reactions, views and "Open in channel" ([onOpen], as a tap on the card).
 *
 * Unread and read differ by three signals, never color alone: an unread post on `surfaceContainerHigh` with its name in
 * the emphasized weight and a `primary` dot before "New · 9:41 AM"; a read one on `surfaceContainerLow` with an
 * `outlineVariant` edge. The container and edge cross-fade on the effects spring as a post turns read, and nothing
 * moves. While its channel is open beside the feed ([selected], two panes), its edge is a `primary` outline
 * (`selectedCardOutline`), and screen readers hear it selected.
 */
@Composable
internal fun FeedPostCard(
    post: FeedPost,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onMore: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    // Read in the draw phase only, so the cross-fade redraws the card without recomposing it.
    val container =
        animateColorAsState(
            if (post.isUnread) colors.surfaceContainerHigh else colors.surfaceContainerLow,
            effects,
            label = "feedPostContainer",
        )
    val edge =
        animateColorAsState(
            when {
                selected -> colors.primary
                post.isUnread -> colors.outlineVariant.copy(alpha = 0f)
                else -> colors.outlineVariant
            },
            effects,
            label = "feedPostEdge",
        )
    val shape = MaterialTheme.shapes.extraLarge
    val edgeWidth = if (selected) MaterialTheme.sizes.selectedCardOutline else CardDefaults.outlinedCardBorder().width
    Card(
        onClick = onOpen,
        modifier =
            modifier.semantics { this.selected = selected }.drawWithCache {
                val stroke = edgeWidth.toPx()
                val fill = shape.createOutline(size, layoutDirection, this)
                // The edge lies inside the card, as a border's does: its outline is inset by half its width.
                val inset = shape.createOutline(Size(size.width - stroke, size.height - stroke), layoutDirection, this)
                onDrawBehind {
                    drawOutline(fill, container.value)
                    translate(stroke / 2, stroke / 2) { drawOutline(inset, edge.value, style = Stroke(stroke)) }
                }
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Column(
            modifier = Modifier.padding(MaterialTheme.spacing.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        ) {
            PostHeader(post, onMore)
            post.text?.let { text ->
                val styles = rememberFormattedTextStyles()
                val styled = remember(text, styles) { text.toAnnotatedString(styles) }
                Text(styled, style = MaterialTheme.typography.bodyLarge.userContent)
            }
            post.media?.let { FeedPostMedia(it) }
            PostFooter(post, onOpen)
        }
    }
}

/** The channel's avatar, its name (two lines at most) and the post's time, after the `primary` dot while unread. */
@Composable
private fun PostHeader(
    post: FeedPost,
    onMore: (() -> Unit)?,
) {
    val typography = MaterialTheme.typography
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            photo = post.channelPhoto,
            name = post.channelTitle,
            id = post.chatId,
            size = MaterialTheme.sizes.avatarRow,
            shape = AvatarShape.Channel,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = post.channelTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = (if (post.isUnread) typography.titleSmallEmphasized else typography.titleSmall).userContent,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (post.isUnread) {
                    Spacer(
                        Modifier
                            .size(MaterialTheme.sizes.recordingDot)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    )
                }
                Text(
                    text = if (post.isUnread) stringResource(R.string.feed_new_at, post.time) else post.time,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = typography.labelMedium,
                )
            }
        }
        if (onMore != null) {
            IconButton(onClick = onMore, shapes = IconButtonDefaults.shapes()) {
                Icon(painterResource(UiR.drawable.ic_more_vert), stringResource(R.string.feed_post_more))
            }
        }
    }
}

/** The reactions, wrapping to more rows, then the view count and "Open in channel". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PostFooter(
    post: FeedPost,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    if (post.reactions.isNotEmpty()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        ) {
            post.reactions.forEach { reaction ->
                Text(
                    text = "${reaction.emoji.orEmpty()} ${reaction.count}".trim(),
                    modifier =
                        Modifier
                            .background(
                                if (reaction.isChosen) colors.primaryContainer else colors.surfaceContainerHighest,
                                CircleShape,
                            ).padding(
                                horizontal = MaterialTheme.spacing.medium,
                                vertical = MaterialTheme.spacing.extraSmall,
                            ),
                    color = if (reaction.isChosen) colors.onPrimaryContainer else colors.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (post.viewCount > 0) {
            Icon(
                painterResource(UiR.drawable.ic_visibility),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
                tint = colors.onSurfaceVariant,
            )
            Text(
                text = compactCount(post.viewCount),
                modifier = Modifier.padding(start = MaterialTheme.spacing.extraSmall),
                color = colors.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpen) { Text(stringResource(R.string.feed_open_in_channel)) }
    }
}

/** [count] as "3.4K", in the user's language. */
@Composable
private fun compactCount(count: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    return remember(count, locale) {
        CompactDecimalFormat.getInstance(locale, CompactDecimalFormat.CompactStyle.SHORT).format(count)
    }
}

@PingogramPreviews
@Composable
private fun FeedPostCardPreview(
    @PreviewParameter(FeedPostPreviews::class) post: FeedPost,
) {
    PingogramPreview { FeedPostCard(post = post, onOpen = {}) }
}
