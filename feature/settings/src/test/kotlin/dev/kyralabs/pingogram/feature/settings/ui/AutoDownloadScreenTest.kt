package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadNetwork
import dev.kyralabs.pingogram.core.domain.model.AutoDownloadSettings
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

/** What the automatic download page sends: each switch, and each size limit through its slider. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AutoDownloadScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<AutoDownloadEvent>()
    private val wifi = AutoDownloadSettings.DefaultWiFiRules
    private val mobile = AutoDownloadSettings.DefaultMobileRules

    private fun setScreen(state: AutoDownloadUiState) {
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { AutoDownloadScreen(state = state, onEvent = { events += it }, onBack = {}) }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun slider(
        @StringRes title: Int,
    ) = composeRule.onNode(
        hasContentDescription(string(title)) and SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress),
    )

    @Test
    fun `each switch changes its kind of media`() {
        setScreen(SettingsPreviewData.autoDownloadWiFi)

        composeRule.onNodeWithText(string(R.string.settings_auto_download_photos)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_auto_download_videos)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_auto_download_files)).performClick()
        composeRule.onNodeWithText(string(R.string.settings_auto_download_voice)).performClick()

        assertEquals(
            listOf(
                wifi.copy(photos = false),
                wifi.copy(videos = false),
                wifi.copy(files = false),
                wifi.copy(voiceAndVideoMessages = false),
            ).map(AutoDownloadEvent::Change),
            events,
        )
    }

    @Test
    fun `a size limit moves by steps and says its limit`() {
        setScreen(SettingsPreviewData.autoDownloadWiFi)

        val videoLimit = slider(R.string.settings_auto_download_video_limit)
        videoLimit.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Up to 10 MB"))
        videoLimit.performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        slider(
            R.string.settings_auto_download_file_limit,
        ).performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }

        assertEquals(
            listOf(wifi.copy(videoSizeLimit = 15_000_000), wifi.copy(fileSizeLimit = 1_000_000))
                .map(AutoDownloadEvent::Change),
            events,
        )
    }

    @Test
    fun `a size limit is disabled while its switch is off`() {
        setScreen(AutoDownloadUiState(AutoDownloadNetwork.Mobile, rules = mobile))

        slider(R.string.settings_auto_download_video_limit).assertIsNotEnabled()
        slider(R.string.settings_auto_download_file_limit).assert(
            hasProgressBarRangeInfo(
                ProgressBarRangeInfo(
                    1f,
                    0f..12f,
                    11,
                ),
            ),
        )
    }

    @Test
    fun `Data Saver shows its note`() {
        setScreen(SettingsPreviewData.autoDownloadMobileDataSaver)

        composeRule.onNodeWithText(string(R.string.settings_auto_download_data_saver)).assertExists()
    }

    @Test
    fun `nothing shows before the settings are read`() {
        setScreen(AutoDownloadUiState(AutoDownloadNetwork.WiFi))

        composeRule.onNodeWithText(string(R.string.settings_auto_download_photos)).assertDoesNotExist()
    }
}
