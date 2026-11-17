package dev.kyralabs.pingogram.feature.chats.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.core.ui.theme.topicHue
import kotlinx.collections.immutable.ImmutableList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Under the name: the preview, or for a forum its newest topics above it ([ForumRowPreview]), which a tap opens through
 * [onTopicClick].
 */
@Composable
internal fun RowSupporting(
    item: ChatListItemModel,
    preview: PreviewText,
    onTopicClick: ((topicId: Int) -> Unit)? = null,
) {
    val contentIcon = (item.preview as? ChatPreview.Message)?.contentIcon
    val topics = item.topics
    if (topics != null) {
        ForumRowPreview(topics, preview, contentIcon, onTopicClick)
    } else {
        val keyword = item.keyword?.takeIf { timeOnPreviewLine() }
        if (keyword == null) {
            ChatPreviewText(preview, contentIcon)
        } else {
            // At large font scales a keyword alert's word goes under the preview (board 30), not beside the title.
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall)) {
                ChatPreviewText(preview, contentIcon)
                KeywordChip(keyword, capped = false)
            }
        }
    }
}

/**
 * The two lines under a forum's name (`docs/DESIGN_SYSTEM.md` §6 "Chat list row", owner decision 2026-11-15, as
 * Telegram's apps show forums): its newest topics, the newest first, then the [preview] of its last message. The newest
 * topic and the preview share one tonal container (`surfaceContainerHighest`), the topic's tab above the preview's
 * pill, which ends in a chevron, so the message reads as that topic's; the other topics follow the tab, separated by
 * commas, those with unread messages in `onSurface` and the emphasized weight.
 *
 * One layout measures the three parts once, without intrinsics or subcomposition (`docs/PERFORMANCE.md` §4): the tab
 * takes at most [TAB_SHARE] of the width, the other topics what is left of its line, and the pill the whole width,
 * at least as wide as the tab. Screen readers hear the row's description instead.
 *
 * Where there is [onTopicClick], a tap on the newest topic's tab, or on another topic's name, calls it with that topic,
 * as Telegram's apps open the topic; a tap elsewhere (the preview, a comma, after the names) is the row's own
 * ([onTopicTap]). A long press is always the row's.
 */
