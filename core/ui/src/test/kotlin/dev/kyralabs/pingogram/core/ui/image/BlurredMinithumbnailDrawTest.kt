package dev.kyralabs.pingogram.core.ui.image

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * [BlurredMinithumbnail] under an image: drawn until the image covers it, then not drawn at all, without composing
 * again (`docs/audit/performance-review-2026-11.md`, M1 and W1: the conversation drew every photo's placeholder under
 * its loaded image).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BlurredMinithumbnailDrawTest {
    // Declares the activity to Robolectric, as ui-test-manifest would.
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

    @Test
    fun `stops drawing once covered, in the draw phase`() {
        var covered by mutableStateOf(false)
        var compositions = 0
        composeRule.setContent {
            SideEffect { compositions++ }
            BlurredMinithumbnail(
                minithumbnail = null,
                fallback = Color.Red,
                modifier = Modifier.size(SIZE.dp).testTag(TAG),
                covered = { covered },
            )
        }
        assertEquals(Color.Red, centre())

        covered = true
        composeRule.waitForIdle()

        assertNotEquals(Color.Red, centre())
        assertEquals("composed again", 1, compositions)
    }

    private fun centre(): Color {
        val pixels = composeRule.onNodeWithTag(TAG).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private companion object {
        const val TAG = "placeholder"
        const val SIZE = 40
    }
}
