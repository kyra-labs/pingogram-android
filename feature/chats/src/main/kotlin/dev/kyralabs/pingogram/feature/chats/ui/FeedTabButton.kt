package dev.kyralabs.pingogram.feature.chats.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Channel feed's tab, first in the folder row (`docs/DESIGN_SYSTEM.md` §6 "Channel feed", frame 1a): the
 * `dynamic_feed` symbol, "Feed" and the unread posts' count on `primaryContainer` in every state, so it reads as a view
 * of its own rather than a folder. It opens the feed ([onOpen]) and is never selected; screen readers hear "Feed, 8
 * unread posts from channels".
 */
@Composable
internal fun FeedTabButton(
    tab: FeedTab,
    onOpen: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val description = feedTabDescription(tab)
    Button(
        onClick = onOpen,
        modifier =
            modifier.clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick {
                        onOpen()
                        true
                    }
                } else {
                    disabled()
                }
            },
        enabled = enabled,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer,
            ),
        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight),
    ) {
        Icon(
            painterResource(UiR.drawable.ic_dynamic_feed),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
        Text(
            stringResource(R.string.chat_list_feed_tab),
            maxLines = 1,
            style = MaterialTheme.typography.labelLargeEmphasized,
        )
        if (tab.unreadCount > 0) {
            Badge(
                modifier = Modifier.padding(start = MaterialTheme.spacing.small),
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
            ) { Text(stringResource(R.string.chat_list_unread_count, tab.unreadCount)) }
        }
    }
}

/** "Feed, 8 unread posts from channels", or "Feed" with nothing unread. */
@Composable
private fun feedTabDescription(tab: FeedTab): String {
    val label = stringResource(R.string.chat_list_feed_tab)
    if (tab.unreadCount == 0) return label
    val count = tab.unreadCount
    val unread = LocalResources.current.getQuantityString(R.plurals.chat_list_a11y_unread_posts, count, count)
    return stringResource(R.string.chat_list_a11y_tab, label, unread)
}

@PingogramPreviews
@Composable
private fun FeedTabButtonPreview() {
    PingogramPreview { FeedTabButton(FeedTab(unreadCount = 8, channelCount = 14), onOpen = {}, enabled = true) }
}
