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
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
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
 * detached search button; [FloatingNavigationBar] hiding and showing it. A plain [Application] keeps app start-up work
 * out of UI tests.
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

    // The Settings tab's account menu (docs/adr/0031-multiple-accounts.md)

    @Test
    fun `a long press on the Settings tab opens the account menu, without selecting the tab`() {
        setBar(selected = TopLevelDestination.Chats, accountMenu = accountMenu())

        labeledTab(R.string.nav_settings).performTouchInput { longClick() }

        composeRule.onNode(hasText("Grace Hopper")).assertExists()
        composeRule.onNode(hasText(string(R.string.account_menu_add))).assertExists()
        assertEquals(emptyList<TopLevelDestination>(), selections)
    }

    @Test
    fun `another account in the menu switches to it, and Add account adds one`() {
        setBar(selected = TopLevelDestination.Chats, accountMenu = accountMenu())

        labeledTab(R.string.nav_settings).performTouchInput { longClick() }
        composeRule.onNode(hasText("Grace Hopper")).performClick()
        labeledTab(R.string.nav_settings).performTouchInput { longClick() }
        composeRule.onNode(hasText(string(R.string.account_menu_add))).performClick()

        assertEquals(listOf(1), switches)
        assertEquals(1, adds)
        composeRule.onNode(hasText("Grace Hopper")).assertDoesNotExist()
    }

    @Test
    fun `the shown account in the menu only closes it`() {
        setBar(selected = TopLevelDestination.Chats, accountMenu = accountMenu())

        labeledTab(R.string.nav_settings).performTouchInput { longClick() }
        composeRule.onNode(hasText("Ada Lovelace")).performClick()

        assertEquals(emptyList<Int>(), switches)
        composeRule.onNode(hasText("Ada Lovelace")).assertDoesNotExist()
    }

    @Test
    fun `with an account menu, a tap on the Settings tab still selects it`() {
        setBar(selected = TopLevelDestination.Chats, accountMenu = accountMenu())

        labeledTab(R.string.nav_settings).performClick()

        assertEquals(listOf(TopLevelDestination.Settings), selections)
        composeRule.onNode(hasText("Grace Hopper")).assertDoesNotExist()
    }

    @Test
    fun `screen readers switch accounts and add one through the Settings tab's actions`() {
        setBar(selected = TopLevelDestination.Chats, accountMenu = accountMenu())
        val tab = labeledTab(R.string.nav_settings)

        tab.performCustomAccessibilityActionWithLabel(string(R.string.account_menu_switch, "\u2068Alan Turing\u2069"))
        tab.performCustomAccessibilityActionWithLabel(string(R.string.account_menu_add))

        assertEquals(listOf(2), switches)
        assertEquals(1, adds)
        // The shown account is no action, and the other tabs have none.
        labeledTab(R.string.nav_chats).assert(hasNoCustomActions())
    }

    @Test
    fun `without an account menu, a long press on the Settings tab opens nothing`() {
        setBar(selected = TopLevelDestination.Chats)

        labeledTab(R.string.nav_settings).performTouchInput { longClick() }

        composeRule.onNode(hasText(string(R.string.account_menu_add))).assertDoesNotExist()
        labeledTab(R.string.nav_settings).assert(hasNoCustomActions())
    }

    // The floating bar showing and hiding (FloatingNavigationBar)

    @Test
    fun `hidden, the floating bar is neither seen, heard nor touched`() {
        var visible by mutableStateOf(true)
        setFloatingBar { visible }
        val chats = labeledTab(R.string.nav_chats).fetchSemanticsNode().boundsInRoot.center

        visible = false
        composeRule.waitForIdle()

        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)
        composeRule.onNode(hasContentDescription(string(R.string.nav_search))).assertDoesNotExist()
        composeRule.onRoot().performTouchInput { click(chats) }
        assertEquals(emptyList<TopLevelDestination>(), selections)
    }

    @Test
    fun `the floating bar comes back as it left, without composing it anew`() {
        var visible by mutableStateOf(true)
        setFloatingBar { visible }
        val tab = labeledTab(R.string.nav_chats).fetchSemanticsNode().id

        visible = false
        composeRule.waitForIdle()
        visible = true
        composeRule.waitForIdle()

        // The same node: the bar stayed composed while hidden.
        assertEquals(tab, labeledTab(R.string.nav_chats).assertIsSelected().fetchSemanticsNode().id)
        labeledTab(R.string.nav_settings).performClick()
        assertEquals(listOf(TopLevelDestination.Settings), selections)
    }

    @Test
    fun `the floating bar slides down as it hides, by less than twice its height`() {
        var visible by mutableStateOf(true)
        setFloatingBar { visible }
        val barHeight =
            composeRule
                .onNodeWithTag(FLOATING_BAR_TAG)
                .fetchSemanticsNode()
                .size.height
        // Kept while the bar's semantics are gone: the tab's coordinates include the bar's graphics layer.
        val tab = labeledTab(R.string.nav_chats).fetchSemanticsNode().layoutInfo.coordinates
        val restingTop = tab.boundsInRoot().top
        composeRule.mainClock.autoAdvance = false

        visible = false
        val tops =
            List(HIDING_FRAMES) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                tab.boundsInRoot().top
            }

        val lowest = tops.max()
        assertTrue("$restingTop → $tops", lowest > restingTop && lowest < restingTop + 2 * barHeight)
        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)
    }

    private fun setFloatingBar(visible: () -> Boolean) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    FloatingNavigationBar(
                        visible = visible(),
                        selected = TopLevelDestination.Chats,
                        onSelect = { selections += it },
                        onSearch = { searches++ },
                        modifier = Modifier.align(Alignment.BottomCenter).testTag(FLOATING_BAR_TAG),
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun searchButtonLeft(): Float =
        composeRule
            .onNode(hasContentDescription(string(R.string.nav_search)))
            .fetchSemanticsNode()
            .boundsInRoot.left

    private fun setBar(
        selected: TopLevelDestination,
        accountMenu: AccountMenuActions? = null,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PingogramNavigationBar(
                    selected = selected,
                    onSelect = { selections += it },
                    onSearch = { searches++ },
                    accountMenu = accountMenu,
                )
            }
        }
    }

    private val switches = mutableListOf<Int>()
    private var adds = 0

    private fun accountMenu() = AccountMenuActions(PreviewMenu, onSwitch = { switches += it }, onAdd = { adds++ })

    private fun labeledTab(
        @StringRes label: Int,
    ) = composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun iconTab(
        @StringRes label: Int,
    ) = composeRule.onNode(hasContentDescription(string(label)) and isSelectable())

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    private fun hasNoCustomActions() =
        SemanticsMatcher("has no custom actions") { SemanticsActions.CustomActions !in it.config }

    private companion object {
        const val LARGE_FONT_SCALE = 2f

        /** Enough frames for the tabs' springs to play out. */
        const val SWITCH_FRAMES = 40

        /** Enough frames for the floating bar to slide and fade away. */
        const val HIDING_FRAMES = 30

        const val FLOATING_BAR_TAG = "floating_bar"

        /** How far the fast spatial spring's overshoot may widen the bar, half of it on each side. */
        val OVERSHOOT_TOLERANCE = 3.dp
    }
}
