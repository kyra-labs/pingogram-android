package dev.kyralabs.pingogram.feature.chats.ui.create

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.feature.chats.ui.COMPACT
import dev.kyralabs.pingogram.feature.chats.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chats.ui.EXPANDED
import dev.kyralabs.pingogram.feature.chats.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chats.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chats.ui.goldenPath
import dev.kyralabs.pingogram.feature.chats.ui.preview.CreateChatPreviewData
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the steps of creating a group or a channel (`docs/TESTING.md`) on a compact phone, light and dark, for
 * every state: picking people (with picks, no matches, loading, no contacts, an error), the group's details (empty,
 * filled in, a missing name, creating, a flood wait, people not added), the channel's details, and its type (private
 * with its link or its error, public while checking, available, taken, too short). The audit's other looks
 * (`docs/DESIGN_SYSTEM.md` §12: an expanded window, right to left, true black, high contrast) for one state of each
 * step. Brand colors, so goldens do not depend on a wallpaper.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CreateChatScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = captureAudit(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = captureAudit(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = captureAudit(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() = captureAudit(ScreenshotVariant.Medium, name = "expanded")

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the state with the most text: it grows and never clips. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "membersPicking" || stateName == "groupFilled" || stateName == "channelPrivate")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    /** The audit's other looks for one representative state of each step only. */
    private fun captureAudit(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        assumeTrue(stateName in REPRESENTATIVE)
        capture(variant, name)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val screen = SCREENS.getValue(stateName)
        composeRule.setContent { ScreenshotTheme(variant) { screen() } }
        composeRule.waitForIdle()
        // Loading indicators show once loading takes a moment; the golden shows them then.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private val REPRESENTATIVE = setOf("membersPicking", "groupFilled", "channelFilled", "channelPrivate")

        private fun members(state: MemberPickerUiState): @Composable () -> Unit =
            { MemberPickerScreen(state, "New group", "Next", onEvent = {}, onAction = {}, onBack = {}) }

        private fun group(state: NewGroupDetailsUiState): @Composable () -> Unit =
            { NewGroupDetailsScreen(state, onEvent = {}, onNotAddedEvent = {}, onPickPhoto = {}, onBack = {}) }

        private fun channel(state: NewChannelDetailsUiState): @Composable () -> Unit =
            { NewChannelDetailsScreen(state, onEvent = {}, onPickPhoto = {}, onBack = {}) }

        private fun type(state: NewChannelTypeUiState): @Composable () -> Unit =
            { NewChannelTypeScreen(state, onEvent = {}, onCopyLink = {}, onShareLink = {}) }

        internal val SCREENS: Map<String, @Composable () -> Unit> =
            with(CreateChatPreviewData) {
                mapOf(
                    "membersPicking" to members(picking),
                    "membersNoMatches" to members(noneMatching),
                    "membersLoading" to members(contactsLoading),
                    "membersEmpty" to members(noContacts),
                    "membersError" to members(contactsError),
                    "groupEmpty" to group(groupEmpty),
                    "groupFilled" to group(groupFilled),
                    "groupMissingName" to group(groupMissingName),
                    "groupCreating" to group(groupCreating),
                    "groupFloodWait" to group(groupFloodWait),
                    "groupNotAdded" to group(groupNotAdded),
                    "channelFilled" to channel(channelFilled),
                    "channelPrivate" to type(channelPrivate),
                    "channelLinkError" to type(channelLinkError),
                    "channelChecking" to type(channelChecking),
                    "channelAvailable" to type(channelAvailable),
                    "channelTaken" to type(channelTaken),
                    "channelTooShort" to type(channelTooShort),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = SCREENS.keys.map { arrayOf(it) }
    }
}
