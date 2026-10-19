package dev.kyralabs.pingogram.core.ui.component

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/** [DelayedLoadingIndicator] composes nothing until loading has taken [LoadingIndicatorDelay], then the indicator. */
@RunWith(RobolectricTestRunner::class)
class DelayedLoadingIndicatorTest {
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
    fun `nothing shows while loading is shorter than the delay`() {
        setIndicator()

        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds - FRAME_MILLIS)

        composeRule.onNodeWithTag(INDICATOR).assertDoesNotExist()
    }

    @Test
    fun `the indicator shows once loading takes the delay`() {
        setIndicator()

        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds + FRAME_MILLIS)

        composeRule.onNodeWithTag(INDICATOR).assertIsDisplayed()
    }

    private fun setIndicator() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { DelayedLoadingIndicator(Modifier.testTag(INDICATOR)) }
        }
    }

    private companion object {
        const val INDICATOR = "indicator"
        const val FRAME_MILLIS = 16L
    }
}
