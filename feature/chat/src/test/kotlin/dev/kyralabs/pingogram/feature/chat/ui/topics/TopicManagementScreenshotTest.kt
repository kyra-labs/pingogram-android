package dev.kyralabs.pingogram.feature.chat.ui.topics

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import dev.kyralabs.pingogram.core.domain.model.TopicColor
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatScreen
import dev.kyralabs.pingogram.feature.chat.ui.ChatSubtitle
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.MEDIUM
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotTheme
import dev.kyralabs.pingogram.feature.chat.ui.ScreenshotVariant
import dev.kyralabs.pingogram.feature.chat.ui.goldenPath
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.TopicsPreviewData
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goldens of managing a forum's topics (`docs/DESIGN_SYSTEM.md` §6 "Forum topics", `REQUIREMENTS.md` FR-C9) on a
 * compact phone, in every look the audit asks for (light, dark, AMOLED black, high contrast, right to left, a 700 dp
 * window and 200% font): the topic list as an administrator sees it (the hidden General topic, drag handles on the
 * pinned topics, "New topic"), a topic's long press menu, the editor of a new topic, of a rename and refused, the
 * question before deleting, and a closed topic's conversation with "Manage topic" and its menu open; the new topic's
 * editor also in each of Telegram's six colors, light only. Brand colors.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class TopicManagementScreenshotTest(
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
        // The six colors only light: the other looks show the editor in one.
        if (stateName.startsWith(COLOR) && name != ScreenshotVariant.Light.goldenName) return
        show(variant) { Content(stateName) }
        when (stateName) {
            "menu" -> {
                composeRule
                    .onNode(
                        hasContentDescription(
                            "Help",
                            substring = true,
                        ).and(SemanticsMatcher.keyIsDefined(SemanticsActions.OnLongClick)),
                    ).performTouchInput { longClick() }
            }

            "topBarMenu" -> {
                composeRule.onNodeWithContentDescription("Manage topic").performClick()
            }
        }
        composeRule.waitForIdle()
        captureScreenRoboImage(goldenPath(javaClass, "$stateName.$name"))
    }

    private fun show(
        variant: ScreenshotVariant,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent { ScreenshotTheme(variant, content) }
        composeRule.waitForIdle()
    }

    companion object {
        const val FONT_SCALE_200 = 2f

        /** The states named after a color: the new topic's editor in that color. */
        const val COLOR = "color."

        private val STATES =
            listOf("list", "menu", "editorNew", "editorRename", "editorRefused", "delete", "topBarMenu") +
                TopicColor.entries.map { COLOR + it.name.lowercase() }

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun states(): List<Array<Any>> = STATES.map { arrayOf(it) }
    }
}

/** What [stateName] shows: the topic list with what it opens, or a closed topic's conversation. */
@Composable
private fun Content(stateName: String) {
    with(TopicsPreviewData) {
        if (stateName == "topBarMenu") {
            TopicConversation()
            return
        }
        val state =
            when (stateName) {
                "editorNew" -> {
                    managed.withEditor(newTopic())
                }

                "editorRename" -> {
                    managed.withEditor(renaming)
                }

                "editorRefused" -> {
                    managed.withEditor(refused)
                }

                "delete" -> {
                    managed.copy(management = TopicManagementUiState(deleting = deleting))
                }

                "list", "menu" -> {
                    managed
                }

                else -> {
                    managed.withEditor(
                        newTopic(TopicColor.entries.first { stateName.endsWith(it.name.lowercase()) }),
                    )
                }
            }
        ForumTopicsScreen(state = state, onEvent = {}, onOpenTopic = {}, onBack = {})
    }
}

/** The conversation of the closed topic "Releases", whose top bar has "Manage topic". */
@Composable
private fun TopicConversation() {
    val group = ChatPreviewData.groupChat
    val header =
        checkNotNull(group.header).copy(
            title = UiText.Plain("Releases"),
            subtitle = ChatSubtitle.Status(UiText.Plain("Compose Developers")),
        )
    ChatScreen(
        state = group.copy(header = header),
        onEvent = {},
        onBack = {},
        topicAction = { TopicActionButton(TopicsPreviewData.closedMenu, onEvent = {}) },
    )
}

private fun ForumTopicsUiState.withEditor(editor: TopicEditorUiState) =
    copy(management = TopicManagementUiState(editor = editor))
