package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChecklistDraft
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChecklistEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.ChecklistForm
import dev.kyralabs.pingogram.feature.chat.ui.checklist.ChecklistSheet
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChecklistPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of checklists (`docs/TESTING.md`, `docs/DESIGN_SYSTEM.md` §6 "Checklists"), every state in the looks the
 * audit asks for (§12): light, dark, true black, high contrast, right to left, a medium window and 200% font scale.
 * A group's conversation with the user's checklist and one of Ada's ("lists"), with a change on its way, the inline
 * field open and a checklist the user may not change ("states"), and the form of a new checklist ("form"). Brand
 * colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChecklistScreenshotTest(
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
                    "lists" -> {
                        ChatScreen(state = ChecklistPreviewData.lists, onEvent = {}, onBack = {})
                    }

                    "states" -> {
                        ChatScreen(state = ChecklistPreviewData.states, onEvent = {}, onBack = {})
                    }

                    "form" -> {
                        Column {
                            ChecklistForm(
                                sheet = ChecklistSheet(),
                                editor =
                                    remember {
                                        ChecklistEditor(
                                            ChecklistDraft(
                                                title = "Pingogram 1.2 release",
                                                tasks =
                                                    listOf(
                                                        "Freeze strings for translation",
                                                        "Write the changelog",
                                                        "",
                                                    ),
                                            ),
                                        )
                                    },
                                canSend = true,
                                onSend = {},
                                onSchedule = {},
                            )
                        }
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
        fun states(): List<Array<Any>> = listOf("lists", "states", "form").map { arrayOf(it) }
    }
}
