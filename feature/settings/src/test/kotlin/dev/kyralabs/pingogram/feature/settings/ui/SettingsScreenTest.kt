package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
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

/** What the Settings screen sends as the user taps its rows and answers the log out confirmation. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SettingsScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<SettingsEvent>()
    private val pages = mutableListOf<SettingsPage>()

    private fun setScreen(
        state: SettingsUiState = SettingsPreviewData.content,
        selection: SettingsPageSelection? = null,
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                SettingsScreen(
                    state = state,
                    onEvent = { events += it },
                    onOpenPage = { pages += it },
                    selection = selection,
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
    fun `shows the account, its phone number masked until asked`() {
        setScreen()

        composeRule.onNodeWithText("Ada Lovelace").assertExists()
        composeRule.onNodeWithText("@ada").assertExists()
        composeRule.onNodeWithText("+44 7911 123456").assertDoesNotExist()
    }

    @Test
    fun `beside a page its row reads as selected, the others not`() {
        setScreen(selection = SettingsPageSelection(SettingsPage.Appearance))

        composeRule.onNode(hasText(string(R.string.settings_appearance)) and isSelectable()).assertIsSelected()
        composeRule
            .onNode(hasText(string(R.string.settings_notifications)) and isSelectable())
            .performScrollTo()
            .assertIsNotSelected()
    }

    @Test
    fun `the card's Edit profile button opens edit profile`() {
        setScreen()

        composeRule.onNodeWithText(string(R.string.settings_edit_profile_button)).performClick()

        assertEquals(listOf(SettingsPage.EditProfile), pages)
    }

    @Test
    fun `without an account there is no profile to edit`() {
        setScreen(SettingsUiState())

        composeRule.onAllNodesWithText(string(R.string.settings_edit_profile_button)).assertCountEquals(0)
    }

    @Test
    fun `a Telegram Premium account shows its Premium label`() {
        setScreen(SettingsUiState(account = SettingsPreviewData.premiumAccount))

        composeRule.onNodeWithText(string(R.string.settings_account_premium)).assertExists()
    }

    @Test
    fun `an account without Telegram Premium shows no Premium label`() {
        setScreen()

        composeRule.onAllNodesWithText(string(R.string.settings_account_premium)).assertCountEquals(0)
    }

    @Test
    fun `the rows open their pages`() {
        setScreen()

        node(R.string.settings_appearance).assertHasClickAction().performClick()
        node(R.string.settings_data_storage).assertHasClickAction().performClick()
        node(R.string.settings_about).assertHasClickAction().performClick()
        node(R.string.settings_privacy_security).assertHasClickAction().performClick()

        assertEquals(
            listOf(SettingsPage.Appearance, SettingsPage.DataStorage, SettingsPage.About, SettingsPage.PrivacySecurity),
            pages,
        )
    }

    @Test
    fun `log out asks the view model for confirmation`() {
        setScreen()

        node(R.string.settings_log_out).performClick()

        assertEquals(listOf(SettingsEvent.LogOut), events)
    }

    @Test
    fun `log out is disabled while logging out`() {
        setScreen(SettingsPreviewData.content.copy(isLoggingOut = true))

        node(R.string.settings_log_out).assertIsNotEnabled()
    }

    @Test
    fun `the confirmation confirms or dismisses`() {
        setScreen(SettingsPreviewData.content.copy(isLogOutDialogOpen = true))

        composeRule
            .onNode(hasText(string(R.string.settings_log_out_dialog_confirm)) and hasAnyAncestor(isDialog()))
            .performClick()
        composeRule
            .onNode(hasText(string(R.string.settings_dialog_dismiss)) and hasAnyAncestor(isDialog()))
            .performClick()

        assertEquals(listOf(SettingsEvent.ConfirmLogOut, SettingsEvent.DismissLogOut), events)
    }
}
