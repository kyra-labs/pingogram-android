package dev.kyralabs.pingogram.ui

import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PingogramNavigationSuiteTypeTest {
    @Test
    fun `compact phones in portrait get the floating bar`() {
        assertEquals(NavigationSuiteType.ShortNavigationBarCompact, typeFor(widthDp = 411, heightDp = 891))
    }

    @Test
    fun `phones in landscape get the collapsed rail`() {
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeFor(widthDp = 891, heightDp = 411))
    }

    @Test
    fun `a narrow window of compact height gets the collapsed rail`() {
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeFor(widthDp = 560, heightDp = 400))
    }

    @Test
    fun `medium and expanded windows get the collapsed rail`() {
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeFor(widthDp = 700, heightDp = 891))
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeFor(widthDp = 1000, heightDp = 800))
    }

    @Test
    fun `large and extra-large windows get the expanded rail`() {
        assertEquals(NavigationSuiteType.WideNavigationRailExpanded, typeFor(widthDp = 1280, heightDp = 800))
        assertEquals(NavigationSuiteType.WideNavigationRailExpanded, typeFor(widthDp = 1600, heightDp = 1000))
    }

    private fun typeFor(
        widthDp: Int,
        heightDp: Int,
    ): NavigationSuiteType =
        pingogramNavigationSuiteType(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp))
}
