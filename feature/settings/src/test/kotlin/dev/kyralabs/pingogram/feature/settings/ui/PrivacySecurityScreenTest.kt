package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsEvent
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the Privacy and security page shows and opens; the screen is stateless, so these check its events. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacySecurityScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<PrivacySecurityEvent>()
    private val pages = mutableListOf<PrivacySecurityPage>()
    private var backs = 0

    private fun setScreen(state: PrivacySecurityUiState = SettingsPreviewData.privacySecurity) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PrivacySecurityScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpen = { pages += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    /** The row titled [id]: the privacy checkup's tags name some settings too, but do nothing when tapped. */
    private fun node(
        @StringRes id: Int,
    ) = composeRule.onAllNodes(hasText(string(id)) and hasClickAction()).onFirst().performScrollTo()

    @Test
    fun `each privacy setting shows its choice and opens its page`() {
        setScreen()

        composeRule.onNodeWithText("My contacts + exceptions").assertExists()
        PrivacySetting.entries.forEach { node(it.titleRes()).assertHasClickAction().performClick() }

        assertEquals(PrivacySetting.entries.map { PrivacySecurityPage.Setting(it) }, pages)
    }

    @Test
    fun `the privacy checkup reviews the first setting open to everybody, and Not now hides it`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_privacy_checkup_review)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_privacy_checkup_not_now)).performClick()

        assertEquals(1, pages.size)
        assertTrue(pages.single() is PrivacySecurityPage.Setting)
        composeRule.onNodeWithText(string(R.string.settings_privacy_checkup_review)).assertDoesNotExist()
    }

    @Test
    fun `blocked users and devices show their counts and open their pages`() {
        setScreen()

        composeRule.onNodeWithText("3 blocked").performScrollTo()
        composeRule.onNodeWithText("4 sessions, including this device").performScrollTo()
        node(R.string.settings_blocked_users).performClick()
        node(R.string.settings_active_sessions).performClick()

        assertEquals(listOf(PrivacySecurityPage.BlockedUsers, PrivacySecurityPage.ActiveSessions), pages)
    }

    @Test
    fun `nobody blocked says so`() {
        setScreen(SettingsPreviewData.privacySecurity.copy(blockedCount = 0))

        composeRule.onNodeWithText(string(R.string.settings_blocked_users_none)).performScrollTo()
    }

    @Test
    fun `two-step verification shows its state and opens its page`() {
        setScreen()

        composeRule
            .onNodeWithText(string(R.string.settings_two_step_verification_on_recovery_email), useUnmergedTree = true)
            .performScrollTo()
        node(R.string.settings_two_step_verification).assertHasClickAction().performClick()

        assertEquals(listOf(PrivacySecurityPage.TwoStepVerification), pages)
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(PrivacySecurityUiState.Error(DomainError.Network))

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(PrivacySecurityEvent.Retry), events)
    }

    @Test
    fun `the groups and the security rows follow the owner's order`() {
        val drafts = mutableListOf<DataOptionsEvent>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PrivacySecurityScreen(
                    state = SettingsPreviewData.privacySecurity,
                    onEvent = {},
                    onOpen = {},
                    onBack = {},
                    secretChatPreviews = false,
                    phoneNumbersHidden = false,
                    onDraftsEvent = { drafts += it },
                )
            }
        }

        fun top(
            @StringRes id: Int,
        ) = composeRule
            .onAllNodesWithText(string(id))
            .onFirst()
            .getUnclippedBoundsInRoot()
            .top

        val groups =
            listOf(
                R.string.settings_section_security,
                R.string.settings_section_privacy,
                R.string.settings_section_contacts,
                R.string.settings_section_secret_chats,
                R.string.settings_section_on_screen,
                R.string.settings_section_account,
            ).map(::top)
        val security =
            listOf(
                R.string.settings_two_step_verification,
                R.string.settings_passcode_lock,
                R.string.settings_blocked_users,
                R.string.settings_active_sessions,
                R.string.settings_websites,
            ).map(::top)
        assertEquals(groups.sorted(), groups)
        assertEquals(security.sorted(), security)
        // Delete cloud drafts is the account's, before Delete account.
        assertTrue(top(R.string.settings_section_account) < top(R.string.settings_delete_drafts))
        assertTrue(top(R.string.settings_delete_drafts) < top(R.string.settings_delete_account))
        node(R.string.settings_delete_drafts).performClick()
        assertEquals(listOf(DataOptionsEvent.DeleteDrafts), drafts)
    }

    @Test
    fun `the privacy screen switch is the first on screen, and sends its change`() {
        val changes = mutableListOf<Boolean>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PrivacySecurityScreen(
                    state = SettingsPreviewData.privacySecurity,
                    onEvent = {},
                    onOpen = {},
                    onBack = {},
                    phoneNumbersHidden = false,
                    privacyScreen = false,
                    onPrivacyScreenChange = { changes += it },
                )
            }
        }

        node(R.string.settings_privacy_screen).performClick()

        assertEquals(listOf(true), changes)
        val privacyScreen = composeRule.onNodeWithText(string(R.string.settings_privacy_screen))
        val phoneNumbers = composeRule.onNodeWithText(string(R.string.settings_hide_phone_numbers))
        assertTrue(privacyScreen.getUnclippedBoundsInRoot().top < phoneNumbers.getUnclippedBoundsInRoot().top)
        composeRule.assertAccessible()
    }

    @Test
    fun `back leaves the page`() {
        setScreen()

        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(1, backs)
    }
}
