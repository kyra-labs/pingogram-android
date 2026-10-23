package dev.kyralabs.pingogram.feature.chat.ui.export

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.EXPANDED
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.export.preview.ChatExportPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of the export page (`docs/DESIGN_SYSTEM.md` §6 "Chat export") on a compact phone, light and dark: the form
 * as it opens and filled in, the export running and waiting for Telegram, done, failed, a chat that cannot be
 * exported and one that cannot be read; the filled-in form also right to left, with AMOLED black, whole on a tall
 * phone and on an expanded window, where the page keeps its column's width. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatExportScreenshotTest(
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
        if (stateName == "form") capture(ScreenshotVariant.Rtl)
    }

    @Test
    fun amoled() {
        if (stateName == "form") capture(ScreenshotVariant.Amoled)
    }

    /** The whole form, its folder and "Export" included, on a phone tall enough for it. */
    @Test
    @Config(qualifiers = WHOLE_FORM)
    fun whole() {
        if (stateName == "form" || stateName == "emptyForm") capture(ScreenshotVariant.Light, "whole")
    }

    @Test
    @Config(qualifiers = EXPANDED)
    fun expanded() {
        if (stateName == "form" || stateName == "done") capture(ScreenshotVariant.Light, "expanded")
    }

    @OptIn(ExperimentalRoborazziApi::class)
    private fun capture(
        variant: ScreenshotVariant,
        name: String = variant.goldenName,
    ) {
        composeRule.setContent {
            ScreenshotTheme(variant) {
                ChatExportScreen(
                    state = STATES.getValue(stateName),
                    onEvent = {},
                    onBack = {},
                    onChooseFolder = {},
                    onOpenFolder = {},
                )
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    companion object {
        /** A compact phone as tall as the whole form at 100% font. */
        const val WHOLE_FORM = "w411dp-h1750dp-xxhdpi"

        val STATES: Map<String, ChatExportUiState> =
            with(ChatExportPreviewData) {
                mapOf(
                    "emptyForm" to emptyForm,
                    "form" to form,
                    "running" to running,
                    "waiting" to waiting,
                    "done" to done,
                    "failed" to failed,
                    "notAllowed" to ChatExportUiState.NotAllowed(isSecretChat = true),
                    "error" to ChatExportUiState.Error(DomainError.Network),
                )
            }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.keys.map { arrayOf(it) }
    }
}
