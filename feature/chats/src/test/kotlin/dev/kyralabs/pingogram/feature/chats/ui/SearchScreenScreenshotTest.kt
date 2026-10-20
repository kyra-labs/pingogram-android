package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Global search's states (`REQUIREMENTS.md` FR-L6): the suggestions, the hint, results in segmented groups with the
 * query's words highlighted, a section still searching (the progress indicator under the chips), sections that failed,
 * a query that found nothing, and the Media filter without a query; in light and dark on a compact phone, and on an
 * expanded window, where the field, the chips and the list keep a comfortable width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SearchScreenScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun suggestionsLight() = capture("suggestionsLight", SearchPreviewData.suggestions, ScreenshotVariant.Light)

    @Test
    fun suggestionsDark() = capture("suggestionsDark", SearchPreviewData.suggestions, ScreenshotVariant.Dark)

    @Test
    fun hintLight() = capture("hintLight", SearchPreviewData.hint, ScreenshotVariant.Light)

    @Test
    fun resultsLight() = capture("resultsLight", SearchPreviewData.results, ScreenshotVariant.Light)

    @Test
    fun resultsDark() = capture("resultsDark", SearchPreviewData.results, ScreenshotVariant.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun resultsExpanded() = capture("resultsExpanded", SearchPreviewData.results, ScreenshotVariant.Light)

    @Test
    fun resultsAmoled() = capture("resultsAmoled", SearchPreviewData.results, ScreenshotVariant.Amoled)

    @Test
    fun resultsHighContrast() =
        capture("resultsHighContrast", SearchPreviewData.results, ScreenshotVariant.HighContrast)

    @Test
    fun resultsRtl() = capture("resultsRtl", SearchPreviewData.results, ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun resultsMedium() = capture("resultsMedium", SearchPreviewData.results, ScreenshotVariant.Medium)

    @Test
    fun searchingLight() = capture("searchingLight", SearchPreviewData.loading, ScreenshotVariant.Light)

    @Test
    fun sectionErrorLight() = capture("sectionErrorLight", SearchPreviewData.error, ScreenshotVariant.Light)

    @Test
    fun noResultsLight() = capture("noResultsLight", SearchPreviewData.noResults, ScreenshotVariant.Light)

    @Test
    fun noResultsDark() = capture("noResultsDark", SearchPreviewData.noResults, ScreenshotVariant.Dark)

    @Test
    fun mediaLight() = capture("mediaLight", SearchPreviewData.media, ScreenshotVariant.Light)

    @Test
    fun mediaDark() = capture("mediaDark", SearchPreviewData.media, ScreenshotVariant.Dark)

    @Test
    fun noMediaLight() = capture("noMediaLight", SearchPreviewData.noMedia, ScreenshotVariant.Light)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        state: SearchUiState,
        variant: ScreenshotVariant,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                SearchScreen(
                    state = state,
                    textFieldState = remember { TextFieldState(SearchPreviewData.queryOf(state)) },
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        // Past the progress indicator's delay, so a search still running shows it.
        composeRule.mainClock.advanceTimeBy(PROGRESS_DELAY_MILLIS)
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }
}

/** Longer than search's progress indicator waits before it shows, and its fade in. */
private const val PROGRESS_DELAY_MILLIS = 1_000L
