package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import coil3.ColorImage
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The account's card at the top of Settings, on the page's color: without and with a photo (a solid color, which Coil
 * shows in inspection mode instead of loading from TDLib), with the Premium label and the Test server label, in light
 * and dark, and at the largest font scale.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AccountCardScreenshotTest {
    private val withPhoto = SettingsPreviewData.premiumAccount.copy(isPremium = false)

    @Test
    fun withoutPhotoLight() = capture(ThemeMode.Light, SettingsPreviewData.account)

    @Test
    fun withoutPhotoDark() = capture(ThemeMode.Dark, SettingsPreviewData.account)

    @Test
    fun withPhotoLight() = capture(ThemeMode.Light, withPhoto)

    @Test
    fun withPhotoDark() = capture(ThemeMode.Dark, withPhoto)

    @Test
    fun premiumLight() = capture(ThemeMode.Light, SettingsPreviewData.premiumAccount)

    @Test
    fun premiumDark() = capture(ThemeMode.Dark, SettingsPreviewData.premiumAccount)

    @Test
    fun testServerLight() = capture(ThemeMode.Light, SettingsPreviewData.testServerAccount)

    @Test
    fun testServerDark() = capture(ThemeMode.Dark, SettingsPreviewData.testServerAccount)

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun premiumOnTestServerFontScale200() =
        capture(ThemeMode.Light, SettingsPreviewData.testServerAccount.copy(isPremium = true))

    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun premiumFontScale200() = capture(ThemeMode.Light, SettingsPreviewData.premiumAccount)

    @OptIn(ExperimentalCoilApi::class)
    private fun capture(
        themeMode: ThemeMode,
        account: AccountUiModel,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalInspectionMode provides true) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                val photoColor = MaterialTheme.colorScheme.tertiary
                val photos = remember(photoColor) { AsyncImagePreviewHandler { ColorImage(photoColor.toArgb()) } }
                CompositionLocalProvider(LocalAsyncImagePreviewHandler provides photos) {
                    Box(
                        Modifier
                            .background(settingsPageColor())
                            .padding(MaterialTheme.spacing.large),
                    ) { AccountCard(account, onEditProfile = {}) }
                }
            }
        }
    }
}
