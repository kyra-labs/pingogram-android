package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
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
    }
}
