package dev.kyralabs.pingogram.feature.feedback

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.TimeZone

/**
 * Goldens of "Send feedback" (`docs/TESTING.md`): a report with a crash and a description, light and dark; without a
 * crash, its text shown; right to left; at 200% font scale. Brand colors, times in UTC.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class FeedbackScreenshotTest {
    private val timeZone = TimeZone.getDefault()

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun tearDown() {
        TimeZone.setDefault(timeZone)
    }

    @Test
    fun crashLight() = capture(ThemeMode.Light, FeedbackSamples.content(description = DESCRIPTION))

    @Test
    fun crashDark() = capture(ThemeMode.Dark, FeedbackSamples.content(description = DESCRIPTION))

    @Test
    fun reportShownLight() =
        capture(ThemeMode.Light, FeedbackSamples.content(FeedbackSamples.report.copy(crash = null), showReport = true))

    @Test
    fun rtlLight() = capture(ThemeMode.Light, FeedbackSamples.content(description = DESCRIPTION), LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun fontScale200Light() = capture(ThemeMode.Light, FeedbackSamples.content(description = DESCRIPTION))

    private fun capture(
        themeMode: ThemeMode,
        state: FeedbackUiState,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                FeedbackScreen(state = state, onEvent = {}, onBack = {})
            }
        }
    }

    private companion object {
        const val DESCRIPTION = "The chat closed when I sent a sticker."
    }
}

const val COMPACT = "w411dp-h891dp-xxhdpi"
