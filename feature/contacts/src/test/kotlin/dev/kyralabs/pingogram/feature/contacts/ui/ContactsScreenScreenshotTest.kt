package dev.kyralabs.pingogram.feature.contacts.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncRationale
import dev.kyralabs.pingogram.feature.contacts.ui.preview.ContactsPreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the Contacts tab (`docs/DESIGN_SYSTEM.md` §12): the list in every look the audit asks for, a medium and an
 * expanded window, the long list with its index, search results, no contacts, an error, and contacts sync: its offer,
 * its paused state, its row with the phone's contacts to invite, and its rationale (the sheet's content, on the
 * sheet's `surfaceContainerLow`). At 200% font: the list, search results and the sync row with the phone's contacts.
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
    @Config(qualifiers = COMPACT_TALL, fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light)

    @Test
    @Config(qualifiers = COMPACT_TALL, fontScale = FONT_SCALE_200)
    fun resultsFontScale200() = capture(ScreenshotVariant.Light, ContactsPreviewData.results)

    @Test
    @Config(qualifiers = COMPACT_TALL, fontScale = FONT_SCALE_200)
    fun syncOnFontScale200() = capture(ScreenshotVariant.Light, ContactsPreviewData.syncOn)

    @Test
    fun longList() = capture(ScreenshotVariant.Light, ContactsPreviewData.long)

    @Test
    fun longListDark() = capture(ScreenshotVariant.Dark, ContactsPreviewData.long)

    /** Synced contacts' icons and "Sync now" end before the fast-scroll index. */
    @Test
    fun longListSyncedDark() = capture(ScreenshotVariant.Dark, ContactsPreviewData.longSynced)

    @Test
    fun results() = capture(ScreenshotVariant.Light, ContactsPreviewData.results)

    @Test
    fun empty() = capture(ScreenshotVariant.Light, ContactsPreviewData.empty)

    @Test
    fun error() = capture(ScreenshotVariant.Light, ContactsPreviewData.error)

    @Test
    fun syncOffer() = capture(ScreenshotVariant.Light, ContactsPreviewData.syncOffer)

    @Test
    fun syncOfferDark() = capture(ScreenshotVariant.Dark, ContactsPreviewData.syncOffer)

    @Test
    fun syncPaused() = capture(ScreenshotVariant.Light, ContactsPreviewData.syncPaused)

    @Test
    fun syncOn() = capture(ScreenshotVariant.Light, ContactsPreviewData.syncOn)

    @Test
    fun syncOnDark() = capture(ScreenshotVariant.Dark, ContactsPreviewData.syncOn)

    @Test
    fun syncRationale() =
        captureRoboImage {
            ScreenshotTheme(ScreenshotVariant.Light) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ContactSyncRationale(onContinue = {}, onDismiss = {})
                }
            }
        }

    @Test
    fun syncRationaleDark() =
        captureRoboImage {
            ScreenshotTheme(ScreenshotVariant.Dark) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ContactSyncRationale(onContinue = {}, onDismiss = {})
                }
            }
        }

    private fun capture(
        variant: ScreenshotVariant,
        state: ContactsUiState = ContactsPreviewData.content,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            ContactsScreen(state = state, onEvent = {}, onAddContact = {}, onEditContact = {})
        }
    }
}
