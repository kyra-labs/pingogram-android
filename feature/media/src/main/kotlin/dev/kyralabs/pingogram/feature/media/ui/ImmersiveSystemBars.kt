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
 */
@Composable
internal fun ImmersiveSystemBars(hidden: Boolean) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val bars = remember(window, view) { ImmersiveBars(ControllerBars(WindowCompat.getInsetsController(window, view))) }
    DisposableEffect(bars) {
        bars.enter()
        onDispose { bars.leave() }
    }
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
 * [hide] hid them.
 */
internal class ImmersiveBars(
    private val bars: SystemBars,
) {
    private val restore = mutableListOf<() -> Unit>()
    private var hidden = false

    fun enter() {
        if (bars.lightStatusBars) {
            bars.lightStatusBars = false
            restore += { bars.lightStatusBars = true }
        }
        if (bars.lightNavigationBars) {
            bars.lightNavigationBars = false
            restore += { bars.lightNavigationBars = true }
        }
        val behavior = bars.behavior
        if (behavior != SWIPE_BEHAVIOR) {
            bars.behavior = SWIPE_BEHAVIOR
            restore += { bars.behavior = behavior }
        }
    }

    fun hide(hide: Boolean) {
        if (hide == hidden) return
        hidden = hide
        if (hide) bars.hide() else bars.show()
    }

    fun leave() {
        hide(false)
        restore.forEach { it() }
        restore.clear()
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
