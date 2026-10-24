package dev.kyralabs.pingogram.feature.profile.ui

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.LocalPhoneNumbersHidden
import dev.kyralabs.pingogram.feature.profile.ui.preview.ProfilePreviewData
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the profile (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §12): a regular user with photos and shared media
 * in every look the audit asks for, a medium window, an expanded window with the shared media beside the profile, a
 * bot, a deleted account, a scam warning with the files tab, and the loading and error states.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ProfileScreenScreenshotTest {
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
    fun expanded() = capture(ScreenshotVariant.Light)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expandedDark() = capture(ScreenshotVariant.Dark)

    @Test
    fun bot() = capture(ScreenshotVariant.Light, ProfilePreviewData.bot, ProfilePreviewData.emptyMedia)

    @Test
    fun deleted() = capture(ScreenshotVariant.Dark, ProfilePreviewData.deleted, ProfilePreviewData.emptyMedia)

    @Test
    fun scamFiles() =
        capture(ScreenshotVariant.Light, ProfilePreviewData.scam, ProfilePreviewData.files, scrolledTo = MEDIA)

    @Test
    fun links() = capture(ScreenshotVariant.Dark, ProfilePreviewData.scam, ProfilePreviewData.links, scrolledTo = MEDIA)

    @Test
    fun photosScrolled() = capture(ScreenshotVariant.Light, scrolledTo = MEDIA_OF_CONTENT)

    /** "Hide phone numbers" on: the number shows its calling code only. */
    @Test
    fun phoneNumberHidden() = capture(ScreenshotVariant.Light, numbersHidden = true)

    @Test
    fun error() = capture(ScreenshotVariant.Light, ProfileUiState.Error(DomainError.NotFound))

    private fun capture(
        variant: ScreenshotVariant,
        state: ProfileUiState = ProfilePreviewData.content,
        media: SharedMediaUiState = ProfilePreviewData.media,
        scrolledTo: Int = 0,
        numbersHidden: Boolean = false,
    ) = captureRoboImage {
        ScreenshotTheme(variant) {
            CompositionLocalProvider(
                LocalPhoneNumbersHidden provides numbersHidden,
            ) { Screen(state, media, scrolledTo) }
        }
    }

    @Composable
    private fun Screen(
        state: ProfileUiState,
        media: SharedMediaUiState,
        scrolledTo: Int,
    ) {
        ProfileScreen(
            gridState = rememberLazyGridState(initialFirstVisibleItemIndex = scrolledTo),
            state = state,
            media = media,
            onEvent = {},
            onMediaEvent = {},
            onBack = {},
            mediaActions = SharedMediaActions({ _, _ -> }, { _, _ -> }, {}),
            onEditContact = {},
        )
    }

    private companion object {
        /** The "Shared media" heading of a profile without photos: after the actions, the info and More. */
        const val MEDIA = 3

        /** The same with the photos' carousel before the info. */
        const val MEDIA_OF_CONTENT = 4
    }
}
