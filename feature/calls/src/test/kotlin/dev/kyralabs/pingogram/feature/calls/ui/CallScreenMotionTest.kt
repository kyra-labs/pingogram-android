package dev.kyralabs.pingogram.feature.calls.ui

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The call screen's layouts as the video turns off (`docs/DESIGN_SYSTEM.md` §5.1, Q-15): the video layout crossfades
 * into the voice one on the effects springs, and changes at once while power saving holds the call screens still.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CallScreenMotionTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                shadowOf(application.packageManager)
                    .addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var state by mutableStateOf<CallUiState>(CallPreviewStates.video)

    @Test
    fun `the video layout fades out over several frames as the video turns off`() {
        setScreen(still = false)

        state = CallPreviewStates.connected
        frames(2)

        assertEquals("the video still fades out", 1, videoNodes())
        frames(SETTLE_FRAMES)
        assertEquals(0, videoNodes())
    }

    @Test
    fun `while power saving holds the call screens still, the voice layout replaces the video at once`() {
        setScreen(still = true)

        state = CallPreviewStates.connected
        // The frame that swaps them, and the one after, which removes what left.
        frames(2)

        assertEquals(0, videoNodes())
    }

    private fun videoNodes(): Int {
        val name = CallPreviewStates.connected.person.name
        val description = composeRule.activity.getString(R.string.call_remote_video, name)
        return composeRule.onAllNodesWithContentDescription(description).fetchSemanticsNodes().size
    }

    private fun frames(count: Int) {
        // The writes reach the recomposer first, so the first frame composes them.
        Snapshot.sendApplyNotifications()
        repeat(count) { composeRule.mainClock.advanceTimeByFrame() }
        composeRule.waitForIdle()
    }

    private fun setScreen(still: Boolean) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalStillCallAnimations provides still) {
                    CallScreen(state, {}, {}, {}, {})
                }
            }
        }
        frames(2)
        assertEquals(1, videoNodes())
    }

    private companion object {
        const val SETTLE_FRAMES = 30
    }
}
