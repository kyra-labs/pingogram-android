package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R

/**
 * Under the title: on the [expanded] row the connection pill while not connected, else the status line while
 * something is unread; on the collapsed row only the connection's state, in one line. [summary] is read here alone.
 */
@Composable
internal fun HeaderSubtitle(
    connection: ConnectionStatus,
    summary: () -> ChatListSummary,
    expanded: Boolean,
) {
    val notConnected = connection != ConnectionStatus.Ready
    when {
        expanded -> {
            val motionScheme = MaterialTheme.motionScheme
            // One slot for both, so the pill and the status line take each other's place.
            AnimatedContent(
                targetState = notConnected,
                transitionSpec = {
                    fadeIn(motionScheme.defaultEffectsSpec()) togetherWith fadeOut(motionScheme.fastEffectsSpec())
                },
                label = "headerSubtitle",
            ) { pill ->
                if (pill) ConnectionPill(connection) else StatusLine(summary())
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
 * nothing is unread.
 */
@Composable
private fun StatusLine(summary: ChatListSummary) {
    if (!summary.hasStatus) return
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
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}
