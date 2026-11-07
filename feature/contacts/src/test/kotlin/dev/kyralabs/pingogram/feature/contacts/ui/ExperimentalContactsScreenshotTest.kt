package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.ui.component.LocalExperimentalUi
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the Contacts tab in the experimental look (`docs/DESIGN_SYSTEM.md` §13.8): the header with its status line
 * and the Invite / My QR / Scan group, contacts sync's offer, "Online now", the condensed letters, in every look the
 * audit asks for and at 200% font; the long list with the index's pill; and the states the experimental look leaves as
 * they are (sync on, search results, no contacts).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ExperimentalContactsScreenshotTest {
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
    @Config(qualifiers = EXPANDED)
    fun expanded() = capture(ScreenshotVariant.Expanded)

    @Test
    @Config(qualifiers = COMPACT_TALL, fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    fun longList() = capture(ScreenshotVariant.Light, ContactsPreviewData.long)

    @Test
    fun longListDark() = capture(ScreenshotVariant.Dark, ContactsPreviewData.long)

    @Test
    fun syncOn() = capture(ScreenshotVariant.Light, ContactsPreviewData.syncOn)

    @Test
    fun results() = capture(ScreenshotVariant.Light, ContactsPreviewData.results)

    @Test
    fun empty() = capture(ScreenshotVariant.Light, ContactsPreviewData.empty)

    private fun capture(
        variant: ScreenshotVariant,
        state: ContactsUiState = ContactsPreviewData.busy,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            CompositionLocalProvider(LocalExperimentalUi provides true) {
                ContactsScreen(state = state, onEvent = {}, onAddContact = {}, onEditContact = {})
            }
        }
    }
}
