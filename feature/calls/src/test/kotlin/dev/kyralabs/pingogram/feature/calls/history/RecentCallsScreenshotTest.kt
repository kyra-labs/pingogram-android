package dev.kyralabs.pingogram.feature.calls.history

import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.calls.ui.COMPACT
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Goldens of Recent calls (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §12): the calls, no calls and a failure in
 * light, dark, AMOLED, high contrast, right to left, a 700 dp window and 200% font scale; loading, the next page on its
 * way or failed, and no missed calls in light and dark. Brand colors. On the test clock, so the loading indicator
 * shows the same frame in every run.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@Suppress("TooManyFunctions") // One golden per state and look.
@OptIn(ExperimentalRoborazziApi::class)
class RecentCallsScreenshotTest {
    @get:Rule(order = 0)
    val activityRegistration =
        object : ExternalResource() {
            override fun before() {
                val application = RuntimeEnvironment.getApplication()
                val packageManager = shadowOf(application.packageManager)
                val activity =
                    packageManager.addActivityIfNotPresent(ComponentName(application, ComponentActivity::class.java))
                // No action bar, as the app's activities have none.
                activity.theme = android.R.style.Theme_Material_Light_NoActionBar
                packageManager.addOrUpdateActivity(activity)
            }
        }

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule(order = 2)
    val testName = TestName()

    @Test
    fun callsLight() = capture(RecentCallsPreviewStates.calls)

    @Test
    fun callsDark() = capture(RecentCallsPreviewStates.calls, ThemeMode.Dark)

    @Test
    fun callsAmoled() = capture(RecentCallsPreviewStates.calls, ThemeMode.Dark, amoled = true)

    @Test
    fun callsHighContrast() = capture(RecentCallsPreviewStates.calls, contrast = Contrast.High)

    @Test
    fun callsRtl() = capture(RecentCallsPreviewStates.calls, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun callsMedium() = capture(RecentCallsPreviewStates.calls)

    @Test
    @Config(fontScale = 2f)
    fun callsFontScale200() = capture(RecentCallsPreviewStates.calls)

    @Test
    fun loadingLight() = capture(RecentCallsPreviewStates.loading)

    @Test
    fun loadingDark() = capture(RecentCallsPreviewStates.loading, ThemeMode.Dark)

    @Test
    fun loadingMoreLight() = capture(RecentCallsPreviewStates.loadingMore)

    @Test
    fun loadingMoreDark() = capture(RecentCallsPreviewStates.loadingMore, ThemeMode.Dark)

    @Test
    fun pageErrorLight() = capture(RecentCallsPreviewStates.pageError)

    @Test
    fun pageErrorDark() = capture(RecentCallsPreviewStates.pageError, ThemeMode.Dark)

    @Test
    fun emptyLight() = capture(RecentCallsPreviewStates.empty)

    @Test
    fun emptyDark() = capture(RecentCallsPreviewStates.empty, ThemeMode.Dark)

    @Test
    fun emptyAmoled() = capture(RecentCallsPreviewStates.empty, ThemeMode.Dark, amoled = true)

    @Test
    fun emptyHighContrast() = capture(RecentCallsPreviewStates.empty, contrast = Contrast.High)

    @Test
    fun emptyRtl() = capture(RecentCallsPreviewStates.empty, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun emptyMedium() = capture(RecentCallsPreviewStates.empty)

    @Test
    @Config(fontScale = 2f)
    fun emptyFontScale200() = capture(RecentCallsPreviewStates.empty)

    @Test
    fun noMissedLight() = capture(RecentCallsPreviewStates.noMissed)

    @Test
    fun noMissedDark() = capture(RecentCallsPreviewStates.noMissed, ThemeMode.Dark)

    @Test
    fun errorLight() = capture(RecentCallsPreviewStates.error)

    @Test
    fun errorDark() = capture(RecentCallsPreviewStates.error, ThemeMode.Dark)

    @Test
    fun errorAmoled() = capture(RecentCallsPreviewStates.error, ThemeMode.Dark, amoled = true)

    @Test
    fun errorHighContrast() = capture(RecentCallsPreviewStates.error, contrast = Contrast.High)

    @Test
    fun errorRtl() = capture(RecentCallsPreviewStates.error, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun errorMedium() = capture(RecentCallsPreviewStates.error)

    @Test
    @Config(fontScale = 2f)
    fun errorFontScale200() = capture(RecentCallsPreviewStates.error)

    private fun capture(
        state: RecentCallsUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                    RecentCallsScreen(state, onEvent = {}, onBack = {})
                }
            }
        }
        // Past the loading indicator's delay, at the same frame of its animation in every run.
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds + FRAME_MILLIS)
        captureScreenRoboImage("src/test/snapshots/${javaClass.name}.${testName.methodName}.png")
    }

    private companion object {
        /** One frame at 60 Hz, past the delay. */
        const val FRAME_MILLIS = 16L
    }
}
