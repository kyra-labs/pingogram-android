package dev.kyralabs.pingogram.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.color.ColorProviders
import androidx.glance.material3.ColorProviders
import androidx.glance.state.PreferencesGlanceStateDefinition
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
 * ([widgetColors]). Each widget keeps the account it was placed in (`docs/adr/0031-multiple-accounts.md`), kept in its
 * Glance state ([ACCOUNT_KEY]): the shown account's the first time it asks for its content. Asking tells the updater a
 * widget of that account is placed, which then keeps it current.
 */
class RecentChatsWidget : GlanceAppWidget() {
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

/** The slot of the account widget [id] keeps; [default] from now on if it keeps none yet. */
private suspend fun accountOf(
    context: Context,
    id: GlanceId,
    default: Int,
): Int {
    val kept = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[ACCOUNT_KEY]
    if (kept != null) return kept
    updateAppWidgetState(context, id) { it[ACCOUNT_KEY] = default }
    return default
}

/** The slot of the account a widget keeps, in its Glance state. */
private val ACCOUNT_KEY = intPreferencesKey("account")

/** The receiver of the widget's updates; tells the updater when widgets come and go. */
class RecentChatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecentChatsWidget()

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
        updater(context).onWidgetsChanged(present = false)
    }

    private fun updater(context: Context) =
        EntryPointAccessors
            .fromApplication(context, RecentChatsWidgetEntryPoint::class.java)
            .recentChatsWidgetUpdater()
}

/** The placed widgets, behind a seam so the updater's tests need no launcher. */
interface RecentChatsWidgets {
    /** The slots of the accounts the placed widgets keep; empty without a widget on a home screen. */
    suspend fun slots(): Set<Int>

    /** Moves the widgets that keep the account in slot [from] to the one in [to]. */
    suspend fun move(
        from: Int,
        to: Int,
    )

    /** Draws every placed widget again. */
    suspend fun updateAll()
}

@Singleton
internal class GlanceRecentChatsWidgets
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RecentChatsWidgets {
        override suspend fun slots(): Set<Int> = ids().mapNotNull { state(it)[ACCOUNT_KEY] }.toSet()

        override suspend fun move(
            from: Int,
            to: Int,
        ) = ids().filter { state(it)[ACCOUNT_KEY] == from }.forEach { id ->
            updateAppWidgetState(context, id) { it[ACCOUNT_KEY] = to }
        }

        private suspend fun ids() = GlanceAppWidgetManager(context).getGlanceIds(RecentChatsWidget::class.java)

        private suspend fun state(id: GlanceId) = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)

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
