package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the call screen (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §12): an incoming call, an outgoing one that
 * rings, connected, reconnecting muted on a Bluetooth headset, ended with an error, and the microphone's rationale;
 * light, dark, AMOLED, high contrast, right to left, a phone in landscape and a 700 dp window. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CallScreenshotTest {
    @Test
    fun incomingLight() = capture(CallPreviewStates.incoming)

    @Test
    fun incomingDark() = capture(CallPreviewStates.incoming, ThemeMode.Dark)

    @Test
    fun incomingRtl() = capture(CallPreviewStates.incoming, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun incomingLandscape() = capture(CallPreviewStates.incoming)

    @Test
    fun ringingLight() = capture(CallPreviewStates.ringing)

    @Test
    fun connectedLight() = capture(CallPreviewStates.connected)

    @Test
    fun connectedDark() = capture(CallPreviewStates.connected, ThemeMode.Dark)

    @Test
    fun connectedAmoled() = capture(CallPreviewStates.connected, ThemeMode.Dark, amoled = true)

    @Test
    fun connectedHighContrast() = capture(CallPreviewStates.connected, contrast = Contrast.High)

    @Test
    fun connectedRtl() = capture(CallPreviewStates.connected, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun connectedLandscape() = capture(CallPreviewStates.connected, ThemeMode.Dark)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun connectedMedium() = capture(CallPreviewStates.connected)

    @Test
    fun reconnectingDark() = capture(CallPreviewStates.reconnecting, ThemeMode.Dark)

    @Test
    fun endedLight() = capture(CallPreviewStates.ended)

    @Test
    fun microphoneLight() = capture(CallPreviewStates.microphone)

    private fun capture(
        state: CallUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                CallScreen(state, {}, {}, {}, {})
            }
        }
    }
}

const val COMPACT = "w411dp-h891dp-xxhdpi"
