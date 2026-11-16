package dev.kyralabs.pingogram.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import dev.kyralabs.pingogram.MainActivity
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.notifications.NotificationIntents
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Conversation widget (board 33), 4 × 2 or 2 × 2 cells, on Glance's widget background with the launcher's corners:
 * the chat's avatar, its title and its unread count, then its newest messages (three at 4 × 2, two at 2 × 2 and one
 * there from 150% font), each on one line after its sender (under it at 2 × 2), and "Reply…" with a reply button. The
 * chat and its messages are one item for screen readers, which opens the chat; the reply is a button of its own. Its
 * other states: a tonal outline while the messages load, "Choose a chat" ([onSetUp]) for a widget that keeps none yet,
 * "Sign in to Pingogram first", that the app is locked, and "Can't update right now · Tap to retry" ([onRetry]).
 */
@Composable
internal fun ConversationWidgetContent(
    content: ConversationContent,
    onSetUp: Action,
    onRetry: Action,
) {
    val context = LocalContext.current
    Scaffold(horizontalPadding = ConversationTokens.padding) {
        Column(GlanceModifier.fillMaxSize().padding(vertical = ConversationTokens.padding)) {
            when (content) {
                ConversationContent.Loading -> {
                    Skeleton()
                }

                ConversationContent.SignedOut -> {
                    Notice(
                        icon = UiR.drawable.ic_pingogram,
                        title = context.getString(R.string.widget_conversation_sign_in),
                        action = openApp(context),
                    )
                }

                ConversationContent.Locked -> {
                    Notice(UiR.drawable.ic_lock, context.getString(R.string.widget_locked), action = openApp(context))
                }

                ConversationContent.NoChat -> {
                    Notice(UiR.drawable.ic_add, context.getString(R.string.widget_conversation_choose), onSetUp)
                }

                ConversationContent.Failed -> {
                    Notice(
                        icon = R.drawable.ic_sync_problem,
                        title = context.getString(R.string.widget_conversation_failed),
                        action = onRetry,
                        subtitle = context.getString(R.string.widget_conversation_retry),
                    )
                }

                is ConversationContent.Chat -> {
                    Conversation(content)
                }
            }
        }
    }
}

/** The chat's header and messages, one item that opens the chat, then the reply. */
@Composable
private fun ColumnScope.Conversation(chat: ConversationContent.Chat) {
    val context = LocalContext.current
    val open = openChat(context, chat)
    val wide = LocalSize.current.width >= ConversationTokens.wideWidth
    val shown = chat.messages.takeLast(messagesShown(context, wide))
    val description = conversationDescription(context, chat, shown)
    Column(
        GlanceModifier
            .defaultWeight()
            .fillMaxWidth()
            .clickable(open)
            .semantics { contentDescription = description },
    ) {
        Header(chat)
        Spacer(GlanceModifier.height(ConversationTokens.gap))
        when {
            chat.isSecret -> Line(context.getString(R.string.widget_secret_chat))
            shown.isEmpty() -> Line(context.getString(R.string.widget_conversation_empty))
            else -> shown.forEach { MessageLine(it, inline = wide) }
        }
    }
    if (chat.canReply) ReplyRow(chat, open)
}

