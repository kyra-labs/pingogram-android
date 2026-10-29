package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreviewSheet
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ReplyPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.reply.ReplyChatsBottomSheet
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of quote replies, replies in another chat and replacing media (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md`
 * §12) on a compact phone, in every look the audit asks for (light, dark, AMOLED, high contrast, right to left, a
 * 700 dp window and 200% font scale): the composer quoting part of a message, above bubbles that quote, sent and
 * received; the composer replying to a message of another chat; editing a photo, whose bar offers "Replace"; the chat
 * picker of "Reply in another chat", loaded and loading; the attach preview of the replacement. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class RepliesScreenshotTest(
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
    fun amoled() = capture(ScreenshotVariant.Amoled)

    @Test
    fun highContrast() = capture(ScreenshotVariant.HighContrast)

    @Test
    fun rtl() = capture(ScreenshotVariant.Rtl)

    @Test
    @Config(qualifiers = MEDIUM)
    fun medium() = capture(ScreenshotVariant.Medium)

    /** At 200% font scale the bars and sheets grow instead of clipping (`REQUIREMENTS.md` §6.3). */
    @Test
    @Config(fontScale = FONT_SCALE_200)
    fun fontScale200() = capture(ScreenshotVariant.Light, "fontScale200")

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                when (stateName) {
                    "quoting" -> {
                        val state = ReplyPreviewData.quotes.copy(composer = ReplyPreviewData.quoting.composer)
                        ChatScreen(state = state, onEvent = {}, onBack = {})
                    }

                    "replyingElsewhere" -> {
                        ChatScreen(state = ReplyPreviewData.replyingElsewhere, onEvent = {}, onBack = {})
                    }

                    "editingMedia" -> {
                        ChatScreen(state = ReplyPreviewData.editingMedia, onEvent = {}, onBack = {})
                    }

                    "chatPicker", "chatPickerLoading" -> {
                        ChatScreen(state = ReplyPreviewData.quotes, onEvent = {}, onBack = {})
                        val loading = stateName == "chatPickerLoading"
                        val sheet = if (loading) ReplyPreviewData.chatPickerLoading else ReplyPreviewData.chatPicker
                        ReplyChatsBottomSheet(sheet, onEvent = {})
                    }

                    "replacement" -> {
                        ChatScreen(state = ReplyPreviewData.editingMedia, onEvent = {}, onBack = {})
                        AttachPreviewSheet(
                            preview = AttachPreviewData.replacement,
                            caption = ComposerText(),
                            onEvent = {},
                            onSend = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        private const val FONT_SCALE_200 = 2f

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> =
            listOf("quoting", "replyingElsewhere", "editingMedia", "chatPicker", "chatPickerLoading", "replacement")
                .map { arrayOf(it) }
    }
}
