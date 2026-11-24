package dev.kyralabs.pingogram.feature.contacts.ui.birthdays

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.contacts.ui.COMPACT
import dev.kyralabs.pingogram.feature.contacts.ui.COMPACT_TALL
import dev.kyralabs.pingogram.feature.contacts.ui.FONT_SCALE_200
import dev.kyralabs.pingogram.feature.contacts.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.contacts.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.contacts.ui.preview.BirthdaysPreviewStates
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the Birthdays page (`docs/DESIGN_SYSTEM.md` §6 "Birthdays"): its three groups light and dark, in AMOLED
 * black, high contrast, right to left and at twice the font size, and the empty state.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class BirthdaysScreenshotTest {
    private val content = BirthdaysPreviewStates().values.first()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = COMPACT_TALL, fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    fun emptyLight() = capture(ScreenshotVariant.Light, BirthdaysUiState.Empty)

    private fun capture(
        variant: ScreenshotVariant,
        state: BirthdaysUiState = content,
    ) = captureRoboImage {
        ScreenshotTheme(variant) { BirthdaysScreen(state = state, onEvent = {}, onBack = {}) }
    }
}
