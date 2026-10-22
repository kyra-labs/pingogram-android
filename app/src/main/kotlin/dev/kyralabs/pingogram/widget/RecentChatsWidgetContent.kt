package dev.kyralabs.pingogram.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.components.TitleBar
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
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
 * The widget of recent chats (`docs/DESIGN_SYSTEM.md` §6 "Home screen widget"): Glance's title bar with the Pingogram
 * mark, "Chats" and a "New chat" button; then the chats as rows (avatar, title, one line of the last message, unread
 * badge) that open their chat, or a short message that opens the app: signed out, locked, no chats. Glance's own
 * components in the theme's roles; the launcher's font, since widgets cannot load the app's.
 */
@Composable
internal fun RecentChatsWidgetContent(content: RecentChatsContent) {
    val context = LocalContext.current
    Scaffold(
        titleBar = {
            TitleBar(
                startIcon = ImageProvider(UiR.drawable.ic_pingogram),
                title = context.getString(R.string.widget_title),
                actions = {
                    // Inset from the widget's rounded corner, which would clip the circle.
                    Box(
                        GlanceModifier.padding(
                            top = WidgetTokens.titleActionInset,
                            end = WidgetTokens.titleActionInset,
                        ),
                    ) {
                        CircleIconButton(
                            imageProvider = ImageProvider(UiR.drawable.ic_edit),
                            contentDescription = context.getString(R.string.widget_new_chat),
                            onClick = actionStartActivity(newChatIntent(context)),
                            backgroundColor = GlanceTheme.colors.primaryContainer,
                            contentColor = GlanceTheme.colors.onPrimaryContainer,
                        )
                    }
                },
            )
        },
    ) {
        when (content) {
            RecentChatsContent.Loading -> {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = GlanceTheme.colors.primary)
                }
            }

            RecentChatsContent.SignedOut -> {
                Notice(context.getString(R.string.widget_signed_out))
            }

            RecentChatsContent.Locked -> {
                Notice(context.getString(R.string.widget_locked))
            }

            is RecentChatsContent.Chats -> {
                if (content.chats.isEmpty()) {
                    Notice(context.getString(R.string.widget_no_chats))
                } else {
                    ChatRows(content.chats)
                }
            }
        }
    }
}

@Composable
private fun ChatRows(chats: List<WidgetChat>) {
    LazyColumn(GlanceModifier.fillMaxSize()) {
        items(chats, itemId = { it.id }) { chat -> ChatRow(chat) }
    }
}

/** One chat: its avatar, title and last message, and its unread count; one item for screen readers. */
@Composable
private fun ChatRow(chat: WidgetChat) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    val unread = chat.unreadCount > 0
    val description = rowDescription(context, chat)
    val open = NotificationIntents.openChat(context, chat.id)?.let { actionStartActivity(it) } ?: openApp(context)
    Row(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .padding(vertical = WidgetTokens.rowPadding)
                .clickable(open)
                .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val avatar = chat.avatar
        if (avatar != null) {
            Image(ImageProvider(avatar), contentDescription = null, modifier = GlanceModifier.size(WidgetTokens.avatar))
        } else {
            Box(
                GlanceModifier
                    .size(WidgetTokens.avatar)
                    .background(
                        ImageProvider(R.drawable.widget_circle),
                        colorFilter = ColorFilter.tint(colors.secondaryContainer),
                    ),
            ) {}
        }
        Column(GlanceModifier.defaultWeight().padding(horizontal = WidgetTokens.rowGap)) {
            Text(
                text = chat.title,
                style =
                    TextStyle(
                        color = colors.onSurface,
                        fontSize = WidgetTokens.title,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Medium,
                    ),
                maxLines = 1,
            )
            Text(
                text = chat.preview,
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = WidgetTokens.body),
                maxLines = 1,
            )
        }
        if (unread || chat.hasUnreadMention) UnreadBadge(chat)
    }
}

/** The unread count ("@" for a mention) on `primary`, `surfaceVariant` while the chat is muted, as in the chat list. */
@Composable
private fun UnreadBadge(chat: WidgetChat) {
    val colors = GlanceTheme.colors
    Box(
        modifier =
            GlanceModifier
                .height(WidgetTokens.badge)
                .background(
                    ImageProvider(R.drawable.widget_pill),
                    colorFilter = ColorFilter.tint(if (chat.isMuted) colors.surfaceVariant else colors.primary),
                ).padding(horizontal = WidgetTokens.badgePadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (chat.hasUnreadMention) "@" else badgeCount(chat.unreadCount),
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

/** A short message in the middle of the widget; a tap opens the app. */
@Composable
private fun Notice(text: String) {
    val context = LocalContext.current
    Box(
        GlanceModifier.fillMaxSize().clickable(openApp(context)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style =
                TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = WidgetTokens.body,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

/** "Alice, 3 unread messages, Hi there": the title, the unread count and the preview. */
private fun rowDescription(
    context: Context,
    chat: WidgetChat,
): String =
    spokenList(
        context.resources,
        listOf(
            chat.title,
            chat.unreadCount.takeIf { it > 0 }?.let {
                context.resources.getQuantityString(R.plurals.widget_unread_messages, it, it)
            },
            chat.preview,
        ),
    )

/** The count as the chat list shows it: "999+" past [MAX_BADGE_COUNT]. */
private fun badgeCount(count: Int): String = if (count > MAX_BADGE_COUNT) "$MAX_BADGE_COUNT+" else count.toString()

private const val MAX_BADGE_COUNT = 999

/** Opens the app where it was; a widget has no other way to say "open the app" when it shows no chat. */
private fun openApp(context: Context): Action =
    actionStartActivity(
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java),
    )

/** Opens New chat as the app shortcut does: the Telegram link `tg://new`, through the app's link handling. */
private fun newChatIntent(context: Context): Intent =
    Intent(Intent.ACTION_VIEW, NEW_CHAT_LINK.toUri(), context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

private const val NEW_CHAT_LINK = "tg://new"

/**
 * The widget's sizes. Glance has no Material theme to read them from: they are the Material 3 tokens the app's chat
 * list uses (`docs/DESIGN_SYSTEM.md` §3.3, §7), in the units RemoteViews take.
 */
internal object WidgetTokens {
    /** Avatars: 40 dp, Material's list item avatar ([RecentChatsWidgetUpdater.AVATAR_SIZE_DP]). */
    val avatar = RecentChatsWidgetUpdater.AVATAR_SIZE_DP.dp

    /** Space above and below a row, and between its avatar, text and badge: `spacing.small`, `spacing.medium`. */
    val rowPadding = 8.dp
    val rowGap = 12.dp

    /** How far the title bar's button stays from the widget's top and end edges: `spacing.small`. */
    val titleActionInset = 8.dp

    /**
     * The unread badge: Material's large badge, 16 dp tall, here 20 dp for the launcher's larger text; its pill's
     * radius is `R.dimen.widget_pill_radius`.
     */
    val badge = 20.dp
    val badgePadding = 6.dp

    /** `titleMedium`, `bodyMedium` and `labelSmall` of the type scale. */
    val title = 16.sp
    val body = 14.sp
    val label = 11.sp
}
