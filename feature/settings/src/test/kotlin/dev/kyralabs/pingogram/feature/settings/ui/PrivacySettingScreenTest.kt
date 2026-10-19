package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Choosing who can see or do something on a privacy setting's page, and what it shows of the exceptions and of
 * Telegram Premium. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class PrivacySettingScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<PrivacySettingEvent>()

    private fun setScreen(state: PrivacySettingUiState = SettingsPreviewData.privacySetting) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                PrivacySettingScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    /** The radio button row of [choice]. */
    private fun choice(choice: PrivacyChoice) =
        composeRule.onNode(
            hasText(string(choice.labelRes())) and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton),
        )

    @Test
    fun `shows the question and the choices, the current one selected`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_privacy_last_seen_question)).assertExists()
        choice(PrivacyChoice.Everybody).assertIsNotSelected()
        choice(PrivacyChoice.Contacts).assertIsSelected()
        choice(PrivacyChoice.Nobody).assertIsNotSelected()
    }

    @Test
    fun `choosing an option selects it`() {
        setScreen()

        choice(PrivacyChoice.Nobody).performClick()
        choice(PrivacyChoice.Everybody).performClick()

        assertEquals(
            listOf(
                PrivacySettingEvent.Select(PrivacyChoice.Nobody),
                PrivacySettingEvent.Select(PrivacyChoice.Everybody),
            ),
            events,
        )
    }

    @Test
    fun `the exceptions show as counts, for the choice that shows`() {
        setScreen()

        composeRule.onNodeWithText("2 users and members of 1 group").assertExists()
        composeRule.onNodeWithText("3 users").assertExists()
        composeRule.onNodeWithText(string(R.string.settings_privacy_exceptions_note)).assertExists()
    }

    @Test
    fun `everybody shows only who is never allowed`() {
        setScreen(SettingsPreviewData.privacySetting.copy(savingChoice = PrivacyChoice.Everybody))

        composeRule.onNodeWithText(string(R.string.settings_privacy_always_allowed)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.settings_privacy_never_allowed)).assertExists()
    }

    @Test
    fun `no exceptions, no exceptions section`() {
        setScreen(
            PrivacySettingUiState(
                PrivacySetting.Calls,
                PrivacyRulesState.Loaded(PrivacyRules(PrivacyChoice.Everybody)),
            ),
        )

        composeRule.onNodeWithText(string(R.string.settings_section_privacy_exceptions)).assertDoesNotExist()
    }

    @Test
    fun `the choice being saved shows selected with progress, and the choices wait`() {
        setScreen(SettingsPreviewData.privacySetting.copy(savingChoice = PrivacyChoice.Nobody))

        choice(PrivacyChoice.Nobody).assertIsSelected().assertIsNotEnabled()
        choice(PrivacyChoice.Contacts).assertIsNotSelected().assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.settings_privacy_setting_saving))).assertExists()
    }

    @Test
    fun `without premium only everybody can be chosen for voice messages, and the page says why`() {
        setScreen(SettingsPreviewData.voiceMessages)

        choice(PrivacyChoice.Everybody).assertIsEnabled()
        choice(PrivacyChoice.Contacts).assertIsNotEnabled().performClick()
        choice(PrivacyChoice.Nobody).assertIsNotEnabled().performClick()
        composeRule.onNodeWithText(string(R.string.settings_privacy_premium_note)).assertExists()

        assertTrue(events.isEmpty())
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(PrivacySettingUiState(PrivacySetting.Calls, PrivacyRulesState.Failed(DomainError.Network)))

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(PrivacySettingEvent.Retry), events)
    }
}
