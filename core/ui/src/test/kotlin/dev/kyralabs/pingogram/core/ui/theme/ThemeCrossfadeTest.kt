package dev.kyralabs.pingogram.core.ui.theme

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * [PingogramTheme] cross-fades its colors when a setting changes (`docs/DESIGN_SYSTEM.md` §1, §5), and shows its first
 * colors at once, so the app never starts with a crossfade.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeCrossfadeTest {
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

    private val light = brandColorScheme(dark = false, contrast = Contrast.Standard)
    private val dark = brandColorScheme(dark = true, contrast = Contrast.Standard)

    private var themeMode by mutableStateOf(ThemeMode.Light)
    private var surface = Color.Unspecified

    /** Shows the theme, then stops the clock, so the test moves it frame by frame. */
    private fun setTheme() {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                surface = MaterialTheme.colorScheme.surface
            }
        }
        composeRule.waitForIdle()
        composeRule.mainClock.autoAdvance = false
    }

    /** Changes the theme mode; the next frames show the change. */
    private fun change(mode: ThemeMode) {
        themeMode = mode
        // Delivers the change to the composition without moving the clock.
        composeRule.waitForIdle()
    }

    @Test
    fun `the first colors show at once`() {
        setTheme()

        assertEquals(light.surface, surface)
    }

    @Test
    fun `a theme change cross-fades the colors to the new theme`() {
        setTheme()

        change(ThemeMode.Dark)
        composeRule.mainClock.advanceTimeBy(CROSSFADE_PART_MILLIS)

        assertNotEquals(light.surface, surface)
        assertNotEquals(dark.surface, surface)

        composeRule.mainClock.advanceTimeBy(CROSSFADE_END_MILLIS)

        assertEquals(dark.surface, surface)
    }

    @Test
    fun `a change during a crossfade continues to the newest theme`() {
        setTheme()
        change(ThemeMode.Dark)
        composeRule.mainClock.advanceTimeBy(CROSSFADE_PART_MILLIS)

        change(ThemeMode.Light)
        composeRule.mainClock.advanceTimeBy(CROSSFADE_PART_MILLIS)
        assertNotEquals(light.surface, surface)
        composeRule.mainClock.advanceTimeBy(CROSSFADE_END_MILLIS)

        assertEquals(light.surface, surface)
    }

    private companion object {
        /** Well into the default effects spring, which settles in about 300 ms. */
        const val CROSSFADE_PART_MILLIS = 64L

        /** Long after the spring settled. */
        const val CROSSFADE_END_MILLIS = 2_000L
    }
}
