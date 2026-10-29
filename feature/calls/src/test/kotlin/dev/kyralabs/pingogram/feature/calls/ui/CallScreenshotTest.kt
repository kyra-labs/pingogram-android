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
 * rings, connected, a video call (both videos, the user's only, the other person's paused), reconnecting muted on a
 * Bluetooth headset, ended with an error, the microphone's rationale, and picture-in-picture with and without video;
 * light, dark, AMOLED, high contrast, right to left, 200% font scale, a phone in landscape and a 700 dp window. Brand
 * colors.
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
    fun incomingVideoLight() = capture(CallPreviewStates.incomingVideo)

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
    @Config(fontScale = 2f)
    fun connectedFontScale200() = capture(CallPreviewStates.connected)

    @Test
    @Config(fontScale = 2f)
    fun incomingFontScale200() = capture(CallPreviewStates.incoming)

    @Test
    fun shareOfferedLight() = capture(CallPreviewStates.shareOffered)

    @Test
    fun shareOfferedRtl() = capture(CallPreviewStates.shareOffered, layoutDirection = LayoutDirection.Rtl)

    @Test
    fun actionsOverVideoDark() = capture(CallPreviewStates.actionsOverVideo, ThemeMode.Dark)

    @Test
    fun sharingScreenLight() = capture(CallPreviewStates.sharingScreen)

    @Test
    fun sharingScreenDark() = capture(CallPreviewStates.sharingScreen, ThemeMode.Dark)

    @Test
    fun sharingScreenAmoled() = capture(CallPreviewStates.sharingScreen, ThemeMode.Dark, amoled = true)

    @Test
    fun sharingScreenHighContrast() = capture(CallPreviewStates.sharingScreen, contrast = Contrast.High)

    @Test
    fun sharingScreenRtl() = capture(CallPreviewStates.sharingScreen, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun sharingScreenMedium() = capture(CallPreviewStates.sharingScreen)

    @Test
    @Config(fontScale = 2f)
    fun sharingScreenFontScale200() = capture(CallPreviewStates.sharingScreen)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun sharingScreenLandscape() = capture(CallPreviewStates.sharingScreen)

    @Test
    fun sharingScreenVideoLight() = capture(CallPreviewStates.sharingScreenVideo)

    @Test
    fun remoteScreenDark() = capture(CallPreviewStates.remoteScreen, ThemeMode.Dark)

    @Test
    fun videoLight() = capture(CallPreviewStates.video)

    @Test
    fun videoDark() = capture(CallPreviewStates.video, ThemeMode.Dark)

    @Test
    fun videoRtl() = capture(CallPreviewStates.video, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun videoLandscape() = capture(CallPreviewStates.video, ThemeMode.Dark)

    @Test
    @Config(fontScale = 2f)
    fun videoFontScale200() = capture(CallPreviewStates.video)

    @Test
    fun videoSelfOnlyLight() = capture(CallPreviewStates.videoSelfOnly)

    @Test
    fun videoPausedDark() = capture(CallPreviewStates.videoPaused, ThemeMode.Dark)

    @Test
    @Config(qualifiers = "w240dp-h427dp-xxhdpi")
    fun videoPictureInPicture() = capture(CallPreviewStates.video, pictureInPicture = true)

    @Test
    @Config(qualifiers = "w240dp-h427dp-xxhdpi")
    fun pictureInPictureWithoutVideo() = capture(CallPreviewStates.connected, ThemeMode.Dark, pictureInPicture = true)

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
        pictureInPicture: Boolean = false,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                CallScreen(state, {}, {}, {}, {}, pictureInPicture = pictureInPicture)
            }
        }
    }
}

const val COMPACT = "w411dp-h891dp-xxhdpi"
