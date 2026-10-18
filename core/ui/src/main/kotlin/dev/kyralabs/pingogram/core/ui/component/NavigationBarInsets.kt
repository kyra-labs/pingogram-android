package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The bottom of the window that the app's floating navigation bar covers, as insets (`docs/DESIGN_SYSTEM.md` §6–7).
 * The bar floats over the top-level screens so their content scrolls behind it; none where there is no bar: the login
 * flow, the navigation rail of larger windows, screens pushed on top of a tab.
 */
val LocalNavigationBarInsets = staticCompositionLocalOf { WindowInsets(0, 0, 0, 0) }

/**
 * A `Scaffold`'s `contentWindowInsets` on a top-level screen: the system bars and the floating navigation bar, so the
 * inner padding keeps the end of scrolling content clear of the bar.
 */
@Composable
fun screenContentWindowInsets(): WindowInsets =
    ScaffoldDefaults.contentWindowInsets.union(LocalNavigationBarInsets.current)
