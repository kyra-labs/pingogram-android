package dev.kyralabs.pingogram.feature.contacts.ui

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the Contacts tab (`docs/DESIGN_SYSTEM.md` §12): the list in every look the audit asks for, a medium and an
 * expanded window, the long list with its index, search results, no contacts, and an error.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactsScreenScreenshotTest {
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
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() = capture(ScreenshotVariant.Expanded)

    @Test
    fun longList() = capture(ScreenshotVariant.Light, ContactsPreviewData.long)

    @Test
    fun longListDark() = capture(ScreenshotVariant.Dark, ContactsPreviewData.long)

    @Test
    fun results() = capture(ScreenshotVariant.Light, ContactsPreviewData.results)

    @Test
    fun empty() = capture(ScreenshotVariant.Light, ContactsPreviewData.empty)

    @Test
    fun error() = capture(ScreenshotVariant.Light, ContactsPreviewData.error)

    private fun capture(
        variant: ScreenshotVariant,
        state: ContactsUiState = ContactsPreviewData.content,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            ContactsScreen(state = state, onEvent = {}, onAddContact = {}, onEditContact = {})
        }
    }
}
