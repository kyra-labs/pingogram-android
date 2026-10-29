package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineBotUiState
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationBottomSheet
import dev.kyralabs.pingogram.feature.chat.ui.inline.InlineLocationSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.InlinePreviewData
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of inline bots in the conversation (`docs/TESTING.md`, `REQUIREMENTS.md` FR-C5) on a compact phone, light
 * and dark: @gif's GIFs above the composer for "cats", its suggestions under the placeholder for "@gif ", stickers, a
 * bot's rows (articles, a contact, a song, a game) with the next page loading, a places bot's "Start" button above its
 * venues, nothing found, a flood wait, and the question whether to share the location. GIFs and rows also right to
 * left and in an expanded window. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class InlineBotScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture(ScreenshotVariant.Light)

    @Test
    fun dark() = capture(ScreenshotVariant.Dark)

    @Test
    fun rtl() {
        assumeTrue(stateName == "gifs" || stateName == "rows")
        capture(ScreenshotVariant.Rtl)
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        assumeTrue(stateName == "gifs" || stateName == "rows")
        capture(ScreenshotVariant.Light, "expanded")
    }

    /** At 200% font scale (`REQUIREMENTS.md` §6.3), the state with the most text: it grows and never clips. */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "rows")
        capture(ScreenshotVariant.Light, "fontScale200")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        val shown = STATES.getValue(stateName)
        composeRule.setContent {
            ScreenshotTheme(variant) {
                // The preview images in place of TDLib's files (PreviewImages), as in the previews.
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    PreviewImages {
                        ChatScreen(
                            state = ChatPreviewData.privateChat,
                            onEvent = {},
                            onBack = {},
                            composerText = remember { ComposerText(FormattedText(shown.typed, persistentListOf())) },
                            inlineResults = shown.inline,
                        )
                        shown.sheet?.let { InlineLocationBottomSheet(sheet = it, onEvent = {}) }
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    /** What a golden shows: what is typed, the bot's results for it, and the location question over them. */
    class Shown(
        val inline: InlineBotUiState?,
        val typed: String = inline?.let { "@${it.username} ${it.query}" }.orEmpty(),
        val sheet: InlineLocationSheet? = null,
    )

    companion object {
        val STATES: Map<String, Shown> =
            with(InlinePreviewData) {
                mapOf(
                    "gifs" to Shown(gifResults),
                    "suggestions" to Shown(gifSuggestions),
                    "stickers" to Shown(stickerResults),
                    "rows" to Shown(rowResults),
                    "button" to Shown(placeResults),
                    "empty" to Shown(empty),
                    "floodWait" to Shown(floodWait),
                    "location" to Shown(loading.copy(username = "places_bot", query = "coffee"), sheet = locationSheet),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
