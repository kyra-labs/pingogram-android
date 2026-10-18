package dev.kyralabs.pingogram.feature.chats.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chats.ui.preview.SearchPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Global search's states (`REQUIREMENTS.md` FR-L6): the suggestions, the hint, results in every section with the
 * query's words highlighted, a section still searching, a section that failed, and a query that found nothing; in
 * light and dark on a compact phone, and on an expanded window, where the field and the list keep a comfortable width.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class SearchScreenScreenshotTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun suggestionsLight() = capture("suggestionsLight", SearchPreviewData.suggestions, ThemeMode.Light)

    @Test
    fun suggestionsDark() = capture("suggestionsDark", SearchPreviewData.suggestions, ThemeMode.Dark)

    @Test
    fun hintLight() = capture("hintLight", SearchPreviewData.hint, ThemeMode.Light)

    @Test
    fun resultsLight() = capture("resultsLight", SearchPreviewData.results, ThemeMode.Light)

    @Test
    fun resultsDark() = capture("resultsDark", SearchPreviewData.results, ThemeMode.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun resultsExpanded() = capture("resultsExpanded", SearchPreviewData.results, ThemeMode.Light)

    @Test
    fun searchingLight() = capture("searchingLight", SearchPreviewData.loading, ThemeMode.Light)

    @Test
    fun sectionErrorLight() = capture("sectionErrorLight", SearchPreviewData.error, ThemeMode.Light)

    @Test
    fun noResultsLight() = capture("noResultsLight", SearchPreviewData.noResults, ThemeMode.Light)

    @Test
    fun noResultsDark() = capture("noResultsDark", SearchPreviewData.noResults, ThemeMode.Dark)

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        name: String,
        state: SearchUiState,
        themeMode: ThemeMode,
    ) {
        composeRule.setContent {
            PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                SearchScreen(
                    state = state,
                    textFieldState = remember { TextFieldState(SearchPreviewData.queryOf(state)) },
                    onEvent = {},
                    onBack = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, name))
    }
}
