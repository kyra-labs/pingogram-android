package dev.kyralabs.pingogram.feature.chat.ui.bot

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.BotEvent
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ChatEvent
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import dev.kyralabs.pingogram.feature.chat.ui.composer.BotInput
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.preview.BotPreviewData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The bots in the composer in use: the bot button, the suggestions with touch, a hardware keyboard and screen readers'
 * actions, "Start", and the commands sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalTestApi::class)
class BotCommandsUiTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<String>()
    private val inserted = mutableListOf<String>()
    private var menus = 0
    private var starts = 0

    private var suggestions by mutableStateOf<CommandSuggestions?>(null)

    private fun setComposer(
        composer: ComposerUiState =
            ComposerUiState.Available(
                canSchedule = true,
                attachOptions = BotPreviewData.attachOptions,
                bot = ComposerBotButton.Commands,
            ),
        typed: String = "",
    ) {
        val text = ComposerText(FormattedText(typed, persistentListOf()))
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatBottomBar(
                    composer = composer,
                    text = text,
                    onSend = {},
                    onFocusChanged = {},
                    bots =
                        BotInput(
                            suggestions = suggestions,
                            onSendCommand = { sent += it },
                            onInsertCommand = { inserted += it },
                            onMenu = { menus++ },
                            onStart = { starts++ },
                        ),
                )
            }
        }
    }

    /** The text field, focused, as for typing on a hardware keyboard. */
    private fun field() = composeRule.onNode(hasSetTextAction()).also { it.requestFocus() }

    private fun suggestionsShown() = composeRule.onNodeWithTag(SUGGESTIONS_TEST_TAG)

    @Test
    fun `the bot button opens the commands, and the attach button moves to the pill's end`() {
        setComposer()

        composeRule.onNodeWithContentDescription(string(R.string.bot_commands_button)).performClick()

        assertEquals(1, menus)
        composeRule.onNodeWithContentDescription(string(R.string.attach)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.composer_gallery)).assertDoesNotExist()
    }

    @Test
    fun `a Mini App menu button is named by the bot`() {
        setComposer(ComposerUiState.Available(canSchedule = true, bot = ComposerBotButton.MiniApp("Open app")))

        composeRule.onNode(hasContentDescription("Open app", substring = true)).performClick()

        assertEquals(1, menus)
    }

    @Test
    fun `a tapped suggestion is sent`() {
        suggestions = BotPreviewData.suggestions
        setComposer(typed = "/st")

        composeRule.onNodeWithText("/stickers").performClick()

        assertEquals(listOf("/stickers"), sent)
    }

    @Test
    fun `the arrow keys pick a suggestion, Enter sends it`() {
        suggestions = BotPreviewData.suggestions
        setComposer(typed = "/st")

        field().performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }

        assertEquals(listOf("/stickers"), sent)
    }

    @Test
    fun `Enter sends the first suggestion and Tab inserts it, before any pick`() {
        suggestions = BotPreviewData.suggestions
        setComposer(typed = "/st")

        field().performKeyInput { pressKey(Key.Tab) }
        field().performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf("/start"), inserted)
        assertEquals(listOf("/start"), sent)
    }

    @Test
    fun `Escape hides the suggestions, and Enter then is the field's again`() {
        suggestions = BotPreviewData.suggestions
        setComposer(typed = "/st")
        suggestionsShown().assertIsDisplayed()

        field().performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()

        suggestionsShown().assertDoesNotExist()
        field().performKeyInput { pressKey(Key.Enter) }
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun `screen readers insert a suggestion with its custom action`() {
        suggestions = BotPreviewData.suggestions
        setComposer(typed = "/st")

        performCustomAction(RuntimeEnvironment.getApplication().getString(R.string.bot_command_insert, "/start"))

        assertEquals(listOf("/start"), inserted)
    }

    @Test
    fun `Start starts the bot, and waits while Telegram is asked`() {
        setComposer(ComposerUiState.BotStart())

        composeRule.onNodeWithText(string(R.string.bot_start)).performClick()

        assertEquals(1, starts)
    }

    @Test
    fun `Start is disabled while starting`() {
        setComposer(ComposerUiState.BotStart(isStarting = true))

        composeRule.onNodeWithText(string(R.string.bot_start)).assertIsNotEnabled()
    }

    @Test
    fun `a command tapped in the sheet is sent, its insert action puts it into the field`() {
        val events = mutableListOf<ChatEvent>()
        composeRule.setContent {
            PingogramTheme(
                dynamicColor = false,
            ) { BotCommandsContent(BotPreviewData.sheet, onEvent = { events += it }) }
        }

        composeRule.onNodeWithText("/help").performClick()
        performCustomAction(RuntimeEnvironment.getApplication().getString(R.string.bot_command_insert, "/stickers"))

        assertEquals(listOf(BotEvent.SendCommand("/help"), BotEvent.InsertCommand("/stickers")), events)
    }

    @Test
    fun `a sheet with many commands searches them`() {
        val many =
            BotCommandsSheet(
                (1..MANY).map { BotCommandItem("command$it", "/command$it", "Does $it") }.toImmutableList(),
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) { BotCommandsContent(many, onEvent = {}) }
        }

        composeRule.onNodeWithText(string(R.string.bot_commands_search)).assertIsDisplayed()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    /** Does what the custom accessibility action [label] of the one node that offers it does, as TalkBack would. */
    private fun performCustomAction(label: String) {
        val node = composeRule.onNode(hasCustomAction(label)).fetchSemanticsNode()
        val action = node.config[SemanticsActions.CustomActions].single { it.label == label }
        composeRule.runOnIdle { action.action() }
    }

    private fun hasCustomAction(label: String) =
        SemanticsMatcher("has the custom action $label") { node ->
            node.config
                .getOrNull(SemanticsActions.CustomActions)
                .orEmpty()
                .any { it.label == label }
        }

    private companion object {
        /** More commands than the sheet shows without a search field. */
        const val MANY = 10
    }
}
