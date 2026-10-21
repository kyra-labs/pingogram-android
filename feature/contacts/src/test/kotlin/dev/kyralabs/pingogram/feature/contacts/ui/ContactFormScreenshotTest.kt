package dev.kyralabs.pingogram.feature.contacts.ui

import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.feature.contacts.ui.preview.EditContactPreviewData
import dev.kyralabs.pingogram.feature.contacts.ui.preview.NewContactPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of Add contact and Edit contact (`docs/DESIGN_SYSTEM.md` §12): each form light and dark, a username Telegram
 * does not know, the `FLOOD_WAIT` countdown, a number not on Telegram, a user to add with the switch, and an expanded
 * window.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ContactFormScreenshotTest {
    @Test
    fun newContactLight() = newContact(ScreenshotVariant.Light, NewContactPreviewData.phoneNumber)

    @Test
    fun newContactDark() = newContact(ScreenshotVariant.Dark, NewContactPreviewData.phoneNumber)

    @Test
    fun newContactUsername() = newContact(ScreenshotVariant.Light, NewContactPreviewData.username)

    @Test
    fun newContactFloodWait() = newContact(ScreenshotVariant.Light, NewContactPreviewData.floodWait)

    @Test
    fun newContactNotOnTelegram() = newContact(ScreenshotVariant.Light, NewContactPreviewData.notOnTelegram)

    @Test
    @Config(qualifiers = EXPANDED)
    fun newContactExpanded() = newContact(ScreenshotVariant.Expanded, NewContactPreviewData.phoneNumber)

    @Test
    fun editContactLight() = editContact(ScreenshotVariant.Light, EditContactPreviewData.contact)

    @Test
    fun editContactDark() = editContact(ScreenshotVariant.Dark, EditContactPreviewData.contact)

    @Test
    fun editContactRtl() = editContact(ScreenshotVariant.Rtl, EditContactPreviewData.contact)

    @Test
    fun addToContacts() = editContact(ScreenshotVariant.Light, EditContactPreviewData.notContact)

    private fun newContact(
        variant: ScreenshotVariant,
        state: NewContactUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { NewContactScreen(state = state, onEvent = {}, onBack = {}) } }

    private fun editContact(
        variant: ScreenshotVariant,
        state: EditContactUiState,
    ) = captureRoboImage { ScreenshotTheme(variant) { EditContactScreen(state = state, onEvent = {}, onBack = {}) } }
}
