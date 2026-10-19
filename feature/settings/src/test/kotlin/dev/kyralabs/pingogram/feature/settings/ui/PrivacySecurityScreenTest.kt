package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
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

    private fun node(
        @StringRes id: Int,
    ) = composeRule.onNodeWithText(string(id)).performScrollTo()

    @Test
    fun `each privacy setting shows its choice and opens its page`() {
        setScreen()

        composeRule.onNodeWithText("My contacts + exceptions").assertExists()
        PrivacySetting.entries.forEach { node(it.titleRes()).assertHasClickAction().performClick() }

        assertEquals(PrivacySetting.entries.map { PrivacySecurityPage.Setting(it) }, pages)
    }

    @Test
    fun `blocked users and active sessions show their counts and open their pages`() {
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
    fun `two-step verification shows its state and opens nothing`() {
        setScreen()

        composeRule
            .onNodeWithText(string(R.string.settings_two_step_verification_on_recovery_email), useUnmergedTree = true)
            .performScrollTo()
        node(R.string.settings_two_step_verification).assertHasNoClickAction()
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(PrivacySecurityUiState.Error(DomainError.Network))

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(PrivacySecurityEvent.Retry), events)
    }

    @Test
    fun `back leaves the page`() {
        setScreen()

        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(1, backs)
    }
}
