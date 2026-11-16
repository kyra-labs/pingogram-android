package dev.kyralabs.pingogram.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.Scaffold
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import dev.kyralabs.pingogram.MainActivity
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.share.ACTION_NEW_VOICE_MESSAGE
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Quick compose widget (board 33): buttons into the app, in the app's colors ([widgetColors]), drawn from no data,
 * so nothing redraws it. 1 × 1: "New", the whole widget in `primary`. 2 × 1 and wider: "Message" in `primary` beside
 * "Voice" in `secondaryContainer`, on the widget background.
 */
class QuickComposeWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val appearance =
            EntryPointAccessors
                .fromApplication(context, RecentChatsWidgetEntryPoint::class.java)
                .appearanceRepository()
                .appearance
        provideContent {
            val settings by appearance.collectAsState(initial = null)
            GlanceTheme(colors = widgetColors(settings)) { QuickComposeContent() }
        }
    }
}

/** The Quick compose widget's receiver: the system's widget updates, nothing else. */
class QuickComposeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickComposeWidget()
}

/**
 * The widget at its size: "New" where it is narrower than two buttons, else "Message" and "Voice". "New" and "Message"
 * open New chat as the app shortcut does (`tg://new`); "Voice" opens the share sheet's chat picker, whose chat opens
 * with a locked voice recording under way ([ACTION_NEW_VOICE_MESSAGE]). Each button is one item for screen readers,
 * "New message" or "New voice message".
 */
@Composable
internal fun QuickComposeContent() {
    val context = LocalContext.current
    val colors = GlanceTheme.colors
    if (LocalSize.current.width < ComposeTokens.twoButtonsWidth) {
        Scaffold(
            modifier =
                GlanceModifier
                    .clickable(actionStartActivity(newChatIntent(context)))
                    .semantics { contentDescription = context.getString(R.string.widget_compose_new_description) },
            backgroundColor = colors.primary,
            horizontalPadding = 0.dp,
        ) {
            ButtonContent(UiR.drawable.ic_edit, context.getString(R.string.widget_compose_new), colors.onPrimary)
        }
        return
    }
    Scaffold(horizontalPadding = ComposeTokens.padding) {
        Row(GlanceModifier.fillMaxSize().padding(vertical = ComposeTokens.padding)) {
            ComposeButton(
                icon = UiR.drawable.ic_edit,
                label = context.getString(R.string.widget_compose_message),
                description = context.getString(R.string.widget_compose_new_description),
                intent = newChatIntent(context),
                tone = Tone(colors.primary, colors.onPrimary),
            )
            Spacer(GlanceModifier.width(ComposeTokens.padding))
            ComposeButton(
                icon = UiR.drawable.ic_mic,
                label = context.getString(R.string.widget_compose_voice),
                description = context.getString(R.string.widget_compose_voice_description),
                intent = newVoiceMessageIntent(context),
                tone = Tone(colors.secondaryContainer, colors.onSecondaryContainer),
            )
        }
    }
}

/** A button's colors: its [container] and its [content] on it. */
private class Tone(
    val container: ColorProvider,
    val content: ColorProvider,
)

/** One of the 2 × 1 widget's buttons, sharing the row, its [tone] with the launcher's corners less the padding. */
@Composable
private fun RowScope.ComposeButton(
    icon: Int,
    label: String,
    description: String,
    intent: Intent,
    tone: Tone,
) {
    Column(
        modifier =
            GlanceModifier
                .defaultWeight()
                .fillMaxHeight()
                .innerBackground(tone.container)
                .clickable(actionStartActivity(intent))
                .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonContent(icon, label, tone.content)
    }
}

/** A button's icon over its label, centred. */
@Composable
private fun ButtonContent(
    icon: Int,
    label: String,
    color: ColorProvider,
) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            ImageProvider(icon),
            contentDescription = null,
            modifier = GlanceModifier.size(ComposeTokens.icon),
            colorFilter = ColorFilter.tint(color),
        )
        Text(
            text = label,
            style = TextStyle(color = color, fontSize = ComposeTokens.label, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** "Voice": the share sheet's chat picker, for a voice message ([ACTION_NEW_VOICE_MESSAGE]). */
private fun newVoiceMessageIntent(context: Context): Intent =
    Intent(ACTION_NEW_VOICE_MESSAGE, null, context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** The widget's sizes: Material 3 tokens in the units RemoteViews take, as [WidgetTokens]. */
internal object ComposeTokens {
    /** Around and between the buttons: `spacing.small`. */
    val padding = 8.dp

    /** Below this width the widget shows "New" alone: two buttons of at least 48 dp, with the padding. */
    val twoButtonsWidth = 120.dp

    /** The icon, 24 dp, over the label in `labelMedium`'s size. */
    val icon = 24.dp
    val label = 12.sp
}
