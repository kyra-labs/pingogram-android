package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of [VoiceChatBar] (`docs/DESIGN_SYSTEM.md` §6, "Voice chat bar"): a voice chat to join, one the user is in,
 * a live stream and one whose count is not known yet; light, dark, AMOLED, high contrast, right to left, a 700 dp
 * window and 200% font. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class VoiceChatBarScreenshotTest {
    @Test
    fun light() = capture()

    @Test
    fun dark() = capture(ThemeMode.Dark)

    @Test
    fun amoled() = capture(ThemeMode.Dark, amoled = true)

    @Test
    fun highContrast() = capture(contrast = Contrast.High)

    @Test
    fun rtl() = capture(layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun medium() = capture()

    @Test
    @Config(fontScale = 2f)
    fun fontScale200() = capture()

    private fun capture(
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.small),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
                    ) {
                        VoiceChatBar(VoiceChatSummary(12, isLiveStream = false, isJoined = false), onJoin = {})
                        VoiceChatBar(VoiceChatSummary(1, isLiveStream = false, isJoined = true), onJoin = {})
                        VoiceChatBar(VoiceChatSummary(1_284, isLiveStream = true, isJoined = false), onJoin = {})
                        VoiceChatBar(VoiceChatSummary(null, isLiveStream = false, isJoined = false), onJoin = {})
                    }
                }
            }
        }
    }
}
