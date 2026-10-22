package dev.kyralabs.pingogram.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import dagger.Binds
import dagger.Module
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.kyralabs.pingogram.core.domain.model.AppearanceSettings
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.repository.AppearanceRepository
import dev.kyralabs.pingogram.core.ui.theme.themeColorScheme
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The home screen widget of recent chats (`docs/adr/0017-home-screen-widget.md`): Glance draws
 * [RecentChatsWidgetContent] from [RecentChatsWidgetUpdater.content] while its session runs, in the app's colors
 * ([widgetColors]). Asking for its content tells the updater a widget is placed, which then keeps it current.
 */
class RecentChatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(context, RecentChatsWidgetEntryPoint::class.java)
        val updater = entryPoint.recentChatsWidgetUpdater()
        val appearance = entryPoint.appearanceRepository().appearance
        updater.onWidgetsChanged(present = true)
        provideContent {
            val content by updater.content.collectAsState()
            val settings by appearance.collectAsState(initial = null)
            GlanceTheme(colors = widgetColors(settings)) { RecentChatsWidgetContent(content) }
        }
    }
}

/**
 * The widget's colors: the wallpaper's (Glance's dynamic colors, Android 12+) where the app uses them, otherwise the
 * color theme's light and dark schemes at standard contrast. The launcher draws the widget, so it follows the system's
 * dark theme, not the app's theme mode.
 */
@Composable
private fun widgetColors(settings: AppearanceSettings?): ColorProviders {
    val dynamic = settings == null || settings.dynamicColor
    return if (dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        GlanceTheme.colors
    } else {
        val palette = settings?.palette ?: AppearanceSettings().palette
        ColorProviders(
            light = themeColorScheme(palette, dark = false, contrast = Contrast.Standard),
            dark = themeColorScheme(palette, dark = true, contrast = Contrast.Standard),
        )
    }
}

/** The receiver of the widget's updates; tells the updater when the first widget comes and the last one goes. */
class RecentChatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentChatsWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        updater(context).onWidgetsChanged(present = true)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        updater(context).onWidgetsChanged(present = false)
    }

    private fun updater(context: Context) =
        EntryPointAccessors
            .fromApplication(context, RecentChatsWidgetEntryPoint::class.java)
            .recentChatsWidgetUpdater()
}

/** The placed widgets, behind a seam so the updater's tests need no launcher. */
interface RecentChatsWidgets {
    /** Whether a widget of recent chats is on a home screen. */
    suspend fun hasWidgets(): Boolean

    /** Draws every placed widget again. */
    suspend fun updateAll()
}

@Singleton
internal class GlanceRecentChatsWidgets
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RecentChatsWidgets {
        override suspend fun hasWidgets(): Boolean =
            GlanceAppWidgetManager(context).getGlanceIds(RecentChatsWidget::class.java).isNotEmpty()

        override suspend fun updateAll() = RecentChatsWidget().updateAll(context)
    }

@Module
@InstallIn(SingletonComponent::class)
internal interface RecentChatsWidgetModule {
    @Binds
    fun widgets(widgets: GlanceRecentChatsWidgets): RecentChatsWidgets
}

/** What the widget, which Android creates without Hilt, takes from the app's graph. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface RecentChatsWidgetEntryPoint {
    fun recentChatsWidgetUpdater(): RecentChatsWidgetUpdater

    fun appearanceRepository(): AppearanceRepository
}
