package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
 * What the Data and storage page shows and sends: TDLib's figures, "Clear cache" and its confirmation, progress while
 * clearing, and trying again after an error. The screen is stateless: these check its events.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DataStorageScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<DataStorageEvent>()
    private var backs = 0

    private fun setScreen(state: DataStorageUiState) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                DataStorageScreen(state = state, onEvent = { events += it }, onBack = { backs++ })
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun dialogButton(
        @StringRes id: Int,
    ) = composeRule.onNode(hasText(string(id)) and hasAnyAncestor(isDialog()))

    @Test
    fun `shows the sizes and the file count`() {
        setScreen(SettingsPreviewData.storage)

        composeRule.onNodeWithText("1.5 GB · 3,456 files").assertExists()
        composeRule.onNodeWithText("85 MB · chats, messages and settings").assertExists()
    }

    @Test
    fun `clear cache asks for confirmation`() {
        setScreen(SettingsPreviewData.storage)

        composeRule.onNodeWithText(string(R.string.settings_clear_cache)).assertHasClickAction().performClick()

        assertEquals(listOf(DataStorageEvent.ClearCache), events)
    }

    @Test
    fun `the confirmation clears the cache`() {
        setScreen(SettingsPreviewData.storage.copy(isClearCacheDialogOpen = true))

        dialogButton(R.string.settings_clear_cache_dialog_confirm).performClick()

        assertEquals(listOf(DataStorageEvent.ConfirmClearCache), events)
    }

    @Test
    fun `cancelling the confirmation dismisses it`() {
        setScreen(SettingsPreviewData.storage.copy(isClearCacheDialogOpen = true))

        dialogButton(R.string.settings_dialog_dismiss).performClick()

        assertEquals(listOf(DataStorageEvent.DismissClearCache), events)
    }

    @Test
    fun `no confirmation shows while it is closed`() {
        setScreen(SettingsPreviewData.storage)

        composeRule.onNode(isDialog()).assertDoesNotExist()
    }

    @Test
    fun `clear cache is disabled with progress while clearing`() {
        setScreen(SettingsPreviewData.storage.copy(isClearingCache = true))

        composeRule.onNodeWithText(string(R.string.settings_clear_cache)).assertIsNotEnabled()
        composeRule.onNode(hasContentDescription(string(R.string.settings_clearing_cache))).assertExists()
    }

    @Test
    fun `an error offers to try again`() {
        setScreen(SettingsPreviewData.storageError)

        composeRule.onNodeWithText(string(R.string.settings_retry)).performClick()

        assertEquals(listOf(DataStorageEvent.Retry), events)
    }

    @Test
    fun `back leaves the page`() {
        setScreen(SettingsPreviewData.storage)

        composeRule.onNodeWithContentDescription(string(R.string.settings_back)).performClick()

        assertEquals(1, backs)
        assertTrue(events.isEmpty())
    }
}