/** The avatar, the title and the unread count, in the muted colors while the chat is muted. */
@Composable
private fun Header(chat: ConversationContent.Chat) {
    val colors = GlanceTheme.colors
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val avatar = chat.avatar
        val avatarModifier = GlanceModifier.size(ConversationTokens.avatar)
        if (avatar != null) {
            Image(ImageProvider(avatar), contentDescription = null, modifier = avatarModifier)
        } else {
            Box(
                avatarModifier.background(
                    ImageProvider(R.drawable.widget_circle),
                    colorFilter = ColorFilter.tint(colors.secondaryContainer),
                ),
            ) {}
        }
        Text(
            text = chat.title,
            modifier = GlanceModifier.defaultWeight().padding(horizontal = ConversationTokens.gap),
            style =
                TextStyle(color = colors.onSurface, fontSize = ConversationTokens.title, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (chat.unreadCount > 0) {
            Box(
                modifier =
                    GlanceModifier
                        .background(
                            ImageProvider(R.drawable.widget_pill),
                            colorFilter = ColorFilter.tint(if (chat.isMuted) colors.surfaceVariant else colors.primary),
                        ).padding(horizontal = WidgetTokens.badgePadding, vertical = WidgetTokens.badgeVerticalPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeCountOf(chat.unreadCount),
                    style =
                        TextStyle(
                            color = if (chat.isMuted) colors.onSurfaceVariant else colors.onPrimary,
                            fontSize = WidgetTokens.label,
                            fontWeight = FontWeight.Medium,
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * One message on one line: its sender in `primary` before its text where the widget is wide ([inline]), over it where
 * it is narrow.
 */
@Composable
private fun MessageLine(
    message: WidgetMessage,
    inline: Boolean,
) {
    val colors = GlanceTheme.colors
    val senderStyle =
        TextStyle(color = colors.primary, fontSize = ConversationTokens.label, fontWeight = FontWeight.Bold)
    val textStyle = TextStyle(color = colors.onSurface, fontSize = ConversationTokens.body)
    if (inline) {
        Row(
            GlanceModifier.fillMaxWidth().padding(bottom = ConversationTokens.messageGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            message.sender?.let { sender ->
                Text(sender, style = senderStyle, maxLines = 1)
                Spacer(GlanceModifier.width(ConversationTokens.gap))
            }
            Text(message.text, GlanceModifier.defaultWeight(), style = textStyle, maxLines = 1)
        }
    } else {
        Column(GlanceModifier.fillMaxWidth().padding(bottom = ConversationTokens.messageGap)) {
            message.sender?.let { Text(it, style = senderStyle, maxLines = 1) }
            Text(message.text, style = textStyle, maxLines = 1)
        }
    }
}

/** "Reply…" on a tonal pill beside the reply button: one button for screen readers, "Reply to Trip". */
@Composable
private fun ReplyRow(
    chat: ConversationContent.Chat,
    reply: Action,
) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val description = context.getString(R.string.widget_conversation_reply_to, chat.title)
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(reply).semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .defaultWeight()
                    .height(ConversationTokens.reply)
                    .background(
                        ImageProvider(R.drawable.widget_button),
                        colorFilter = ColorFilter.tint(colors.surfaceVariant),
                    ).padding(horizontal = ConversationTokens.padding),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = context.getString(R.string.widget_conversation_reply),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = ConversationTokens.body),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(ConversationTokens.gap))
        CircleIconButton(
            imageProvider = ImageProvider(UiR.drawable.ic_reply),
            contentDescription = null,
            onClick = reply,
            backgroundColor = colors.primary,
            contentColor = colors.onPrimary,
        )
    }
}

/** A short line in the widget's secondary color. */
@Composable
private fun Line(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = ConversationTokens.body),
        maxLines = MESSAGE_LINES,
    )
}

/** How many messages fit: three where wide (4 × 2), two where narrow (2 × 2), one there from 150% font. */
private fun messagesShown(
    context: Context,
    wide: Boolean,
): Int {
    val largeFont = context.resources.configuration.fontScale >= LARGE_FONT_SCALE
    return when {
        wide -> MAX_MESSAGES
        largeFont -> 1
        else -> MAX_MESSAGES - 1
    }
}

/** "Trip — Coorg 2026, 9 unread messages, Meera: Final plan…": the chat, its count and the messages shown. */
private fun conversationDescription(
    context: Context,
    chat: ConversationContent.Chat,
    shown: List<WidgetMessage>,
): String =
    spokenList(
        context.resources,
        listOf(chat.title) +
            listOfNotNull(
                context.getString(R.string.widget_muted).takeIf { chat.isMuted },
                chat.unreadCount.takeIf { it > 0 }?.let {
                    context.resources.getQuantityString(R.plurals.widget_unread_messages, it, it)
                },
                context.getString(R.string.widget_secret_chat).takeIf { chat.isSecret },
            ) +
            shown.map { message ->
                message.sender?.let { context.getString(R.string.widget_preview_sender, it, message.text) }
                    ?: message.text
            },
    )

/** Opens the chat in its account, or the app where it cannot. */
private fun openChat(
    context: Context,
    chat: ConversationContent.Chat,
): Action =
    NotificationIntents.openChat(context, chat.chatId, chat.slot)?.let { actionStartActivity(it) } ?: openApp(context)

/** The count as the chat list shows it: "999+" past 999. */
private fun badgeCountOf(count: Int): String = if (count > MAX_BADGE) "$MAX_BADGE+" else count.toString()

private const val MAX_BADGE = 999
private const val MESSAGE_LINES = 2
private const val LARGE_FONT_SCALE = 1.5f

/** The widget's sizes: Material 3 tokens in the units RemoteViews take, as [WidgetTokens]. */
internal object ConversationTokens {
    /** Inside the widget: `spacing.medium`; its parts `spacing.small` apart, messages `spacing.extraSmall`. */
    val padding = 12.dp
    val gap = 8.dp
    val messageGap = 4.dp

    /** The chat's avatar: 32 dp ([AVATAR_SIZE_DP]). */
    val avatar = AVATAR_SIZE_DP.dp

    /** The reply field: 40 dp tall, as the button beside it (widget_button's radius makes it a pill). */
    val reply = 40.dp

    /** The notices' badge and its icon: 48 dp and 24 dp. */
    val noticeBadge = 48.dp
    val noticeIcon = 24.dp

    /** The loading outline's bars: 12 dp tall, the title's and a line's widths. */
    val bar = 12.dp
    val titleBar = 120.dp
    val lineBar = 180.dp

    /** From this width the widget is 4 × 2 and shows three messages. */
    val wideWidth = 250.dp

    /** `titleMedium`, `bodyMedium` and `labelMedium` of the type scale. */
    val title = 16.sp
    val body = 14.sp
    val label = 12.sp

    /** The avatar's size in dp, which the updater draws its bitmap at. */
    const val AVATAR_SIZE_DP = 32f
}
