package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Settings home (`docs/DESIGN_SYSTEM.md` §13.9): settings search finds pages by their titles
 * and summaries and opens them, the account card's buttons open their pages and its menu switches accounts, the phone
 * number shows on request unless "Hide phone numbers" is on, and every page of the standard home stays reachable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsHomeTest {
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
        summaries: SettingsHomeSummaries = SettingsPreviewData.homeSummaries,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                CompositionLocalProvider(LocalPhoneNumbersHidden provides phoneNumbersHidden) {
                    SettingsScreen(
                        state = state,
                        onEvent = { events += it },
                        onOpenPage = { opened += it },
                        onOpenSubPage = { openedSubPages += it },
                        summaries = summaries,
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
    fun `search finds a single setting by its name and opens its page`() {
        show()
        search("incognito")

        composeRule.onNode(hasText(string(R.string.settings_incognito_keyboard)) and hasClickAction()).performClick()

        assertEquals(listOf(SettingsPage.PrivacySecurity), opened)
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
    fun `on this phone stays hidden while no tool has a row there`() {
        val groups =
            HomeGroups.map {
                if (it === OnThisPhoneGroup) HomeGroup(it.title, emptyList(), it.category, it.footer) else it
            }
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                Column {
                    SettingsHomeGroups(
                        summaries = SettingsPreviewData.homeSummaries,
                        onOpen = {},
                        selection = null,
                        groups = groups,
                    )
                }
            }
        }

        composeRule.onAllNodes(hasText(string(R.string.settings_home_on_this_phone))).assertCountEquals(0)
        composeRule.onAllNodes(hasText(string(R.string.settings_home_on_this_phone_footer))).assertCountEquals(0)
    }

    @Test
    fun `on this phone lists reminders with how many are to come, and opens them`() {
        show(summaries = SettingsPreviewData.homeSummaries.copy(upcomingReminders = 3))

        composeRule.onNodeWithText(string(R.string.settings_home_on_this_phone)).assertExists()
        composeRule.onNodeWithText("3 upcoming").assertExists()
        composeRule.onNodeWithText(string(R.string.settings_reminders)).performClick()

        assertEquals(listOf(SettingsPage.Reminders), opened)
    }

    @Test
    fun `on this phone opens keyword alerts`() {
        show()

        composeRule.onNode(hasText(string(R.string.settings_home_on_this_phone))).assertExists()
        composeRule.onNode(hasText(string(R.string.settings_keyword_alerts)) and hasClickAction()).performClick()

        assertEquals(listOf(SettingsPage.KeywordAlerts), opened)
    }

    @Test
    fun `focus is on this phone`() {
        show()

        composeRule.onNodeWithText(string(R.string.settings_focus)).performClick()

        assertEquals(listOf(SettingsPage.Focus), opened)
    }

    @Test
    fun `on this phone shows a tool's row with the group's footer`() {
        val tool =
            page(
                SettingsPage.Appearance,
                R.string.settings_appearance,
                R.string.settings_appearance_summary,
                R.drawable.ic_shield_filled,
            )
        val onThisPhone = OnThisPhoneGroup
        val groups = listOf(HomeGroup(onThisPhone.title, listOf(tool), onThisPhone.category, onThisPhone.footer))
        composeRule.setContent {
            PingogramTheme(themeMode = ThemeMode.Light, dynamicColor = false) {
                Column {
                    SettingsHomeGroups(
                        summaries = SettingsPreviewData.homeSummaries,
                        onOpen = { opened += (it as HomeDestination.Page).page },
                        selection = null,
                        groups = groups,
                    )
                }
            }
        }

        composeRule.onNode(hasText(string(R.string.settings_home_on_this_phone))).assertExists()
        composeRule.onNode(hasText(string(R.string.settings_home_on_this_phone_footer))).assertExists()
        composeRule.onNode(hasText(string(R.string.settings_appearance)) and hasClickAction()).performClick()
        assertEquals(listOf(SettingsPage.Appearance), opened)
    }

    @Test
    fun `on this phone comes first`() {
        assertEquals(OnThisPhoneGroup, HomeGroups.first())
    }

    @Test
    fun `every page of the standard home stays reachable`() {
        val reachable =
            HomeGroups.flatMap { it.entries }.mapNotNull { (it.destination as? HomeDestination.Page)?.page }.toSet()
        val standard =
            listOf(
                SettingsPage.Appearance,
                SettingsPage.Chats,
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
        // The pages above Privacy and security and its settings found by name, whose summary names it, follow it.
        val above =
            found
                .drop(1)
                .map { entry ->
                    when (val destination = entry.destination) {
                        is HomeDestination.SubPage -> destination.subPage.parent
                        is HomeDestination.Page -> destination.page
                        else -> null
                    }
                }.toSet()
        assertEquals(setOf(SettingsPage.PrivacySecurity), above)
        assertEquals(emptyList<HomeEntry>(), searchEntries(SearchEntries, "  ", resources, Locale.US))
    }

    @Test
    fun `the groups after On this phone hold their pages in order, with no More group`() {
        val pages =
            HomeGroups.drop(1).map { group ->
                string(group.title) to group.entries.map { (it.destination as HomeDestination.Page).page }
            }
        assertEquals(
            listOf(
                string(R.string.settings_home_make_it_yours) to
                    listOf(
                        SettingsPage.Appearance,
                        SettingsPage.Chats,
                        SettingsPage.ChatFolders,
                        SettingsPage.Stickers,
                    ),
                string(R.string.settings_home_alerts_privacy) to
                    listOf(
                        SettingsPage.Notifications,
                        SettingsPage.PrivacySecurity,
                        SettingsPage.ActiveSessions,
                        SettingsPage.DataStorage,
                    ),
                string(R.string.settings_home_app) to
                    listOf(SettingsPage.Language, SettingsPage.PowerSaving, SettingsPage.About),
            ),
            pages,
        )
    }

    @Test
    fun `search no longer finds Recent calls, which the Contacts tab opens`() {
        val resources = RuntimeEnvironment.getApplication().resources
        assertEquals(emptyList<HomeEntry>(), searchEntries(SearchEntries, "recent calls", resources, Locale.US))
    }

    @Test
    fun `Chats follows Appearance in Make it yours`() {
        val pages = HomeGroups[1].entries.map { (it.destination as HomeDestination.Page).page }
        assertEquals(SettingsPage.Chats, pages[pages.indexOf(SettingsPage.Appearance) + 1])
    }

    @Test
    fun `search finds the pages that moved to Chats under it, and those that stayed under Appearance`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val chats = string(R.string.settings_chats)
        val appearance = string(R.string.settings_appearance)
        for ((subPage, parent) in listOf(
            SettingsSubPage.DoubleTapReaction to chats,
            SettingsSubPage.SwipeActions to chats,
            SettingsSubPage.ChatsShownFirst to chats,
            SettingsSubPage.ChatAppearance to appearance,
            SettingsSubPage.ColorTheme to appearance,
        )) {
            val entry = SearchEntries.single { it.destination == HomeDestination.SubPage(subPage) }
            assertEquals(parent, resources.getString(entry.summary))
        }
        val found = searchEntries(SearchEntries, "swipe", resources, Locale.US).map { it.destination }
        // The Chats row, whose summary names swipes, then the page above it.
        assertEquals(
            listOf(HomeDestination.Page(SettingsPage.Chats), HomeDestination.SubPage(SettingsSubPage.SwipeActions)),
            found,
        )
    }

    @Test
    fun `search finds snippets, transcription, the login email, passkeys, the personal channel and the color`() {
        val resources = RuntimeEnvironment.getApplication().resources
        val expected =
            mapOf(
                SettingsSubPage.Snippets to R.string.settings_chats,
                SettingsSubPage.Transcription to R.string.settings_chats,
                SettingsSubPage.LoginEmail to R.string.settings_privacy_security,
                SettingsSubPage.Passkeys to R.string.settings_privacy_security,
                SettingsSubPage.PersonalChannel to R.string.settings_edit_profile_button,
                SettingsSubPage.ProfileColor to R.string.settings_edit_profile_button,
            )
        for ((subPage, parent) in expected) {
            val entry = SearchEntries.single { it.destination == HomeDestination.SubPage(subPage) }
            assertEquals(string(parent), resources.getString(entry.summary))
            val found = searchEntries(SearchEntries, resources.getString(entry.title), resources, Locale.US)
            assertTrue("$subPage", entry in found)
        }
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
