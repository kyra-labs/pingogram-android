package dev.kyralabs.pingogram.feature.media.storypost

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.github.takahirom.roborazzi.captureRoboImage
import dev.kyralabs.pingogram.core.domain.model.Contrast
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.ui.COMPACT
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of a story's "Selected people" (`docs/TESTING.md`), its content on the sheet's container as
 * `ModalBottomSheet` draws it: contacts with two picked in light, dark, AMOLED, high contrast, right to left, 200% font
 * scale and a 700 dp window; then nobody matching, no contacts and a failure. Brand colors.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class StoryPeopleScreenshotTest {
    @Test
    fun contactsLight() = capture(StoryPeoplePreviewData.contacts)

    @Test
    fun contactsDark() = capture(StoryPeoplePreviewData.contacts, ThemeMode.Dark)

    @Test
    fun contactsAmoled() = capture(StoryPeoplePreviewData.contacts, ThemeMode.Dark, amoled = true)

    @Test
    fun contactsHighContrast() = capture(StoryPeoplePreviewData.contacts, contrast = Contrast.High)

    @Test
    fun contactsRtl() = capture(StoryPeoplePreviewData.contacts, layoutDirection = LayoutDirection.Rtl)

    @Test
    @Config(fontScale = 2f)
    fun contactsFontScale200() = capture(StoryPeoplePreviewData.contacts)

    @Test
    @Config(qualifiers = "w700dp-h891dp-xxhdpi")
    fun contactsMedium() = capture(StoryPeoplePreviewData.contacts)

    @Test
    fun noMatchesLight() = capture(StoryPeoplePreviewData.noMatches, query = "zz")

    @Test
    fun noContactsLight() = capture(StoryPeoplePreviewData.noContacts)

    @Test
    fun errorLight() = capture(StoryPeoplePreviewData.error)

    @Suppress("LongParameterList") // One golden per theme variant and state.
    private fun capture(
        state: StoryPeopleUiState,
        themeMode: ThemeMode = ThemeMode.Light,
        amoled: Boolean = false,
        contrast: Contrast = Contrast.Standard,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        query: String = "",
    ) = captureRoboImage {
        CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
            PingogramTheme(themeMode = themeMode, dynamicColor = false, amoled = amoled, contrast = contrast) {
                SheetSurface { StoryPeopleContent(state, query) {} }
            }
        }
    }
}

/** [content] on a bottom sheet's container, its top corners and padding, as in the sheet's window. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = BottomSheetDefaults.ExpandedShape,
        color = BottomSheetDefaults.ContainerColor,
    ) {
        Box(Modifier.padding(top = MaterialTheme.spacing.extraLarge)) { content() }
    }
}
