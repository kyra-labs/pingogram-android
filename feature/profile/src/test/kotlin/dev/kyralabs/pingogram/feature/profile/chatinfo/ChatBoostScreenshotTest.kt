package dev.kyralabs.pingogram.feature.profile.chatinfo

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.profile.ui.COMPACT
import dev.kyralabs.pingogram.feature.profile.ui.EXPANDED
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.profile.ui.ScreenshotVariant
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the boosts page (`docs/TESTING.md`): a channel on its way to the next level, which the user boosts, in
 * light, dark and AMOLED, on an expanded window; a group at the highest level; the error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatBoostScreenshotTest {
    private val channel = ChatBoostUiState.Content(PreviewBoostStatus, isChannel = true)

    @Test
    fun channelLight() = capture(ScreenshotVariant.Light, channel)

    @Test
    fun channelDark() = capture(ScreenshotVariant.Dark, channel)

    @Test
    fun channelAmoled() = capture(ScreenshotVariant.Amoled, channel)

    @Test
    @Config(qualifiers = EXPANDED)
    fun channelExpanded() = capture(ScreenshotVariant.Light, channel)

    @Test
    fun groupMaxLevel() =
        capture(
            ScreenshotVariant.Light,
            ChatBoostUiState.Content(
                PreviewBoostStatus.copy(level = 10, boostCount = 120, nextLevelBoostCount = 0, unusedBoosts = 0),
                isChannel = false,
            ),
        )

    @Test
    fun error() = capture(ScreenshotVariant.Light, ChatBoostUiState.Error(DomainError.Network))

    private fun capture(
        variant: ScreenshotVariant,
        state: ChatBoostUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { ChatBoostScreen(state, {}, {}) } }
}
