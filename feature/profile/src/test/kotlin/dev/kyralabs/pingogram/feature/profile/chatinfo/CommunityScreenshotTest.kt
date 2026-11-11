package dev.kyralabs.pingogram.feature.profile.chatinfo

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityPreviewData
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityScreen
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.CommunityUiState
import dev.kyralabs.pingogram.feature.profile.chatinfo.community.RenameCommunityDialog
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.MEDIUM
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a community's page (`docs/DESIGN_SYSTEM.md` §6 "Community page", the owner's board 19a): the owner's in
 * every look the audit asks for, at a 700 dp width and at 200% font, a member's while a join is on its way, the
 * error, and the rename dialog's content.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CommunityScreenshotTest {
    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = "w411dp-h1800dp-xxhdpi", fontScale = 2f)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    fun memberJoiningDark() = capture(ScreenshotVariant.Dark, CommunityPreviewData.member)

    @Test
    fun error() = capture(ScreenshotVariant.Light, CommunityUiState.Error(DomainError.Network))

    @Test
    fun renameDialog() =
        captureRoboImage {
            ScreenshotTheme(ScreenshotVariant.Light) {
                RenameCommunityDialog("Kyra Labs Community", isBusy = false, onEvent = {})
            }
        }

    private fun capture(
        variant: ScreenshotVariant,
        state: CommunityUiState = CommunityPreviewData.owner,
    ) = captureRoboImage { ScreenshotTheme(variant) { CommunityScreen(state, onEvent = {}, onBack = {}) } }
}
