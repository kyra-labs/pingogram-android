package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.core.ui.component.LocalRecentCallsLauncher
import dev.kyralabs.pingogram.core.ui.text.LocalPhoneNumbersHidden
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.navigation.route
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.settings.ui.SettingsEvent
import dev.kyralabs.pingogram.feature.settings.ui.SettingsPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsScreen
import dev.kyralabs.pingogram.feature.settings.ui.SettingsSubPage
import dev.kyralabs.pingogram.feature.settings.ui.SettingsUiState
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Settings home in the experimental look (`docs/DESIGN_SYSTEM.md` §13.9): settings search finds pages by their titles
 * and summaries and opens them, the account card's buttons open their pages and its menu switches accounts, the phone
 * number shows on request unless "Hide phone numbers" is on, and every page of the standard home stays reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExperimentalSettingsHomeTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val opened = mutableListOf<SettingsPage>()
    private val openedSubPages = mutableListOf<SettingsSubPage>()
    private val events = mutableListOf<SettingsEvent>()

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun show(
        state: SettingsUiState = SettingsPreviewData.content,
        phoneNumbersHidden: Boolean = false,
        themeMode: ThemeMode = ThemeMode.Light,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalExperimentalUi provides true,
                    LocalPhoneNumbersHidden provides phoneNumbersHidden,
                    LocalRecentCallsLauncher provides {},
                ) {
                    SettingsScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenPage = { opened += it },
                        onOpenSubPage = { openedSubPages += it },
                        summaries = SettingsPreviewData.homeSummaries,
                    )
                }
            }
        }
    }

    private fun search(query: String) {
        composeRule
            .onNode(hasContentDescription(string(R.string.settings_home_search)) and hasClickAction())
            .performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(query)
        composeRule.waitForIdle()
    }

    @Test
    fun `search finds a page by a word of its summary and opens it`() {
        show()
        search("sticker")

        // The page comes first; the pages above it, which name it as their summary, follow.
        composeRule
            .onAllNodes(hasText(string(R.string.settings_home_stickers_emoji)) and hasClickAction())[0]
            .performClick()

        assertEquals(listOf(SettingsPage.Stickers), opened)
    }

    @Test
    fun `search finds pages beyond the groups, such as the QR code`() {
        show()
        search("qr")

        composeRule.onNode(hasText(string(R.string.settings_my_qr_title)) and hasClickAction()).performClick()

        assertEquals(listOf(SettingsPage.MyQrCode), opened)
    }

    @Test
    fun `search finds a page above a page and opens it there`() {
        show()
        search("blocked")

        composeRule.onNode(hasText(string(R.string.settings_blocked_users)) and hasClickAction()).performClick()

        assertEquals(listOf(SettingsSubPage.BlockedUsers), openedSubPages)
        assertEquals(emptyList<SettingsPage>(), opened)
    }

    @Test
    fun `every page above a page has its own entry and route`() {
        val entries = SubPageEntries.map { (it.destination as HomeDestination.SubPage).subPage }
        assertEquals(SettingsSubPage.entries.toList(), entries)
        assertEquals(
            SettingsSubPage.entries.size,
            SettingsSubPage.entries
                .map { it.route }
                .toSet()
                .size,
        )
        assertEquals(SettingsSubPage.entries.size, SubPageEntries.map { it.title }.toSet().size)
    }

    @Test
    fun `search that finds nothing says so`() {
        show()
        search("zebra")

        composeRule.onAllNodes(hasText(string(R.string.settings_home_no_results))).assertCountEquals(1)
    }

    @Test
    fun `closing search shows the home again`() {
        show()
        search("data")

        composeRule.onNode(hasContentDescription(string(R.string.settings_home_search_close))).performClick()

        composeRule.onAllNodes(hasText(string(R.string.settings_home_make_it_yours))).assertCountEquals(1)
    }

    @Test
    fun `the card's buttons open Edit profile and the QR code`() {
        show()

        composeRule.onNode(hasText(string(R.string.settings_edit_profile_button)) and hasClickAction()).performClick()
        composeRule.onNode(hasText(string(R.string.settings_home_qr_code)) and hasClickAction()).performClick()

        assertEquals(listOf(SettingsPage.EditProfile, SettingsPage.MyQrCode), opened)
    }

    @Test
    fun `the phone number shows on request`() {
        show()
        val number = SettingsPreviewData.content.account!!.phoneNumber
        composeRule.onAllNodes(hasText(number)).assertCountEquals(0)

        composeRule.onNode(hasContentDescription(string(R.string.settings_home_show_phone))).performClick()

        composeRule.onAllNodes(hasText(number)).assertCountEquals(1)
    }

    @Test
    fun `while phone numbers are hidden the number stays masked, without the button`() {
        show(phoneNumbersHidden = true)

        composeRule.onAllNodes(hasContentDescription(string(R.string.settings_home_show_phone))).assertCountEquals(0)
    }

    @Test
    fun `the account menu switches to another account`() {
        show(SettingsPreviewData.withOtherAccounts)
        val other = SettingsPreviewData.otherAccounts.first()

        composeRule.onNode(hasContentDescription(string(R.string.settings_home_switch_account))).performClick()
        composeRule.onNode(hasText(other.name) and hasClickAction()).performClick()

        assertEquals(listOf<SettingsEvent>(SettingsEvent.SwitchAccount(other.slot)), events)
    }

    @Test
    fun `every page of the standard home stays reachable`() {
        val reachable =
            HomeGroups.flatMap { it.entries }.mapNotNull { (it.destination as? HomeDestination.Page)?.page }.toSet()
        val standard =
            listOf(
                SettingsPage.Appearance,
                SettingsPage.ChatFolders,
                SettingsPage.Stickers,
                SettingsPage.Notifications,
                SettingsPage.DataStorage,
                SettingsPage.PowerSaving,
                SettingsPage.Language,
                SettingsPage.PrivacySecurity,
                SettingsPage.About,
            )
        assertEquals(emptyList<SettingsPage>(), standard - reachable)
    }

    @Test
    fun `search ignores case and accents and needs every word`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val found = searchEntries(SearchEntries, "PRÍVACY security", resources, Locale.US)
        assertEquals(HomeDestination.Page(SettingsPage.PrivacySecurity), found.first().destination)
        // The pages above Privacy and security, whose summary names it, follow it.
        val above = found.drop(1).map { (it.destination as HomeDestination.SubPage).subPage.parent }.toSet()
        assertEquals(setOf(SettingsPage.PrivacySecurity), above)
        assertEquals(emptyList<HomeEntry>(), searchEntries(SearchEntries, "  ", resources, Locale.US))
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun searchResults() {
        show()
        search("data")
        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.searchResults.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun searchResultsDark() {
        show(themeMode = ThemeMode.Dark)
        search("notif")
        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.searchResultsDark.png")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun accountMenu() {
        show(SettingsPreviewData.withOtherAccounts)
        composeRule.onNode(hasContentDescription(string(R.string.settings_home_switch_account))).performClick()
        composeRule.waitForIdle()
        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.accountMenu.png")
    }
}
