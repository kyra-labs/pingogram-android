package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
import dev.kyralabs.pingogram.core.domain.repository.UnansweredSettings
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.COMPACT
import dev.kyralabs.pingogram.feature.settings.ui.ComponentActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** What the Chats page shows and sends: its switches (whole rows, read as switches) and the pages its rows open. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class ChatsPageScreenTest {
    @get:Rule(order = 0)
    val activityRegistration = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatsPageEvent>()
    private val opened = mutableListOf<String>()

    private fun setScreen(state: ChatsPageUiState.Content = ChatsPageUiState.Content()) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatsPageScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    pages =
                        ChatsPagePages(
                            onOpenQuickReaction = { opened += "quickReaction" },
                            onOpenSnippets = { opened += "snippets" },
                            onOpenTranscription = { opened += "transcription" },
                            onOpenSwipeActions = { opened += "swipeActions" },
                            onOpenShownFirst = { opened += "shownFirst" },
                            onOpenRaiseToListen = { opened += "raiseToListen" },
                        ),
                )
            }
        }
    }

    private fun string(
        @StringRes id: Int,
    ) = RuntimeEnvironment.getApplication().getString(id)

    private fun node(
        @StringRes id: Int,
    ) = composeRule.onNodeWithText(string(id))

    private fun switchRow(
        @StringRes title: Int,
    ) = composeRule.onNode(
        hasText(string(title)) and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
    )

    private fun days(count: Int) =
        RuntimeEnvironment.getApplication().resources.getQuantityString(
            R.plurals.settings_unanswered_days,
            count,
            count,
        )

    private fun open(
        @StringRes title: Int,
    ) = RuntimeEnvironment.getApplication().getString(R.string.settings_split_row_open, string(title))

    /** A clickable node whose click is labelled [label] for screen readers. */
    private fun hasClickLabel(label: String) =
        SemanticsMatcher("has click label \"$label\"") {
            it.config.getOrNull(SemanticsActions.OnClick)?.label == label
        }

    private fun toggleState(state: ToggleableState) =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    @Test
    fun `its groups are Messages, Voice messages and Chat list`() {
        setScreen()

        node(R.string.settings_chat_section_messages).assertExists()
        node(R.string.settings_section_voice_messages).performScrollTo().assertExists()
        node(R.string.settings_section_chat_list).performScrollTo().assertExists()
    }

    @Test
    fun `the rows that open a page open theirs`() {
        setScreen()

        for (title in listOf(
            R.string.settings_double_tap,
            R.string.settings_snippets,
            R.string.settings_transcription,
            R.string.settings_swipe_actions,
            R.string.shown_first_title,
        )) {
            node(title).performScrollTo().performClick()
        }

        assertEquals(listOf("quickReaction", "snippets", "transcription", "swipeActions", "shownFirst"), opened)
        assertEquals(emptyList<ChatsPageEvent>(), events)
    }

    @Test
    fun `the Enter rows are switches whose taps change which Enter sends`() {
        setScreen()

        switchRow(R.string.settings_enter_sends_hardware).assert(toggleState(ToggleableState.On)).performClick()
        switchRow(R.string.settings_enter_sends_on_screen)
            .performScrollTo()
            .assert(toggleState(ToggleableState.Off))
            .performClick()

        assertEquals(
            listOf(
                ChatsPageEvent.SetSendKeys(SendKeys(hardwareKeyboard = false)),
                ChatsPageEvent.SetSendKeys(SendKeys(onScreenKeyboard = true)),
            ),
            events,
        )
    }

    @Test
    fun `raise to listen is a split row whose text opens its page and whose switch toggles it`() {
        setScreen(ChatsPageUiState.Content(raiseToListen = RaiseToListenSettings(enabled = false)))
        node(R.string.settings_pause_when_lowered).assertDoesNotExist()

        composeRule
            .onNode(hasClickLabel(open(R.string.settings_raise_to_listen)))
            .performScrollTo()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .performClick()
        composeRule
            .onNode(hasContentDescription(string(R.string.settings_raise_to_listen)) and isToggleable())
            .assert(toggleState(ToggleableState.Off))
            .performClick()

        assertEquals(listOf("raiseToListen"), opened)
        assertEquals(listOf(ChatsPageEvent.SetRaiseToListen(true)), events)
    }

    @Test
    fun `the skip silence row is a switch, off at first, whose tap turns it on`() {
        setScreen()

        switchRow(R.string.settings_skip_silence)
            .performScrollTo()
            .assert(toggleState(ToggleableState.Off))
            .assert(hasText(string(R.string.settings_skip_silence_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetSkipSilence(true)), events)
    }

    @Test
    fun `send silently by default is a switch, off at first, whose tap turns it on`() {
        setScreen()

        switchRow(R.string.settings_send_silently)
            .performScrollTo()
            .assert(toggleState(ToggleableState.Off))
            .assert(hasText(string(R.string.settings_send_silently_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetSendsSilently(true)), events)
    }

    @Test
    fun `send silently by default shows on, and a tap turns it off`() {
        setScreen(ChatsPageUiState.Content(sendsSilently = true))

        switchRow(R.string.settings_send_silently)
            .performScrollTo()
            .assert(toggleState(ToggleableState.On))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetSendsSilently(false)), events)
    }

    @Test
    fun `confirm bot commands is a switch, off at first, whose tap turns it on`() {
        setScreen()

        switchRow(R.string.settings_confirm_bot_commands)
            .performScrollTo()
            .assert(toggleState(ToggleableState.Off))
            .assert(hasText(string(R.string.settings_confirm_bot_commands_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetConfirmsBotCommands(true)), events)
    }

    @Test
    fun `confirm bot commands shows on, and a tap turns it off`() {
        setScreen(ChatsPageUiState.Content(confirmsBotCommands = true))

        switchRow(R.string.settings_confirm_bot_commands)
            .performScrollTo()
            .assert(toggleState(ToggleableState.On))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetConfirmsBotCommands(false)), events)
    }

    @Test
    fun `undo and redo is a switch, on at first, whose tap turns it off`() {
        setScreen()

        switchRow(R.string.settings_undo_redo)
            .performScrollTo()
            .assert(toggleState(ToggleableState.On))
            .assert(hasText(string(R.string.settings_undo_redo_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetShowsUndoRedo(false)), events)
    }

    @Test
    fun `undo and redo shows off, and a tap turns it on`() {
        setScreen(ChatsPageUiState.Content(showsUndoRedo = false))

        switchRow(R.string.settings_undo_redo)
            .performScrollTo()
            .assert(toggleState(ToggleableState.Off))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetShowsUndoRedo(true)), events)
    }

    @Test
    fun `the drafts button row is a switch, on at first, whose tap turns it off`() {
        setScreen()

        switchRow(R.string.settings_drafts_button)
            .performScrollTo()
            .assert(toggleState(ToggleableState.On))
            .assert(hasText(string(R.string.settings_drafts_button_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetShowDraftsButton(false)), events)
    }

    @Test
    fun `the unanswered button row is a switch, on at first, and its days choose when a chat counts`() {
        setScreen()

        switchRow(R.string.settings_unanswered_button)
            .performScrollTo()
            .assert(toggleState(ToggleableState.On))
            .assert(hasText(string(R.string.settings_unanswered_button_summary)))
            .performClick()
        composeRule.onNodeWithText(days(7)).performScrollTo()
        composeRule.assertAccessible()
        composeRule.onNodeWithText(days(7)).performClick()

        assertEquals(
            listOf(ChatsPageEvent.SetShowUnansweredButton(false), ChatsPageEvent.SetUnansweredAfterDays(7)),
            events,
        )
    }

    @Test
    fun `the unanswered days are dimmed while the button is off`() {
        setScreen(ChatsPageUiState.Content(unanswered = UnansweredSettings(showButton = false)))

        composeRule.onNodeWithText(days(7)).performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `the compact chat list row is a switch, off at first, whose tap turns it on`() {
        setScreen()

        switchRow(R.string.settings_compact_chat_list)
            .performScrollTo()
            .assert(toggleState(ToggleableState.Off))
            .assert(hasText(string(R.string.settings_compact_chat_list_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetCompactChatList(true)), events)
    }

    @Test
    fun `the notes and names row is a switch, on at first, whose tap turns it off`() {
        setScreen()

        switchRow(R.string.settings_notes_and_names)
            .performScrollTo()
            .assert(toggleState(ToggleableState.On))
            .assert(hasText(string(R.string.settings_notes_and_names_summary)))
            .performClick()

        assertEquals(listOf(ChatsPageEvent.SetNotesAndNames(false)), events)
    }
}
