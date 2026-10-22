package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * The bottom of the window that the app's floating navigation bar covers on the top-level screens, as insets
 * (`docs/DESIGN_SYSTEM.md` §6–7). The bar floats over them so their content scrolls behind it; none where there is no
 * bar: the login flow, the navigation rail of larger windows. Screens pushed on top of a tab, which hide the bar, do
 * not read them: they stay the same whatever the destination, because changing this static local recomposes every
 * screen. A screen that hides the bar ([HideNavigationBar]) keeps these insets, as its own floating toolbar takes the
 * bar's place.
 */
val LocalNavigationBarInsets = staticCompositionLocalOf { WindowInsets(0, 0, 0, 0) }

/**
 * A `Scaffold`'s `contentWindowInsets` on a top-level screen: the system bars and the floating navigation bar, so the
 * inner padding keeps the end of scrolling content clear of the bar.
 */
@Composable
fun screenContentWindowInsets(): WindowInsets =
    ScaffoldDefaults.contentWindowInsets.union(LocalNavigationBarInsets.current)

/**
 * The bottom that a floating toolbar takes at the bottom of the window, placed as the floating navigation bar is
 * (`FloatingToolbarDefaults.ScreenOffset` above the system navigation bar): the system navigation bar, the toolbar and
 * the margin below it, and the same margin again above it, so the end of a list rests clear of the toolbar.
 */
@Composable
fun floatingToolbarInsets(): WindowInsets = floatingInsets(FloatingToolbarDefaults.ContainerSize)

/**
 * The bottom the floating navigation bar takes (`docs/DESIGN_SYSTEM.md` §6.1), as [floatingToolbarInsets] for its
 * `sizes.floatingNavigationBar`-tall pill, lower than Material's floating toolbar, as Google Photos' is.
 */
@Composable
fun floatingNavigationBarInsets(): WindowInsets = floatingInsets(MaterialTheme.sizes.floatingNavigationBar)

/**
 * The system navigation bar, something [height] tall at `FloatingToolbarDefaults.ScreenOffset` above it, and as much
 * above.
 */
@Composable
private fun floatingInsets(height: Dp): WindowInsets =
    WindowInsets.navigationBars.add(
        WindowInsets(bottom = height + FloatingToolbarDefaults.ScreenOffset * 2),
    )
