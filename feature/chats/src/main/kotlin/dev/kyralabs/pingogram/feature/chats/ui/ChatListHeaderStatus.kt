package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.component.StateCrossfade
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.catchup.CatchUpChip
import dev.kyralabs.pingogram.feature.chats.ui.focus.ChatListFocus
import dev.kyralabs.pingogram.feature.chats.ui.focus.FocusPillChip
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Under the title: on the [expanded] row the connection pill while not connected, else the status line while
 * something is unread, and below them the Focus pill while a profile is on ([focus]); on the collapsed row only the
 * connection's state, in one line. [summary] is read here alone. While the collapsed bar hides the expanded row
 * ([expandedRowShows] false), the pill there keeps its place but its indicator stands still.
 */
@Composable
internal fun HeaderSubtitle(
    connection: ConnectionStatus,
    summary: () -> ChatListSummary,
    expanded: Boolean,
    expandedRowShows: Boolean = true,
    focus: ChatListFocus? = null,
    onCatchUp: () -> Unit = {},
) {
    val notConnected = connection != ConnectionStatus.Ready
    when {
        expanded -> {
            Column {
                // One slot for both, so the pill and the status line take each other's place, its size on the scheme.
                StateCrossfade(targetState = notConnected, label = "headerSubtitle") { pill ->
                    if (pill) {
                        ConnectionPill(
                            connection,
                            animated = expandedRowShows,
                        )
                    } else {
                        StatusLine(summary(), onCatchUp)
                    }
                }
                if (focus != null) FocusPillSlot(focus)
            }
        }

        notConnected -> {
            Text(
                text = stringResource(connection.labelRes()),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * The status line (owner decision 2026-11-06): the main list's unread chats in `primary` and the emphasized weight,
 * then "· 3 mentions" in `onSurfaceVariant`, in `bodyMedium`; one text, so screen readers read it once. Nothing while
 * nothing is unread. From five chats to catch up on, the Catch-up chip ([CatchUpChip], owner decision 2026-11-15)
 * follows it, [onCatchUp] opening Catch-up.
 */
@Composable
private fun StatusLine(
    summary: ChatListSummary,
    onCatchUp: () -> Unit,
) {
    if (!summary.hasStatus && !summary.offersCatchUp) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The chip keeps its size; the counts give way to it at large font scales.
        if (summary.hasStatus) StatusText(summary, Modifier.weight(1f, fill = false))
        if (summary.offersCatchUp) CatchUpChip(summary.catchUpChats, onCatchUp)
    }
}

@Composable
private fun StatusText(
    summary: ChatListSummary,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val colors = MaterialTheme.colorScheme
    val emphasized = MaterialTheme.typography.bodyMediumEmphasized.fontWeight
    val text =
        remember(summary.unreadChats, summary.mentionChats, resources, colors.primary, emphasized) {
            val unread =
                summary.unreadChats.takeIf { it > 0 }?.let {
                    resources.getQuantityString(R.plurals.chat_list_status_unread, it, it)
                }
            val mentions =
                summary.mentionChats.takeIf { it > 0 }?.let {
                    resources.getQuantityString(R.plurals.chat_list_status_mentions, it, it)
                }
            buildAnnotatedString {
                val line =
                    if (unread != null && mentions != null) {
                        resources.getString(R.string.chat_list_status, unread, mentions)
                    } else {
                        unread ?: mentions.orEmpty()
                    }
                append(line)
                if (unread != null) {
                    val start = line.indexOf(unread)
                    if (start >= 0) {
                        addStyle(
                            SpanStyle(color = colors.primary, fontWeight = emphasized),
                            start,
                            start + unread.length,
                        )
                    }
                }
            }
        }
    Text(
        text = text,
        modifier = modifier,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}

/**
 * The Focus pill below the status line while a profile is on (`docs/DESIGN_SYSTEM.md` §13.13, board 31), growing in
 * and shrinking away on the scheme's springs; [focus]'s pill is read here alone.
 */
@Composable
private fun FocusPillSlot(focus: ChatListFocus) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = focus.pill(),
        transitionSpec = {
            fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        label = "focusPill",
    ) { pill ->
        if (pill != null) {
            val density = LocalDensity.current
            FocusPillChip(
                pill,
                onOpen = focus.onOpen,
                modifier = Modifier.onSizeChanged { focus.pillHeight = with(density) { it.height.toDp() } },
            )
        }
    }
}

/**
 * Drafts (`docs/DESIGN_SYSTEM.md` §6 "Drafts"): a [TopBarAction] with the description symbol, which opens the page of
 * unsent messages, shown only while a chat has a draft ([summary]'s, read here alone so its flips recompose the button
 * and nothing else).
 */
@Composable
internal fun HeaderDraftsButton(
    summary: () -> ChatListSummary,
    onClick: () -> Unit,
) {
    if (!summary().hasDrafts) return
    TopBarAction(label = stringResource(R.string.drafts_title), onClick = onClick) {
        Icon(painterResource(UiR.drawable.ic_description), contentDescription = null)
    }
}

/**
 * Mentions & replies (`docs/DESIGN_SYSTEM.md` §6): a [TopBarAction] with the alternate email symbol, which opens the
 * page, and how many chats have unread mentions ([summary]'s) in a `primary` badge. Screen readers hear "Mentions &
 * replies, 3 chats with unread mentions".
 */
@Composable
internal fun HeaderMentionsButton(
    summary: () -> ChatListSummary,
    onClick: () -> Unit,
) {
    val label = stringResource(R.string.mentions_title)
    val resources = LocalResources.current
    TopBarAction(
        label = label,
        onClick = onClick,
        description = {
            val count = summary().mentionChats
            if (count > 0) {
                val unread = resources.getQuantityString(R.plurals.mentions_a11y_unread_chats, count, count)
                resources.getString(R.string.chat_list_a11y_tab, label, unread)
            } else {
                label
            }
        },
    ) {
        BadgedBox(
            badge = {
                // Read here alone, so a count changing recomposes the badge and nothing else.
                val count = summary().mentionChats
                if (count > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ) { Text(unreadCountText(count)) }
                }
            },
        ) { Icon(painterResource(UiR.drawable.ic_alternate_email), contentDescription = null) }
    }
}
