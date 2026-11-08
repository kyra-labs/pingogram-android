package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachInputs
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachOption
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollDraft
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollEditor
import dev.kyralabs.pingogram.feature.chat.ui.attach.PollSheet
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The attach menu on the conversation screen (`REQUIREMENTS.md` FR-C5): the attach button and its sheet, the preview
 * of picked items, the poll form, and cancelling uploads, also for screen readers. The screen is stateless: these
 * check what it sends.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class AttachScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()
    private val picked = mutableListOf<AttachOption>()

    private fun setScreen(
        state: ChatUiState,
        hasCamera: Boolean = true,
        caption: ComposerText = ComposerText(),
        poll: PollEditor = PollEditor(),
    ) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    attach = AttachInputs({ picked += it }, hasCamera, caption, poll),
                )
            }
        }
        composeRule.waitForIdle()
    }

    private fun openMenu() {
        composeRule.onNodeWithContentDescription("Attach").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun `the attach button opens the menu, whose option goes to the screen once the sheet hid`() {
        setScreen(ChatPreviewData.privateChat)
        openMenu()

        composeRule.onNodeWithContentDescription("Gallery").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(AttachOption.Gallery), picked)
        composeRule.onNodeWithContentDescription("Gallery").assertDoesNotExist()
    }

    @Test
    fun `the menu offers what the chat takes, without a camera on a device without one`() {
        setScreen(ChatPreviewData.privateChat, hasCamera = false)
        openMenu()

        listOf("Gallery", "File", "Contact").forEach {
            composeRule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("Camera").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Poll").assertDoesNotExist()
    }

    @Test
    fun `editing a message hides the attach button`() {
        setScreen(ChatPreviewData.editing)

        composeRule.onNodeWithContentDescription("Attach").assertDoesNotExist()
    }

    @Test
    fun `an item's remove button removes it`() {
        setScreen(ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.files))

        composeRule.onNodeWithContentDescription("Remove photos.zip").performClick()

        assertEquals(listOf(ChatEvent.RemoveAttachment(2)), events.filterIsInstance<ChatEvent.RemoveAttachment>())
    }

    @Test
    fun `the preview shows each file's name and size, and the photos and videos by what they are`() {
        setScreen(ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.files))

        composeRule.onNodeWithText("Q3 report.pdf").assertIsDisplayed()
        composeRule.onNodeWithText("2.5 MB").assertIsDisplayed()
        composeRule.onNodeWithText("3 files").assertIsDisplayed()
    }

    @Test
    fun `photos and videos are named for screen readers, an item being prepared too`() {
        setScreen(ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.media))

        composeRule.onNodeWithContentDescription("Photo 1 of 4").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Video 3 of 4, 0:42").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Preparing item 4 of 4").assertExists()
    }

    @Test
    fun `send waits until every item is ready`() {
        setScreen(ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.media))

        composeRule.onNodeWithContentDescription("Send").assertIsNotEnabled()
    }

    @Test
    fun `sending a ready preview sends it, with its caption`() {
        val caption = ComposerText().apply { state.setTextAndPlaceCursorAtEnd("For Q3") }
        setScreen(ChatPreviewData.privateChat.copy(attachPreview = AttachPreviewData.files), caption = caption)

        composeRule.onNodeWithContentDescription("Send").assertIsEnabled().performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(ChatEvent.SendAttachments(SendOptions())),
            events.filterIsInstance<ChatEvent.SendAttachments>(),
        )
    }

    @Test
    fun `the poll is sent once it is complete`() {
        val editor = PollEditor()
        setScreen(ChatPreviewData.groupChat.copy(poll = PollSheet()), poll = editor)
        composeRule.onNodeWithContentDescription("Send").performScrollTo().assertIsNotEnabled()

        composeRule.runOnIdle {
            editor.question.setTextAndPlaceCursorAtEnd("Lunch?")
            editor.options[0].text.setTextAndPlaceCursorAtEnd("Pizza")
            editor.options[1].text.setTextAndPlaceCursorAtEnd("Sushi")
        }
        composeRule
            .onNodeWithContentDescription("Send")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ChatEvent.SendPoll(SendOptions())), events.filterIsInstance<ChatEvent.SendPoll>())
    }

    @Test
    fun `in quiz mode a radio button marks the correct answer`() {
        val editor = PollEditor(PollDraft("2 + 2?", listOf("3", "4"), isQuiz = true))
        setScreen(ChatPreviewData.groupChat.copy(poll = PollSheet()), poll = editor)

        composeRule.onNodeWithContentDescription("Option 2 is the correct answer").performClick()

        composeRule.runOnIdle { assertEquals(editor.options[1].key, editor.correctOption) }
    }

    @Test
    fun `adding an option adds a field`() {
        val editor = PollEditor()
        setScreen(ChatPreviewData.groupChat.copy(poll = PollSheet()), poll = editor)

        composeRule.onNodeWithText("Add an option").performScrollTo().performClick()

        composeRule.runOnIdle { assertEquals(3, editor.options.size) }
    }

    @Test
    fun `in a channel voting stays anonymous, and the form says why`() {
        val editor = PollEditor(PollDraft(isAnonymous = false))
        setScreen(ChatPreviewData.channel.copy(poll = PollSheet(isChannel = true)), poll = editor)

        composeRule.onNodeWithText("Polls in channels are always anonymous").performScrollTo().assertIsDisplayed()
        composeRule
            .onNode(isToggleable() and hasText("Anonymous voting", substring = true))
            .assertIsOn()
            .assertIsNotEnabled()
    }

    @Test
    fun `screen readers cancel the uploads of a bubble with a custom action`() {
        setScreen(ChatPreviewData.uploading)

        val album = composeRule.onNodeWithContentDescription("10:11 AM", substring = true).fetchSemanticsNode()
        val cancel = album.config[SemanticsActions.CustomActions].first()
        composeRule.runOnIdle { cancel.action() }

        assertEquals("Cancel upload", cancel.label)
        assertEquals(
            listOf(ChatEvent.CancelUpload(listOf(71L, 72L))),
            events.filterIsInstance<ChatEvent.CancelUpload>(),
        )
    }

    @Test
    fun `bubbles that upload nothing offer no cancel`() {
        setScreen(ChatPreviewData.privateChat)

        val bubble = composeRule.onNodeWithContentDescription("9:46 AM", substring = true).fetchSemanticsNode()

        assertTrue(bubble.config[SemanticsActions.CustomActions].none { it.label == "Cancel upload" })
    }
}
