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
 * Goldens of the voice chat's screen (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §12): joining, connected with its
 * participants, sending the camera (and offered it), others' videos (a pinned one, one alone), sharing the screen
 * and others' screens, muted by an administrator (Raise hand), a live stream, ended, the microphone's rationale,
 * joining refused, a conference call's link and a conference call; each in light, dark, AMOLED, high contrast, right
 * to left, a 700 dp window and 200% font scale; then a phone in landscape and a live stream in picture-in-picture.
 * Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@Suppress("TooManyFunctions") // One golden per state and look.
class GroupCallScreenshotTest {
    @Test
    fun joiningLight() = capture(GroupCallPreviewStates.joining)

    @Test
    fun joiningDark() = capture(GroupCallPreviewStates.joining, ThemeMode.Dark)

    @Test
    fun joiningAmoled() = capture(GroupCallPreviewStates.joining, ThemeMode.Dark, amoled = true)

    @Test
    fun joiningHighContrast() = capture(GroupCallPreviewStates.joining, contrast = Contrast.High)

    @Test
    fun joiningRtl() = capture(GroupCallPreviewStates.joining, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun joiningMedium() = capture(GroupCallPreviewStates.joining)

    @Test
    @Config(fontScale = 2f)
    fun joiningFontScale200() = capture(GroupCallPreviewStates.joining)

    @Test
    fun connectedLight() = capture(GroupCallPreviewStates.connected)

    @Test
    fun connectedDark() = capture(GroupCallPreviewStates.connected, ThemeMode.Dark)

    @Test
    fun connectedAmoled() = capture(GroupCallPreviewStates.connected, ThemeMode.Dark, amoled = true)

    @Test
    fun connectedHighContrast() = capture(GroupCallPreviewStates.connected, contrast = Contrast.High)

    @Test
    fun connectedRtl() = capture(GroupCallPreviewStates.connected, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun connectedMedium() = capture(GroupCallPreviewStates.connected)

    @Test
    @Config(fontScale = 2f)
    fun connectedFontScale200() = capture(GroupCallPreviewStates.connected)

    @Test
    fun cameraOfferedLight() = capture(GroupCallPreviewStates.cameraOffered)

    @Test
    fun cameraLight() = capture(GroupCallPreviewStates.camera)

    @Test
    fun cameraDark() = capture(GroupCallPreviewStates.camera, ThemeMode.Dark)

    @Test
    fun cameraAmoled() = capture(GroupCallPreviewStates.camera, ThemeMode.Dark, amoled = true)

    @Test
    fun cameraHighContrast() = capture(GroupCallPreviewStates.camera, contrast = Contrast.High)

    @Test
    fun cameraRtl() = capture(GroupCallPreviewStates.camera, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun cameraMedium() = capture(GroupCallPreviewStates.camera)

    @Test
    @Config(fontScale = 2f)
    fun cameraFontScale200() = capture(GroupCallPreviewStates.camera)

    @Test
    fun videosLight() = capture(GroupCallPreviewStates.videos)

    @Test
    fun videosDark() = capture(GroupCallPreviewStates.videos, ThemeMode.Dark)

    @Test
    fun videosAmoled() = capture(GroupCallPreviewStates.videos, ThemeMode.Dark, amoled = true)

    @Test
    fun videosHighContrast() = capture(GroupCallPreviewStates.videos, contrast = Contrast.High)

    @Test
    fun videosRtl() = capture(GroupCallPreviewStates.videos, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun videosMedium() = capture(GroupCallPreviewStates.videos)

    @Test
    @Config(fontScale = 2f)
    fun videosFontScale200() = capture(GroupCallPreviewStates.videos)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun videosLandscape() = capture(GroupCallPreviewStates.videos)

    @Test
    fun videoLight() = capture(GroupCallPreviewStates.video)

    @Test
    fun sharingScreenLight() = capture(GroupCallPreviewStates.sharingScreen)

    @Test
    fun sharingScreenDark() = capture(GroupCallPreviewStates.sharingScreen, ThemeMode.Dark)

    @Test
    fun sharingScreenAmoled() = capture(GroupCallPreviewStates.sharingScreen, ThemeMode.Dark, amoled = true)

    @Test
    fun sharingScreenHighContrast() = capture(GroupCallPreviewStates.sharingScreen, contrast = Contrast.High)

    @Test
    fun sharingScreenRtl() = capture(GroupCallPreviewStates.sharingScreen, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun sharingScreenMedium() = capture(GroupCallPreviewStates.sharingScreen)

    @Test
    @Config(fontScale = 2f)
    fun sharingScreenFontScale200() = capture(GroupCallPreviewStates.sharingScreen)

    @Test
    fun screensLight() = capture(GroupCallPreviewStates.screens)

    @Test
    fun raiseHandLight() = capture(GroupCallPreviewStates.raiseHand)

    @Test
    fun raiseHandDark() = capture(GroupCallPreviewStates.raiseHand, ThemeMode.Dark)

    @Test
    fun raiseHandAmoled() = capture(GroupCallPreviewStates.raiseHand, ThemeMode.Dark, amoled = true)

    @Test
    fun raiseHandHighContrast() = capture(GroupCallPreviewStates.raiseHand, contrast = Contrast.High)

    @Test
    fun raiseHandRtl() = capture(GroupCallPreviewStates.raiseHand, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun raiseHandMedium() = capture(GroupCallPreviewStates.raiseHand)

    @Test
    @Config(fontScale = 2f)
    fun raiseHandFontScale200() = capture(GroupCallPreviewStates.raiseHand)

    @Test
    fun streamLight() = capture(GroupCallPreviewStates.stream)

    @Test
    fun streamDark() = capture(GroupCallPreviewStates.stream, ThemeMode.Dark)

    @Test
    fun streamAmoled() = capture(GroupCallPreviewStates.stream, ThemeMode.Dark, amoled = true)

    @Test
    fun streamHighContrast() = capture(GroupCallPreviewStates.stream, contrast = Contrast.High)

    @Test
    fun streamRtl() = capture(GroupCallPreviewStates.stream, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun streamMedium() = capture(GroupCallPreviewStates.stream)

    @Test
    @Config(fontScale = 2f)
    fun streamFontScale200() = capture(GroupCallPreviewStates.stream)

    @Test
    fun endedLight() = capture(GroupCallPreviewStates.ended)

    @Test
    fun endedDark() = capture(GroupCallPreviewStates.ended, ThemeMode.Dark)

    @Test
    fun endedAmoled() = capture(GroupCallPreviewStates.ended, ThemeMode.Dark, amoled = true)

    @Test
    fun endedHighContrast() = capture(GroupCallPreviewStates.ended, contrast = Contrast.High)

    @Test
    fun endedRtl() = capture(GroupCallPreviewStates.ended, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun endedMedium() = capture(GroupCallPreviewStates.ended)

    @Test
    @Config(fontScale = 2f)
    fun endedFontScale200() = capture(GroupCallPreviewStates.ended)

    @Test
    fun microphoneLight() = capture(GroupCallPreviewStates.microphone)

    @Test
    fun microphoneDark() = capture(GroupCallPreviewStates.microphone, ThemeMode.Dark)

    @Test
    fun microphoneAmoled() = capture(GroupCallPreviewStates.microphone, ThemeMode.Dark, amoled = true)

    @Test
    fun microphoneHighContrast() = capture(GroupCallPreviewStates.microphone, contrast = Contrast.High)

    @Test
    fun microphoneRtl() = capture(GroupCallPreviewStates.microphone, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun microphoneMedium() = capture(GroupCallPreviewStates.microphone)

    @Test
    @Config(fontScale = 2f)
    fun microphoneFontScale200() = capture(GroupCallPreviewStates.microphone)

    @Test
    fun unavailableLight() = capture(GroupCallPreviewStates.unavailable)

    @Test
    fun unavailableDark() = capture(GroupCallPreviewStates.unavailable, ThemeMode.Dark)

    @Test
    fun unavailableAmoled() = capture(GroupCallPreviewStates.unavailable, ThemeMode.Dark, amoled = true)

    @Test
    fun unavailableHighContrast() = capture(GroupCallPreviewStates.unavailable, contrast = Contrast.High)

    @Test
    fun unavailableRtl() = capture(GroupCallPreviewStates.unavailable, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun unavailableMedium() = capture(GroupCallPreviewStates.unavailable)

    @Test
    @Config(fontScale = 2f)
    fun unavailableFontScale200() = capture(GroupCallPreviewStates.unavailable)

    @Test
    fun confirmConferenceLight() = capture(GroupCallPreviewStates.confirmConference)

    @Test
    fun confirmConferenceDark() = capture(GroupCallPreviewStates.confirmConference, ThemeMode.Dark)

    @Test
    fun confirmConferenceAmoled() = capture(GroupCallPreviewStates.confirmConference, ThemeMode.Dark, amoled = true)

    @Test
    fun confirmConferenceHighContrast() = capture(GroupCallPreviewStates.confirmConference, contrast = Contrast.High)

    @Test
    fun confirmConferenceRtl() =
        capture(GroupCallPreviewStates.confirmConference, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun confirmConferenceMedium() = capture(GroupCallPreviewStates.confirmConference)

    @Test
    @Config(fontScale = 2f)
    fun confirmConferenceFontScale200() = capture(GroupCallPreviewStates.confirmConference)

    @Test
    fun conferenceLight() = capture(GroupCallPreviewStates.conference)

    @Test
    fun conferenceDark() = capture(GroupCallPreviewStates.conference, ThemeMode.Dark)

    @Test
    fun conferenceAmoled() = capture(GroupCallPreviewStates.conference, ThemeMode.Dark, amoled = true)

    @Test
    fun conferenceHighContrast() = capture(GroupCallPreviewStates.conference, contrast = Contrast.High)

    @Test
    fun conferenceRtl() = capture(GroupCallPreviewStates.conference, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun conferenceMedium() = capture(GroupCallPreviewStates.conference)

    @Test
    @Config(fontScale = 2f)
    fun conferenceFontScale200() = capture(GroupCallPreviewStates.conference)

    @Test
    @Config(qualifiers = "w891dp-h411dp-xxhdpi")
    fun connectedLandscape() = capture(GroupCallPreviewStates.connected)

    @Test
    @Config(qualifiers = "w427dp-h240dp-xxhdpi")
    fun streamPictureInPicture() = capture(GroupCallPreviewStates.stream, ThemeMode.Dark, pictureInPicture = true)

    private fun capture(
        state: GroupCallUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        pictureInPicture: Boolean = false,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                GroupCallScreen(state, {}, {}, {}, {}, pictureInPicture = pictureInPicture)
            }
        }
    }
}
