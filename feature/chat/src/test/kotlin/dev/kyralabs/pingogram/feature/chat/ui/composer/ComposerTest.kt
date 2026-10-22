package dev.kyralabs.pingogram.feature.chat.ui.composer

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.COMPACT
import dev.kyralabs.pingogram.feature.chat.ui.ComponentActivityRegistration
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The composer in use: typing, sending with the button, Enter and the split button's menu, scheduling, formatting
 * through the text field, and the label where the user cannot write.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalTestApi::class)
class ComposerTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val sent = mutableListOf<SendOptions>()
    private val focus = mutableListOf<Boolean>()
    private var now = NOW

    private fun setComposer(
        text: ComposerText = ComposerText(),
        composer: ComposerUiState = ComposerUiState.Available(canSchedule = true),
    ): ComposerText {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatBottomBar(composer, text, onSend = { sent += it }, onFocusChanged = { focus += it }, now = { now })
            }
        }
        return text
    }

    private fun field() = composeRule.onNode(hasSetTextAction())

    private fun clickText(
        @StringRes id: Int,
    ) = composeRule.onNodeWithText(string(id)).performClick()

    private fun formatted(
        text: String,
        vararg entities: TextEntity,
    ) = FormattedText(text, persistentListOf(*entities))

    @Test
    fun `the send button shows once there is something to send`() {
        setComposer()
        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).assertDoesNotExist()

        field().performTextInput("Hi")

        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).assertIsDisplayed()
    }

    @Test
    fun `the send button sends now`() {
        setComposer()
        field().performTextInput("Hi")

        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).performClick()

        assertEquals(listOf(SendOptions()), sent)
    }

    @Test
    fun `Enter on a hardware keyboard sends`() {
        setComposer()
        field().performTextInput("Hi")

        field().performKeyInput { pressKey(Key.Enter) }

        assertEquals(listOf(SendOptions()), sent)
    }

    @Test
    fun `Shift+Enter starts a new line instead`() {
        val text = setComposer()
        field().performTextInput("Hi")

        field().performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Enter) } }

        assertEquals(emptyList<SendOptions>(), sent)
        assertEquals("Hi\n", text.state.text.toString())
    }

    @Test
    fun `Enter with nothing to send sends nothing`() {
        setComposer()

        field().performKeyInput { pressKey(Key.Enter) }

        assertEquals(emptyList<SendOptions>(), sent)
    }

    @Test
    fun `the send menu sends without sound`() {
        setComposer()
        field().performTextInput("Shh")

        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).performTouchInput { longClick() }
        clickText(R.string.composer_send_silently)

        assertEquals(listOf(SendOptions(silent = true)), sent)
    }

    @Test
    fun `the send menu schedules a message, an hour from now at first`() {
        setComposer()
        field().performTextInput("Later")

        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).performTouchInput { longClick() }
        clickText(R.string.composer_schedule)
        clickText(R.string.composer_schedule_next)
        clickText(R.string.composer_schedule_confirm)

        val inAnHour = NOW.plus(1, ChronoUnit.HOURS).atZone(ZoneId.systemDefault()).truncatedTo(ChronoUnit.MINUTES)
        assertEquals(listOf(SendOptions(scheduledAt = inAnHour.toEpochSecond())), sent)
    }

    @Test
    fun `a time that has passed meanwhile asks for another`() {
        setComposer()
        field().performTextInput("Later")
        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).performTouchInput { longClick() }
        clickText(R.string.composer_schedule)
        clickText(R.string.composer_schedule_next)

        now = NOW.plus(2, ChronoUnit.HOURS)
        clickText(R.string.composer_schedule_confirm)

        composeRule.onNodeWithText(string(R.string.composer_schedule_past)).assertIsDisplayed()
        assertEquals(emptyList<SendOptions>(), sent)
    }

    @Test
    fun `a chat that cannot schedule offers sending without sound only`() {
        setComposer(composer = ComposerUiState.Available(canSchedule = false))
        field().performTextInput("Hi")

        composeRule.onNodeWithContentDescription(string(R.string.composer_send)).performTouchInput { longClick() }

        composeRule.onNodeWithText(string(R.string.composer_send_silently)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.composer_schedule)).assertDoesNotExist()
    }

    @Test
    fun `text typed inside a format joins it`() {
        val text = setComposer(ComposerText(formatted("Say word now", TextEntity(4, 4, TextEntityType.Bold))))

        field().performTextInputSelection(TextRange(6))
        field().performTextInput("XY")

        assertEquals(formatted("Say woXYrd now", TextEntity(4, 6, TextEntityType.Bold)), text.content)
    }

    @Test
    fun `text typed right after a format stays plain`() {
        val text = setComposer(ComposerText(formatted("Say word now", TextEntity(4, 4, TextEntityType.Bold))))

        field().performTextInputSelection(TextRange(8))
        field().performTextInput("!")

        assertEquals(formatted("Say word! now", TextEntity(4, 4, TextEntityType.Bold)), text.content)
    }

    @Test
    fun `screen readers format the selected text`() {
        val text = setComposer(ComposerText(formatted("Say word now")))
        field().performTextInputSelection(TextRange(4, 8))

        performCustomAction(string(R.string.composer_format_bold))

        assertEquals(formatted("Say word now", TextEntity(4, 4, TextEntityType.Bold)), text.content)
    }

    @Test
    fun `formatting is offered only while text is selected`() {
        setComposer(ComposerText(formatted("Say word now")))

        field().performTextInputSelection(TextRange(4))

        composeRule.onNode(hasCustomAction(string(R.string.composer_format_bold))).assertDoesNotExist()
    }

    @Test
    fun `the link action links the selected text to the address given`() {
        val text = setComposer(ComposerText(formatted("Read this")))
        field().performTextInputSelection(TextRange(5, 9))

        performCustomAction(string(R.string.composer_format_link))
        // The dialog's field, in a window above the composer's.
        composeRule.onAllNodes(hasSetTextAction()).onLast().performTextInput("https://example.org")
        clickText(R.string.composer_link_save)

        assertEquals(
            formatted("Read this", TextEntity(5, 4, TextEntityType.TextUrl("https://example.org"))),
            text.content,
        )
    }

    @Test
    fun `the text field reports its focus`() {
        setComposer()

        field().performClick()

        assertTrue(focus.last())
    }

    @Test
    fun `where the user cannot write, a label says why`() {
        setComposer(composer = ComposerUiState.Unavailable(UiText.resource(R.string.composer_restricted)))

        composeRule.onNodeWithText(string(R.string.composer_restricted)).assertIsDisplayed()
        field().assertDoesNotExist()
    }

    @Test
    fun `a channel the user reads shows nothing`() {
        setComposer(composer = ComposerUiState.Hidden)

        field().assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.composer_restricted)).assertDoesNotExist()
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
        /** 2026-10-18T09:41:30Z. */
        val NOW: Instant = Instant.parse("2026-10-18T09:41:30Z")
    }
}
