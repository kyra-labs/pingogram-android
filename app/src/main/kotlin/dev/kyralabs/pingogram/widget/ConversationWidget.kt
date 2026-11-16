package dev.kyralabs.pingogram.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.MainActivity
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The Conversation widget (board 33): one chat's newest messages and a reply, drawn by [ConversationWidgetContent] from
 * [ConversationWidgetUpdater.content] in the app's colors ([widgetColors]). Each widget keeps its chat and that chat's
 * account in its Glance state ([ConversationWidgetSetup.choose]); a widget just placed keeps none and asks to choose
 * one, which the app's chat picker does.
 */
class ConversationWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context, ConversationWidgetEntryPoint::class.java)
        val updater = entryPoint.conversationWidgetUpdater()
        val appearance = entryPoint.appearanceRepository().appearance
        val key = keyOf(getAppWidgetState(context, PreferencesGlanceStateDefinition, id))
        key?.let(updater::onWidgetShown)
        val setUp = actionStartActivity(ConversationWidgetSetup.intent(context, appWidgetIdOf(context, id)))
        val retry = actionRunCallback<RetryConversation>()
        provideContent {
            // A widget without a chat asks to sign in or unlock first, where Pingogram needs it (board 33).
            val content by (key?.let { updater.content(it) } ?: updater.setUpContent)
                .collectAsState(initial = key?.let(updater::currentContent) ?: ConversationContent.Loading)
            val settings by appearance.collectAsState(initial = null)
            GlanceTheme(colors = widgetColors(settings)) { ConversationWidgetContent(content, setUp, retry) }
        }
    }
}

/** "Tap to retry": loads the failed histories again. */
class RetryConversation : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        EntryPointAccessors
            .fromApplication(context, ConversationWidgetEntryPoint::class.java)
            .conversationWidgetUpdater()
            .retry()
    }
}

/** The Conversation widget's receiver; tells the updater when widgets come and go. */
class ConversationWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ConversationWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updater(context).onWidgetsChanged()
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        updater(context).onWidgetsChanged()
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        updater(context).onWidgetsChanged()
    }

    private fun updater(context: Context) =
        EntryPointAccessors
            .fromApplication(context, ConversationWidgetEntryPoint::class.java)
            .conversationWidgetUpdater()
}

/**
 * Setting up a Conversation widget: its "Choose a chat" opens the app ([intent]), which, signed in and unlocked, shows
 * its chat picker for that widget ([appWidgetIdOf]); the chat picked is kept in the widget's state ([choose]).
 */
object ConversationWidgetSetup {
    /** The action of a widget's "Choose a chat". */
    internal const val ACTION_CHOOSE_CHAT = "dev.kyralabs.pingogram.action.CHOOSE_WIDGET_CHAT"

    private const val WIDGET_SCHEME = "pingogram-widget"

    /** Opens the app's chat picker for the widget with [appWidgetId]. */
    fun intent(
        context: Context,
        appWidgetId: Int,
    ): Intent =
        // Its own data per widget, so each widget's pending intent is its own.
        Intent(
            ACTION_CHOOSE_CHAT,
            "$WIDGET_SCHEME://conversation/$appWidgetId".toUri(),
            context,
            MainActivity::class.java,
        ).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** The widget [intent] asks a chat for; null for other intents. */
    fun appWidgetIdOf(intent: Intent?): Int? =
        intent
            ?.takeIf { it.action == ACTION_CHOOSE_CHAT }
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }

    /**
     * Keeps chat [chatId] of the account in [slot] in the Conversation widget [appWidgetId] and draws it; false, doing
     * nothing, when that id is no Conversation widget of this app (an intent another app sent).
     */
    suspend fun choose(
        context: Context,
        appWidgetId: Int,
        slot: Int,
        chatId: Long,
    ): Boolean {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId)?.provider
        if (provider != ComponentName(context, ConversationWidgetReceiver::class.java)) return false
        val id = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
        updateAppWidgetState(context, id) {
            it[ACCOUNT_KEY] = slot
            it[CHAT_KEY] = chatId
        }
        EntryPointAccessors
            .fromApplication(context, ConversationWidgetEntryPoint::class.java)
            .conversationWidgetUpdater()
            .onWidgetsChanged()
        ConversationWidget().update(context, id)
        return true
    }
}

/** The chat a widget keeps, from its Glance state; null for one that keeps none yet. */
private fun keyOf(state: Preferences): ConversationKey? {
    val chatId = state[CHAT_KEY] ?: return null
    return ConversationKey(slot = state[ACCOUNT_KEY] ?: 0, chatId = chatId)
}

private fun appWidgetIdOf(
    context: Context,
    id: GlanceId,
): Int = GlanceAppWidgetManager(context).getAppWidgetId(id)

/** The account and the chat a Conversation widget keeps, in its Glance state. */
private val ACCOUNT_KEY = intPreferencesKey("account")
private val CHAT_KEY = longPreferencesKey("chat")

@Singleton
internal class GlanceConversationWidgets
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ConversationWidgets {
        override suspend fun keys(): Set<ConversationKey> {
            val manager = GlanceAppWidgetManager(context)
            return manager
                .getGlanceIds(ConversationWidget::class.java)
                .mapNotNull { keyOf(getAppWidgetState(context, PreferencesGlanceStateDefinition, it)) }
                .toSet()
        }

        override suspend fun updateAll() = ConversationWidget().updateAll(context)
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface ConversationWidgetModule {
    @Binds
    fun widgets(widgets: GlanceConversationWidgets): ConversationWidgets
}

/** What the widget, which Android creates without Hilt, takes from the app's graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ConversationWidgetEntryPoint {
    fun conversationWidgetUpdater(): ConversationWidgetUpdater

    fun appearanceRepository(): AppearanceRepository
}
