package dev.kyralabs.pingogram.feature.settings.ui.chats

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.kyralabs.pingogram.core.domain.model.SendKeys
import dev.kyralabs.pingogram.core.domain.repository.RaiseToListenSettings
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
    fun `pause when lowered shows below raise to listen only while it is on`() {
        setScreen(ChatsPageUiState.Content(raiseToListen = RaiseToListenSettings(enabled = false)))
        node(R.string.settings_pause_when_lowered).assertDoesNotExist()

        switchRow(R.string.settings_raise_to_listen).performScrollTo().performClick()

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
}
