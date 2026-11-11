package dev.kyralabs.pingogram.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of [BirthdaySuggestionCard] (`docs/DESIGN_SYSTEM.md` §6 "Birthday suggestions", the owner's board 16b): a
 * suggestion waiting for the user and one the user sent, in light, dark, AMOLED, high contrast, right to left, a 700 dp
 * window and 200% font; and the answers (accepted, declined). Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class BirthdaySuggestionCardScreenshotTest {
    private val states = BirthdaySuggestionPreviewStates().values.toList()
    private val waiting = listOf(states[0], states[4])

    @Test
    fun light() = capture(waiting)

    @Test
    fun dark() = capture(waiting, ThemeMode.Dark)

    @Test
    fun amoled() = capture(waiting, ThemeMode.Dark, amoled = true)

    @Test
    fun highContrast() = capture(waiting, contrast = Contrast.High)

    @Test
    fun rtl() = capture(waiting, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun medium() = capture(waiting)

    @Test
    @Config(qualifiers = "w411dp-h1600dp-xxhdpi", fontScale = 2f)
    fun fontScale200() = capture(listOf(states[0]))

    // Accepting is left out: its LoadingIndicator moves in every frame, so no golden of it would hold.
    @Test
    fun answers() = capture(states.subList(2, 4))

    private fun capture(
        suggestions: List<BirthdaySuggestion>,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column(
                        modifier = Modifier.padding(MaterialTheme.spacing.medium),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
                    ) {
                        suggestions.forEach { BirthdaySuggestionCard(it, onAccept = {}, onDecline = {}) }
                    }
                }
            }
        }
    }
}
