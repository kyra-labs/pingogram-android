package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.ThemeMode
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.content.PreviewImages
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the attach menu on a compact phone, light and dark (`docs/TESTING.md`): the attach sheet, the preview of
 * photos and videos (one still being prepared) and of files, the poll form with a quiz, and photos and a file being
 * uploaded; the files' preview also on an expanded window, and the attach sheet at 200% font scale, where options go
 * into the overflow menu. Inspection mode, so Coil shows solid colors for the images ([PreviewImages]). Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AttachScreenshotTest(
    private val stateName: String,
) {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun light() = capture("light", ThemeMode.Light)

    @Test
    fun dark() = capture("dark", ThemeMode.Dark)

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        // The sheet keeps its maximum width on wide windows; one state shows it.
        assumeTrue(stateName == "previewFiles")
        capture("expanded", ThemeMode.Light)
    }

    /** At 200% font scale the options that do not fit go into the group's overflow menu (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() {
        assumeTrue(stateName == "attachMenu")
        capture("fontScale200", ThemeMode.Light)
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: String,
        themeMode: ThemeMode,
    ) {
        val state = STATES.getValue(stateName)
        composeRule.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true) {
                PingogramTheme(themeMode = themeMode, dynamicColor = false) {
                    PreviewImages {
                        ChatScreen(
                            state = state,
                            onEvent = {},
                            onBack = {},
                            attach = AttachInputs(pollEditor = PollEditor(AttachPreviewData.quiz)),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        if (stateName == "attachMenu") {
            composeRule.onNodeWithContentDescription("Attach").performClick()
            composeRule.waitForIdle()
        }
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$variant"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        val STATES: Map<String, ChatUiState> =
            mapOf(
                "attachMenu" to
                    ChatPreviewData.groupChat.copy(
                        composer =
                            ComposerUiState.Available(true, attachOptions = AttachOption.entries.toImmutableList()),
                    ),
                "previewMedia" to ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.media),
                "previewOriginal" to
                    ChatPreviewData.privateChat.copy(
                        attachPreview = AttachPreviewData.media.copy(offersOriginal = true, original = true),
                    ),
                "previewFiles" to ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.files),
                "pollForm" to ChatPreviewData.groupChat.copy(poll = PollSheet()),
                "uploading" to ChatPreviewData.uploading,
            )

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
