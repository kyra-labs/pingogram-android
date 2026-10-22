package dev.kyralabs.pingogram.feature.chat.ui

import android.content.res.Resources
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The link from a message to its thread (REQUIREMENTS.md FR-C9): a channel post's comments, a group message's replies.

/**
 * "12 comments" ("Leave a comment" before the first) or "3 replies" under a bubble, on its side: an extra-small
 * `FilledTonalButton` (`secondaryContainer`) with the comment icon and its press morph, which opens the thread of
 * [link]'s message through [onOpen]. It keeps the 48 dp touch target. Screen readers reach it as the bubble's custom
 * action ([threadAction]) and hear its text in the bubble's description, as the bubble is read as one.
 */
@Composable
internal fun ThreadButton(
    link: ThreadLink,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.ExtraSmallContainerHeight
    FilledTonalButton(
        onClick = onOpen,
        shapes = ButtonDefaults.shapesFor(height),
        modifier = modifier.heightIn(min = height),
        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_mode_comment),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(text = threadLinkText(LocalResources.current, link), style = ButtonDefaults.textStyleFor(height))
    }
}

/** "12 comments", "Leave a comment" or "3 replies". */
internal fun threadLinkText(
    resources: Resources,
    link: ThreadLink,
): String =
    when {
        !link.isComments -> resources.getQuantityString(R.plurals.thread_replies, link.count, link.count)
        link.count == 0 -> resources.getString(R.string.thread_comments_none)
        else -> resources.getQuantityString(R.plurals.thread_comments, link.count, link.count)
    }

/** The screen reader's action that opens [bubble]'s thread, where it has a link to one. */
internal fun threadAction(bubble: ChatItem.Bubble): ContentAction? =
    bubble.thread?.let { link ->
        ContentAction(
            if (link.isComments) R.string.thread_open_comments else R.string.thread_open_replies,
            ChatEvent.OpenThread(link.messageId),
        )
    }

/**
 * The link to this message's thread: a channel post's comments, even before the first (TDLib gives a channel post a
 * reply count only where the channel has a discussion group), or the replies to a group's message once there are any.
 */
internal fun Message.threadLink(kind: ChatKind): ThreadLink? {
    val count = interactionInfo?.replyCount ?: return null
    val isComments = kind == ChatKind.Channel
    return ThreadLink(id, count, isComments).takeIf { isComments || count > 0 }
}