@Composable
internal fun ForumRowPreview(
    topics: ImmutableList<RowTopic>,
    preview: PreviewText,
    @DrawableRes contentIcon: Int?,
    onTopicClick: ((topicId: Int) -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.userContent
    val emphasized = MaterialTheme.typography.bodyMediumEmphasized.fontWeight
    val others = topics.drop(1)
    val othersText =
        remember(others, colors.onSurface, emphasized) { othersText(others, colors.onSurface, emphasized) }
    val gap = MaterialTheme.spacing.extraSmall
    val newest = topics.first()
    // Read only by a tap, to find the name under it: never state, so laying the text out invalidates nothing.
    val othersLayout = remember { arrayOfNulls<TextLayoutResult>(1) }
    val newestTap = onTopicClick?.let { Modifier.onTopicTap(newest, it) { newest.id } } ?: Modifier
    // Before the padding, so the whole line's height takes the tap.
    val othersTap =
        onTopicClick?.let { Modifier.onTopicTap(othersText, it) { at -> othersLayout[0]?.topicAt(othersText, at.x) } }
            ?: Modifier
    Layout(
        modifier = Modifier.clearAndSetSemantics {},
        content = {
            NewestTopicTab(newest, style, emphasized, newestTap)
            Text(
                text = othersText,
                modifier = othersTap.padding(vertical = MaterialTheme.spacing.extraSmall),
                color = colors.onSurfaceVariant,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                style = style,
                onTextLayout = { othersLayout[0] = it },
            )
            PreviewPill(preview, contentIcon, style)
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val gapPx = gap.roundToPx()
        val tab = measurables[0].measure(Constraints(maxWidth = (width * TAB_SHARE).toInt()))
        val rest = measurables[1].measure(Constraints(maxWidth = (width - tab.width - gapPx).coerceAtLeast(0)))
        val pill = measurables[2].measure(Constraints(minWidth = tab.width.coerceAtMost(width), maxWidth = width))
        val line = maxOf(tab.height, rest.height)
        layout(width, line + pill.height) {
            // The tab sits on the pill, so the two read as one container.
            tab.placeRelative(0, line - tab.height)
            rest.placeRelative(tab.width + gapPx, line - rest.height)
            pill.placeRelative(0, line)
        }
    }
}

/** The newest topic's symbol and name, on a tab of the tonal container with its top corners rounded. */
@Composable
private fun NewestTopicTab(
    topic: RowTopic,
    style: TextStyle,
    emphasized: FontWeight?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val radius = MaterialTheme.shapes.small
    val shape = remember(radius) { radius.copy(bottomStart = ZeroCorner, bottomEnd = ZeroCorner) }
    Row(
        modifier =
            modifier
                .background(colors.surfaceContainerHighest, shape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TopicSymbol(topic.color)
        Text(
            text = topic.name,
            color = if (topic.isUnread) colors.onSurface else colors.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = if (topic.isUnread) style.copy(fontWeight = emphasized) else style,
        )
    }
}

/**
 * The preview on a pill of the tonal container, its top start corner square where the tab joins it, ending in a
 * chevron.
 */
@Composable
private fun PreviewPill(
    preview: PreviewText,
    @DrawableRes contentIcon: Int?,
    style: TextStyle,
) {
    val colors = MaterialTheme.colorScheme
    val radius = MaterialTheme.shapes.small
    val shape = remember(radius) { radius.copy(topStart = ZeroCorner) }
    Row(
        modifier =
            Modifier
                .background(colors.surfaceContainerHighest, shape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (contentIcon != null) {
            InlineIcon(contentIcon, colors.onSurfaceVariant, MaterialTheme.sizes.inlineIconSmall)
        }
        Text(
            text = preview.styled,
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onSurfaceVariant,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            style = style,
        )
        InlineIcon(UiR.drawable.ic_chevron_right, colors.onSurfaceVariant, MaterialTheme.sizes.inlineIconSmall)
    }
}

/** A topic's symbol on a surface: General's hash, the others' speech bubble in the ink of their sender hue (§2.2). */
@Composable
private fun TopicSymbol(color: TopicColor?) {
    val tint = if (color == null) MaterialTheme.colorScheme.onSurfaceVariant else topicHue(color).ink
    val icon = if (color == null) UiR.drawable.ic_tag else UiR.drawable.ic_mode_comment_filled
    InlineIcon(icon, tint, MaterialTheme.sizes.inlineIconSmall)
}

/**
 * Calls [onTopic] with the topic [topicAt] finds where a tap lands (null: none there), once the pointer is released
 * within the touch slop and before the long-press timeout, and consumes the release, so the row it is in takes no tap.
 * It consumes nothing else: the row still shows its press, a long press stays the row's, and a scroll the list's.
 * Restarts when [key] changes.
 */
private fun Modifier.onTopicTap(
    key: Any,
    onTopic: (topicId: Int) -> Unit,
    topicAt: (Offset) -> Int?,
): Modifier =
    pointerInput(key, onTopic) {
        awaitEachGesture {
            val topic = topicAt(awaitFirstDown().position) ?: return@awaitEachGesture
            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
            if (up != null) {
                up.consume()
                onTopic(topic)
            }
        }
    }

/**
 * The topic whose name in [text], laid out as this one line, is at [x]: the character there or the one before it, so
 * a comma or a space goes to a name beside it; null past the line's ends.
 */
private fun TextLayoutResult.topicAt(
    text: AnnotatedString,
    x: Float,
): Int? {
    if (x < getLineLeft(0) || x > getLineRight(0)) return null
    val offset = getOffsetForPosition(Offset(x, size.height / 2f))
    return text
        .getStringAnnotations(TOPIC_TAG, (offset - 1).coerceAtLeast(0), offset + 1)
        .firstOrNull()
        ?.item
        ?.toIntOrNull()
}

/** "Phone reports, General, Off topic", the topics with unread messages in [unreadColor] and [unreadWeight]. */
private fun othersText(
    topics: List<RowTopic>,
    unreadColor: Color,
    unreadWeight: FontWeight?,
): AnnotatedString =
    buildAnnotatedString {
        topics.forEachIndexed { index, topic ->
            if (index > 0) append(TOPIC_SEPARATOR)
            // Each name says which topic it is, for a tap ([topicAt]).
            pushStringAnnotation(TOPIC_TAG, topic.id.toString())
            if (topic.isUnread) {
                withStyle(SpanStyle(color = unreadColor, fontWeight = unreadWeight)) { append(topic.name) }
            } else {
                append(topic.name)
            }
            pop()
        }
    }

/** The most of the width the newest topic's tab takes, leaving the rest of its line to the others. */
private const val TAB_SHARE = 0.6f

private const val TOPIC_SEPARATOR = ", "

private const val TOPIC_TAG = "topic"

private val ZeroCorner = CornerSize(0.dp)
