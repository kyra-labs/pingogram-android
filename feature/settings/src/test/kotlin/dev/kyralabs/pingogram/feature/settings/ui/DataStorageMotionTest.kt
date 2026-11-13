package dev.kyralabs.pingogram.feature.settings.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Data and storage's card grows in (`docs/plans/motion-expressive-plan.md` C-2, M-105): when the statistics answer,
 * the first group below it moves down over several frames rather than jumping in one.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class DataStorageMotionTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf(SettingsPreviewData.storage.copy(usage = StorageUsageState.Loading))

    @Test
    fun `the first group moves down over several frames as the card arrives`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                DataStorageScreen(
                    state = state,
                    onEvent = {},
                    onOpenAutoDownload = {},
                    onOpenProxies = {},
                    onOpenNetworkUsage = {},
                    onBack = {},
                )
            }
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        val before = groupTop()

        state = SettingsPreviewData.storage
        val tops =
            List(FRAMES) {
                composeRule.mainClock.advanceTimeByFrame()
                composeRule.waitForIdle()
                groupTop()
            }

        val moves = (listOf(before) + tops).zipWithNext().count { (a, b) -> b > a }
        assertTrue("tops $before, $tops", tops.last() > before)
        assertTrue("moved in $moves frames: $before, $tops", moves > 2)
    }

    private fun groupTop(): Dp = composeRule.onNodeWithText(AUTO_DOWNLOAD).getUnclippedBoundsInRoot().top

    private companion object {
        const val AUTO_DOWNLOAD = "Automatic download"
        const val FRAMES = 30
    }
}
