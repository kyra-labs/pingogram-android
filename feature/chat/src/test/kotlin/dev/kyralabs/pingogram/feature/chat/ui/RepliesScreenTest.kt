package dev.kyralabs.pingogram.feature.chat.ui

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuData
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSession
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.testing.assertAccessible
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.AttachPreviewSheet
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.preview.AttachPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ChatPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ForwardPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.preview.ReplyPreviewData
import dev.kyralabs.pingogram.feature.chat.ui.reply.ReplyChatsBottomSheet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Quote replies, replies in another chat, message links and replacing media on screen (`docs/DESIGN_SYSTEM.md` §6
 * "Message actions", "Composer"): "Quote" in the lifted message's text selection, the reply bar and bubbles showing
 * quotes, the chat picker, "Copy link" in the menu and on the clipboard, and "Replace" in the edit bar and the attach
 * preview.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
@OptIn(ExperimentalFoundationApi::class) // ComposeFoundationFlags
class RepliesScreenTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val events = mutableListOf<ChatEvent>()

    private var newContextMenu = true

    @Before
    fun useNewContextMenu() {
        // The context menu Pingogram adds "Quote" to, whose provider the test can see.
        newContextMenu = ComposeFoundationFlags.isNewContextMenuEnabled
        ComposeFoundationFlags.isNewContextMenuEnabled = true
    }

    @After
    fun restoreFlags() {
        ComposeFoundationFlags.isNewContextMenuEnabled = newContextMenu
    }

    private fun string(
        @StringRes id: Int,
        vararg args: Any,
    ): String = RuntimeEnvironment.getApplication().getString(id, *args)

    @Test
    // Selecting shows the platform magnifier, whose window Robolectric cannot draw.
    @Config(shadows = [NoMagnifier::class])
    fun `Quote in the lifted message's selection replies quoting what is selected`() {
        val toolbar = RecordingMenuProvider()
        var state by mutableStateOf(ChatPreviewData.privateChat)
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalTextContextMenuToolbarProvider provides toolbar) {
                    ChatScreen(
                        state = state,
                        onEvent = { event ->
                            events += event
                            state =
                                when (event) {
                                    is ChatEvent.ShowMessageActions -> {
                                        state.copy(
                                            messageActions =
                                                ChatPreviewData.ownMessageActions.copy(
                                                    messageId = event.messageId,
                                                    reactionsMessageId = event.messageId,
                                                ),
                                        )
                                    }

                                    is ReplyEvent.Quote -> {
                                        state.copy(messageActions = null)
                                    }

                                    else -> {
                                        state
                                    }
                                }
                        },
                        onBack = {},
                    )
                }
            }
        }
        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule
            .onNode(hasText("See you there!") and hasAnyAncestor(hasTestTag(LIFTED_MESSAGE_TAG)))
            .performTouchInput { longClick() }
        composeRule.waitForIdle()
        val quote = toolbar.item(string(R.string.quote_action))
        composeRule.runOnUiThread { quote.onClick(NoSession) }
        composeRule.waitForIdle()

        val event = events.filterIsInstance<ReplyEvent.Quote>().single()
        assertEquals(11L, event.messageId)
        assertTrue("a word of the text is selected", event.selected.any { it.isNotBlank() && it in "See you there!" })
    }

    @Test
    fun `a quote shows its mark in the reply bar and in the bubbles`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val state = ReplyPreviewData.quotes.copy(composer = ReplyPreviewData.quoting.composer)
                ChatScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }

        // The bar's mark is named; the bubbles say it in their description.
        composeRule.onNodeWithContentDescription(string(R.string.quote_label)).assertExists()
        composeRule.onNodeWithText("still on for tonight").assertExists()
        composeRule
            .onNodeWithContentDescription(
                string(R.string.chat_bubble_quote, "Ada Lovelace", "still on for tonight"),
                substring = true,
            ).assertExists()
    }

    @Test
    fun `a reply in another chat names that chat, and its bar shows no message here`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = ReplyPreviewData.replyingElsewhere, onEvent = { events += it }, onBack = {})
            }
        }

        composeRule
            .onNodeWithText(string(R.string.composer_reply_elsewhere, "Linus Torvalds", "Compose Developers"))
            .performClick()
        composeRule.waitForIdle()

        assertTrue(events.none { it is ChatEvent.OpenReply })
    }

    @Test
    fun `the chat picker opens the chat tapped`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ReplyChatsBottomSheet(ReplyPreviewData.chatPicker, onEvent = { events += it })
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.reply_chats_title)).assertExists()
        composeRule.onNodeWithText("Analytical Engine team").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ReplyEvent.PickChat(TEAM_CHAT)), events.filterIsInstance<ReplyEvent.PickChat>())
    }

    @Test
    fun `Copy link and Reply in another chat are actions of the menu`() {
        var state by mutableStateOf(ChatPreviewData.privateChat)
        val actions =
            ChatPreviewData.incomingMessageActions.copy(
                messageId = 11,
                reactionsMessageId = 11,
                actions =
                    persistentListOf(
                        MessageAction.Reply,
                        MessageAction.ReplyElsewhere,
                        MessageAction.Copy,
                        MessageAction.CopyLink,
                    ),
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        state =
                            if (event is ChatEvent.ShowMessageActions) {
                                state.copy(messageActions = actions)
                            } else {
                                state.copy(messageActions = null)
                            }
                    },
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.message_action_copy_link)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription(ADA_LAST).performTouchInput { longClick() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(string(R.string.message_action_reply_elsewhere)).performClick()
        composeRule.waitForIdle()

        assertEquals(
            listOf(ReplyEvent.CopyLink(11), ReplyEvent.ReplyElsewhere(11)),
            events.filterIsInstance<ReplyEvent>(),
        )
    }

    @Test
    fun `a copied link goes to the clipboard`() {
        val effects = Channel<ChatEffect>(Channel.BUFFERED)
        val copied = mutableListOf<String>()
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatEffects(
                    effects = remember { effects.receiveAsFlow() },
                    snackbarHostState = remember { SnackbarHostState() },
                    onOpenMedia = {},
                    copyText = { copied += it },
                )
            }
        }

        effects.trySend(ReplyEffect.CopyLink("https://t.me/compose/25"))
        composeRule.waitForIdle()

        assertEquals(listOf("https://t.me/compose/25"), copied)
    }

    @Test
    fun `the edit bar's Replace opens the picker`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                ChatScreen(state = ReplyPreviewData.editingMedia, onEvent = { events += it }, onBack = {})
            }
        }

        composeRule.onNodeWithContentDescription(string(R.string.composer_replace_media)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ReplyEvent.ReplaceMedia), events.filterIsInstance<ReplyEvent>())
    }

    @Test
    fun `the replacement preview replaces, or chooses another`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                AttachPreviewSheet(
                    preview = AttachPreviewData.replacement,
                    caption = remember { ComposerText() },
                    onEvent = { events += it },
                    onSend = { _: SendOptions -> error("A replacement is not sent") },
                )
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(string(R.string.attach_replace_choose)).performClick()
        composeRule.onNodeWithText(string(R.string.attach_replace_confirm)).performClick()
        composeRule.waitForIdle()

        assertEquals(listOf(ReplyEvent.ReplaceMedia, ReplyEvent.SaveReplacement), events.filterIsInstance<ReplyEvent>())
    }

    @Test
    fun `the new parts are accessible`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Column {
                    ChatScreen(state = ReplyPreviewData.editingMedia, onEvent = {}, onBack = {})
                }
            }
        }

        composeRule.assertAccessible()
    }

    /** A toolbar that keeps what the selection's menu offers, and stays shown until closed. */
    private class RecordingMenuProvider : TextContextMenuProvider {
        private var data: TextContextMenuData? = null

        override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
            data = dataProvider.data()
            awaitCancellation()
        }

        fun item(label: String): TextContextMenuItem =
            data
                ?.components
                .orEmpty()
                .filterIsInstance<TextContextMenuItem>()
                .single { it.label == label }
    }

    private object NoSession : TextContextMenuSession {
        override fun close() = Unit
    }

    private companion object {
        /** Ada's newest message in [ChatPreviewData.privateChat], as screen readers hear it. */
        const val ADA_LAST = "Ada, 9:46 AM: See you there!"

        /** The team's chat in [ForwardPreviewData.targets]. */
        const val TEAM_CHAT = 3L
    }
}
