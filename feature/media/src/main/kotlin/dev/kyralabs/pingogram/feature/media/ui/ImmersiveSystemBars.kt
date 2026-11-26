package dev.kyralabs.pingogram.feature.media.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * The system bars while the viewer shows: light icons on its dark background, and hidden while [hidden] (immersive,
 * with the chrome), coming back for a while with a swipe from the edge. Everything is as it was once the viewer
 * leaves the composition. Nothing happens outside an activity (previews).
 *
 * Each window attribute changes only where it differs ([ImmersiveBars]): each change is a window relayout, a binder
 * call to the window manager on the main thread, in the frames that open and close the viewer
 * (`docs/audit/performance-review-2026-11.md`, M3).
 *
 * Dark mode can switch while the viewer shows, as the activity is not created again: the app then sets the icons for
 * its new theme ([appDark], the app's theme around the viewer's own dark one), in an effect of its own that runs
 * before this one, and the viewer turns them light again.
 */
@Composable
internal fun ImmersiveSystemBars(
    hidden: Boolean,
    appDark: Boolean,
) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val bars = remember(window, view) { ImmersiveBars(ControllerBars(WindowCompat.getInsetsController(window, view))) }
    DisposableEffect(bars) {
        bars.enter()
        onDispose { bars.leave() }
    }
    LaunchedEffect(bars, appDark) { bars.themeIs(appDark) }
    LaunchedEffect(bars, hidden) { bars.hide(hidden) }
}

/** What [ImmersiveBars] changes of the window: a seam over [WindowInsetsControllerCompat] for its tests. */
internal interface SystemBars {
    var lightStatusBars: Boolean
    var lightNavigationBars: Boolean
    var behavior: Int

    fun hide()

    fun show()
}

/**
 * The viewer's system bars on [bars]: [enter] turns their icons light and lets a swipe show them for a while, changing
 * only what differs and remembering it; [leave] puts back only what [enter] changed, and shows the bars again only if
 * [hide] hid them. Once the app's theme changed ([themeIs]), which set the icons for the new theme, the icons turn
 * light again, and what [leave] puts back of them is the new theme's.
 */
internal class ImmersiveBars(
    private val bars: SystemBars,
) {
    private val restoreIcons = mutableListOf<() -> Unit>()
    private var restoreBehavior: (() -> Unit)? = null
    private var hidden = false
    private var dark: Boolean? = null

    fun enter() {
        lightIcons()
        val behavior = bars.behavior
        if (behavior != SWIPE_BEHAVIOR) {
            bars.behavior = SWIPE_BEHAVIOR
            restoreBehavior = { bars.behavior = behavior }
        }
    }

    /** The app's theme is [dark] now: a change since the last call means the app set the icons for its new theme. */
    fun themeIs(dark: Boolean) {
        val changed = this.dark != null && this.dark != dark
        this.dark = dark
        if (changed) {
            restoreIcons.clear()
            lightIcons()
        }
    }

    private fun lightIcons() {
        if (bars.lightStatusBars) {
            bars.lightStatusBars = false
            restoreIcons += { bars.lightStatusBars = true }
        }
        if (bars.lightNavigationBars) {
            bars.lightNavigationBars = false
            restoreIcons += { bars.lightNavigationBars = true }
        }
    }

    fun hide(hide: Boolean) {
        if (hide == hidden) return
        hidden = hide
        if (hide) bars.hide() else bars.show()
    }

    fun leave() {
        hide(false)
        restoreIcons.forEach { it() }
        restoreIcons.clear()
        restoreBehavior?.invoke()
        restoreBehavior = null
    }

    private companion object {
        const val SWIPE_BEHAVIOR = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** [SystemBars] of a window's [controller]. */
private class ControllerBars(
    private val controller: WindowInsetsControllerCompat,
) : SystemBars {
    override var lightStatusBars: Boolean
        get() = controller.isAppearanceLightStatusBars
        set(value) {
            controller.isAppearanceLightStatusBars = value
        }

    override var lightNavigationBars: Boolean
        get() = controller.isAppearanceLightNavigationBars
        set(value) {
            controller.isAppearanceLightNavigationBars = value
        }

    override var behavior: Int
        get() = controller.systemBarsBehavior
        set(value) {
            controller.systemBarsBehavior = value
        }

    override fun hide() = controller.hide(WindowInsetsCompat.Type.systemBars())

    override fun show() = controller.show(WindowInsetsCompat.Type.systemBars())
}
