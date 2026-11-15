package dev.kyralabs.pingogram.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.kyralabs.pingogram.MainActivity
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.text.spokenList
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The unread counter (board 33), 2 × 1 cells: on Glance's widget background with the launcher's corners, the unread
 * messages in a large number over "unread · 3 mentions", and the avatars of the first three unread unmuted chats at its
 * end; with nothing unread, "All read" beside a tick on a nine-lobed cookie in `primaryContainer`. A tap opens the
 * chat list (owner decision 2026-11-15: an empty counter too). Screen readers hear one description, "47 unread
 * messages, 3 mentions". Signed out or locked, a short line; a passcode keeps the avatars off the home screen.
 */
@Composable
internal fun UnreadCounterContent(content: RecentChatsContent) {
    val context = LocalContext.current
    val description = counterDescription(context, content)
    Scaffold(
        modifier =
            GlanceModifier
                .clickable(actionStartActivity(openChats(context)))
                .semantics { contentDescription = description },
        horizontalPadding = CounterTokens.padding,
    ) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
            when (content) {
                RecentChatsContent.Loading -> CircularProgressIndicator(color = GlanceTheme.colors.primary)
                RecentChatsContent.SignedOut -> Line(context.getString(R.string.widget_unread_signed_out))
                RecentChatsContent.Locked -> Line(context.getString(R.string.widget_unread_locked))
                is RecentChatsContent.Chats -> Counts(content)
            }
        }
    }
}

@Composable
private fun Counts(content: RecentChatsContent.Chats) {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    if (content.unreadMessages == 0 && content.mentions == 0) {
        AllRead()
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = content.unreadMessages.toString(),
                style =
                    TextStyle(color = colors.onSurface, fontSize = CounterTokens.number, fontWeight = FontWeight.Bold),
                maxLines = 1,
            )
            Text(
                text =
                    listOfNotNull(
                        context.getString(R.string.widget_unread_unread),
                        content.mentions.takeIf { it > 0 }?.let {
                            context.resources.getQuantityString(R.plurals.widget_unread_mentions, it, it)
                        },
                    ).joinToString(" · "),
                style = TextStyle(color = colors.onSurfaceVariant, fontSize = CounterTokens.label),
                maxLines = 2,
            )
        }
        content.chats
            .filter { it.unreadCount > 0 && !it.isMuted && it.avatar != null }
            .take(MAX_AVATARS)
            .forEach { chat ->
                Spacer(GlanceModifier.width(CounterTokens.avatarGap))
                Image(
                    ImageProvider(checkNotNull(chat.avatar)),
                    contentDescription = null,
                    modifier = GlanceModifier.size(CounterTokens.avatar),
                )
            }
    }
}

/** "All read" beside a tick on the nine-lobed cookie. */
@Composable
private fun AllRead() {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier
                .size(CounterTokens.badge)
                .background(
                    ImageProvider(R.drawable.widget_cookie9),
                    colorFilter = ColorFilter.tint(colors.primaryContainer),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(UiR.drawable.ic_check),
                contentDescription = null,
                modifier = GlanceModifier.size(CounterTokens.badgeIcon),
                colorFilter = ColorFilter.tint(colors.onPrimaryContainer),
            )
        }
        Text(
            text = context.getString(R.string.widget_unread_all_read),
            modifier = GlanceModifier.padding(start = CounterTokens.avatarGap),
            style = TextStyle(color = colors.onSurface, fontSize = CounterTokens.title, fontWeight = FontWeight.Medium),
            maxLines = 1,
        )
    }
}

@Composable
private fun Line(text: String) {
    Text(
        text = text,
        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = CounterTokens.label),
        maxLines = 2,
    )
}

/** What screen readers hear for the whole widget. */
private fun counterDescription(
    context: Context,
    content: RecentChatsContent,
): String =
    when (content) {
        RecentChatsContent.Loading -> {
            context.getString(R.string.widget_unread_label)
        }

        RecentChatsContent.SignedOut -> {
            context.getString(R.string.widget_unread_signed_out)
        }

        RecentChatsContent.Locked -> {
            context.getString(R.string.widget_unread_locked)
        }

        is RecentChatsContent.Chats -> {
            if (content.unreadMessages == 0 && content.mentions == 0) {
                context.getString(R.string.widget_unread_all_read)
            } else {
                spokenList(
                    context.resources,
                    listOfNotNull(
                        context.resources.getQuantityString(
                            R.plurals.widget_unread_messages,
                            content.unreadMessages,
                            content.unreadMessages,
                        ),
                        content.mentions.takeIf { it > 0 }?.let {
                            context.resources.getQuantityString(R.plurals.widget_unread_mentions, it, it)
                        },
                    ),
                )
            }
        }
    }

/** The chat list: the app's launch intent, which opens it where it was. */
private fun openChats(context: Context): Intent =
    context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?: Intent(context, MainActivity::class.java)

private const val MAX_AVATARS = 3

/** The counter's sizes: Material 3 tokens in the units RemoteViews take, as [WidgetTokens]. */
internal object CounterTokens {
    /** Inside the widget: `spacing.large`. */
    val padding = 16.dp

    /** The avatars of unread chats: 24 dp (`sizes.avatarInline`), `spacing.small` apart. */
    val avatar = 24.dp
    val avatarGap = 8.dp

    /** The "All read" cookie and its tick: 40 dp (`sizes.leadingIconContainer`) and 24 dp. */
    val badge = 40.dp
    val badgeIcon = 24.dp

    /** `displaySmall`, `titleMedium` and `bodySmall` of the type scale. */
    val number = 36.sp
    val title = 16.sp
    val label = 12.sp
}
