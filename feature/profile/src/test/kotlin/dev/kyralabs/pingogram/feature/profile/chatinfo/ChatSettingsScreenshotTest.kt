package dev.kyralabs.pingogram.feature.profile.chatinfo

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.ChatSettingsUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.DiscussionPicker
import dev.kyralabs.pingogram.feature.profile.chatinfo.settings.DiscussionPickerContent
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a group's or channel's settings page (`docs/TESTING.md`): a channel in light, dark, AMOLED, high
 * contrast, right to left, a 700 dp window and twice the font size; a supergroup, a basic group and its upgrade
 * question, saving, the error, and the discussion picker.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h1400dp-xxhdpi")
class ChatSettingsScreenshotTest {
    @Test
    fun channelLight() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.channel)

    @Test
    fun channelDark() = capture(ScreenshotVariant.Dark, ChatSettingsPreviewData.channel)

    @Test
    fun channelAmoled() = capture(ScreenshotVariant.Amoled, ChatSettingsPreviewData.channel)

    @Test
    fun channelHighContrast() = capture(ScreenshotVariant.HighContrast, ChatSettingsPreviewData.channel)

    @Test
    fun channelRtl() = capture(ScreenshotVariant.Rtl, ChatSettingsPreviewData.channel)

    @Test
    @Config(qualifiers = MEDIUM)
    fun channelMedium() = capture(ScreenshotVariant.Medium, ChatSettingsPreviewData.channel)

    @Test
    @Config(qualifiers = "w411dp-h2400dp-xxhdpi", fontScale = 2f)
    fun channelFontScale200() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.channel)

    @Test
    fun supergroupLight() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.supergroup)

    @Test
    fun supergroupDark() = capture(ScreenshotVariant.Dark, ChatSettingsPreviewData.supergroup)

    @Test
    @Config(qualifiers = COMPACT)
    fun basicGroupLight() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.basicGroup)

    @Test
    @Config(qualifiers = COMPACT)
    fun upgradeDialog() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.upgradeDialog)

    @Test
    fun savingLight() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.saving)

    @Test
    @Config(qualifiers = COMPACT)
    fun error() = capture(ScreenshotVariant.Light, ChatSettingsPreviewData.error)

    @Test
    @Config(qualifiers = COMPACT)
    fun pickerLight() = capturePicker(ScreenshotVariant.Light, ChatSettingsPreviewData.pickerGroups)

    @Test
    @Config(qualifiers = COMPACT)
    fun pickerDark() = capturePicker(ScreenshotVariant.Dark, ChatSettingsPreviewData.pickerGroups)

    @Test
    @Config(qualifiers = COMPACT)
    fun pickerEmpty() = capturePicker(ScreenshotVariant.Light, ChatSettingsPreviewData.pickerEmpty)

    private fun capture(
        variant: ScreenshotVariant,
        state: ChatSettingsUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { ChatSettingsScreen(state, {}, {}) } }

    private fun capturePicker(
        variant: ScreenshotVariant,
        picker: DiscussionPicker,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) { DiscussionPickerContent(picker, {}) }
        }
    }
}
