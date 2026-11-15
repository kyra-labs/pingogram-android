package dev.kyralabs.pingogram.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import dagger.hilt.android.EntryPointAccessors

/**
 * The unread counter widget (board 33): how many messages of the main list's unmuted chats are unread, the mentions,
 * and the avatars of the first three unread chats, from the same updater as the recent chats widget
 * ([RecentChatsWidgetUpdater]), in the same colors; each widget keeps the account it was placed in, as that one does.
 */
class UnreadCounterWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context, RecentChatsWidgetEntryPoint::class.java)
        val updater = entryPoint.recentChatsWidgetUpdater()
        val appearance = entryPoint.appearanceRepository().appearance
        val slot = accountOf(context, id, default = updater.shownSlot)
        updater.onWidgetShown(slot)
        provideContent {
            val content by updater.content(slot).collectAsState(initial = updater.currentContent(slot))
            val settings by appearance.collectAsState(initial = null)
            GlanceTheme(colors = widgetColors(settings)) { UnreadCounterContent(content) }
        }
    }
}

/** The counter's receiver; tells the updater when widgets come and go, as the recent chats widget's does. */
class UnreadCounterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UnreadCounterWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updater(context).onWidgetsChanged(present = true)
    }

    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray,
    ) {
        super.onDeleted(context, appWidgetIds)
        updater(context).onWidgetsChanged(present = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Recent chats widgets may still be placed: the updater asks the launcher again.
        updater(context).onWidgetsChanged(present = true)
    }

    private fun updater(context: Context) =
        EntryPointAccessors
            .fromApplication(context, RecentChatsWidgetEntryPoint::class.java)
            .recentChatsWidgetUpdater()
}
