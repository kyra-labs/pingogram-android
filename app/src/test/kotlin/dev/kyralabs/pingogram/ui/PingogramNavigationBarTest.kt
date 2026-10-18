package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [PingogramNavigationBar]: tabs with labels where they fit, icons named by their label where they do not, and the
 * detached search button. A plain [Application] keeps app start-up work out of UI tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PingogramNavigationBarTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val selections = mutableListOf<TopLevelDestination>()
    private var searches = 0

    @Test
    fun `every tab shows its label on a phone`() {
        setBar(selected = TopLevelDestination.Chats)

        labeledTab(R.string.nav_chats).assertIsSelected()
        labeledTab(R.string.nav_contacts).assertIsNotSelected()
        labeledTab(R.string.nav_settings).assertIsNotSelected()
    }

    @Test
    @Config(qualifiers = "w360dp-h780dp-xxhdpi")
    fun `on a narrow phone at a large font scale the other tabs show icons named by their labels`() {
        RuntimeEnvironment.setFontScale(LARGE_FONT_SCALE)
        setBar(selected = TopLevelDestination.Chats)

        labeledTab(R.string.nav_chats).assertIsSelected()
        iconTab(R.string.nav_contacts).assertIsNotSelected()
        iconTab(R.string.nav_settings).assertIsNotSelected()
        composeRule.onNode(hasText(string(R.string.nav_contacts))).assertDoesNotExist()
    }

    @Test
    fun `selecting a tab reports its destination`() {
        setBar(selected = TopLevelDestination.Chats)

        labeledTab(R.string.nav_settings).performClick()

        assertEquals(listOf(TopLevelDestination.Settings), selections)
    }

    @Test
    fun `the search button opens search`() {
        setBar(selected = TopLevelDestination.Contacts)

        composeRule.onNode(hasContentDescription(string(R.string.nav_search))).performClick()

        assertEquals(1, searches)
    }

    @Test
    fun `switching tabs keeps the bar in place`() {
        var selected by mutableStateOf(TopLevelDestination.Chats)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                // Centred at the bottom, as in the app.
                Box(Modifier.fillMaxSize()) {
                    PingogramNavigationBar(
                        selected = selected,
                        onSelect = {},
                        onSearch = {},
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        val before = searchButtonLeft()
        composeRule.mainClock.autoAdvance = false

        selected = TopLevelDestination.Contacts
        val during =
            List(SWITCH_FRAMES) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                searchButtonLeft()
            }
        composeRule.mainClock.autoAdvance = true
        composeRule.waitForIdle()
        val after = searchButtonLeft()

        // The labels' widths differ a little, so the bar may settle a few pixels away; on the way it stays between,
        // give or take the spring's overshoot.
        val tolerance = with(composeRule.density) { OVERSHOOT_TOLERANCE.toPx() }
        val range = (minOf(before, after) - tolerance)..(maxOf(before, after) + tolerance)
        assertTrue("$before → $during → $after", during.all { it in range })
    }

    private fun searchButtonLeft(): Float =
        composeRule
            .onNode(hasContentDescription(string(R.string.nav_search)))
            .fetchSemanticsNode()
            .boundsInRoot.left

    private fun setBar(selected: TopLevelDestination) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PingogramNavigationBar(selected = selected, onSelect = { selections += it }, onSearch = { searches++ })
            }
        }
    }

    private fun labeledTab(
        @StringRes label: Int,
    ) = composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun iconTab(
        @StringRes label: Int,
    ) = composeRule.onNode(hasContentDescription(string(label)) and isSelectable())

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val LARGE_FONT_SCALE = 2f

        /** Enough frames for the tabs' springs to play out. */
        const val SWITCH_FRAMES = 40

        /** How far the fast spatial spring's overshoot may widen the bar, half of it on each side. */
        val OVERSHOOT_TOLERANCE = 3.dp
    }
}
