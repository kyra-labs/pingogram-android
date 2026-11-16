package dev.kyralabs.pingogram.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
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

// The Conversation widget's states without a chat to show ([ConversationWidgetContent]).

/** Its [icon] in a tonal circle over [title] (and [subtitle]), centred; a tap runs [action]. */
@Composable
internal fun ColumnScope.Notice(
    icon: Int,
    title: String,
    action: Action,
    subtitle: String? = null,
) {
    val colors = GlanceTheme.colors
    val description = listOfNotNull(title, subtitle).joinToString(". ")
    Column(
        modifier =
            GlanceModifier
                .defaultWeight()
                .fillMaxWidth()
                .clickable(action)
                .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            GlanceModifier
                .size(ConversationTokens.noticeBadge)
                .background(
                    ImageProvider(R.drawable.widget_circle),
                    colorFilter = ColorFilter.tint(colors.secondaryContainer),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                ImageProvider(icon),
                contentDescription = null,
                modifier = GlanceModifier.size(ConversationTokens.noticeIcon),
                colorFilter = ColorFilter.tint(colors.onSecondaryContainer),
            )
        }
        Spacer(GlanceModifier.height(ConversationTokens.gap))
        Text(
            text = title,
            style =
                TextStyle(
                    color = colors.onSurface,
                    fontSize = ConversationTokens.title,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
            maxLines = 2,
        )
        subtitle?.let {
            Text(
                text = it,
                style =
                    TextStyle(
                        color = colors.onSurfaceVariant,
                        fontSize = ConversationTokens.body,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
            )
        }
    }
}

/** While the messages load: the header's and the messages' places as tonal shapes, no shimmer. */
@Composable
internal fun ColumnScope.Skeleton() {
    val tint = ColorFilter.tint(GlanceTheme.colors.surfaceVariant)
    val description = LocalContext.current.getString(R.string.widget_conversation_label)
    Column(GlanceModifier.defaultWeight().fillMaxWidth().semantics { contentDescription = description }) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                GlanceModifier
                    .size(ConversationTokens.avatar)
                    .background(ImageProvider(R.drawable.widget_circle), colorFilter = tint),
            ) {}
            Spacer(GlanceModifier.width(ConversationTokens.gap))
            Bar(ConversationTokens.titleBar, tint)
        }
        repeat(SKELETON_LINES) {
            Spacer(GlanceModifier.height(ConversationTokens.gap))
            Bar(ConversationTokens.lineBar, tint)
        }
    }
}

@Composable
private fun Bar(
    width: Dp,
    tint: ColorFilter,
) {
    Box(
        GlanceModifier
            .width(width)
            .height(ConversationTokens.bar)
            .background(ImageProvider(R.drawable.widget_pill), colorFilter = tint),
    ) {}
}

/** Opens the app where it was; a widget has no other way to say "open the app" when it shows no chat. */
internal fun openApp(context: Context): Action =
    actionStartActivity(
        context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java),
    )

private const val SKELETON_LINES = 3
