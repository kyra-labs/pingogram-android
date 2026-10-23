package dev.kyralabs.pingogram.feature.chat.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.BotButtonStyle
import dev.kyralabs.pingogram.core.domain.model.ChatMemberStatus
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Contact
import dev.kyralabs.pingogram.core.domain.model.KeyboardButton
import dev.kyralabs.pingogram.core.domain.model.KeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.SUPERGROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.supergroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.text
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonTone
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardButton
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.ShareKind
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * A bot's keyboard in [ChatViewModel] (`ChatBotKeyboard`): which one the composer shows, what its buttons send or
 * share, only after the user chose and agreed, and a bot's request for a reply.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatBotKeyboardTest : ChatViewModelHarness() {
    private val bot =
        user(BOT, firstName = "Shop Bot", type = UserType.Bot).copy(usernames = persistentListOf("shop_bot"))
    private val me = user(ME, firstName = "Ada", lastName = "Lovelace").copy(phoneNumber = "447911123456")

    /** The chat with the bot, its message [MESSAGE] carrying [markup] and named as the chat's keyboard. */
    private fun TestScope.botConversation(
        markup: ReplyMarkup?,
        chatId: Long = BOT,
        loaded: Boolean = true,
        handle: SavedStateHandle = savedState,
        topic: MessageTopic? = null,
    ): ChatViewModel {
        users.setUsers(me, bot, user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace"))
        val type = if (chatId == BOT) ChatType.Private(BOT) else groupChat.type
        chats.setChats(chat(id = chatId, type = type, title = "Shop").copy(replyMarkupMessageId = MESSAGE))
        val keyboardMessage = keyboardMessage(markup, chatId)
        messages.setHistory(chatId, if (loaded) history(keyboardMessage) else history(message(1, chatId)))
        messages.onMessage =
            { _, id -> if (id == MESSAGE) Outcome.Success(keyboardMessage) else Outcome.Failure(DomainError.NotFound) }
        val viewModel = viewModel(chatId, handle = handle, topic = topic)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.botReply.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.botKeyboardDialog.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.keyboardShareSheet.collect {} }
        runCurrent()
        return viewModel
    }

    private fun keyboardMessage(
        markup: ReplyMarkup?,
        chatId: Long = BOT,
    ) = message(MESSAGE, chatId, sender = MessageSender.User(BOT)).copy(replyMarkup = markup)

    private fun keyboard(
        vararg buttons: KeyboardButton,
        resize: Boolean = false,
        oneTime: Boolean = false,
        persistent: Boolean = false,
        placeholder: String = "",
        forceReply: Boolean = false,
    ) = ReplyMarkup.ShowKeyboard(
        rows = buttons.map { persistentListOf(it) }.toImmutableList(),
        resize = resize,
        oneTime = oneTime,
        persistent = persistent,
        personal = false,
        placeholder = placeholder,
        forceReply = forceReply,
    )

    private fun button(
        text: String,
        type: KeyboardButtonType = KeyboardButtonType.Text,
        style: BotButtonStyle = BotButtonStyle.Default,
    ) = KeyboardButton(text, style, type)

    private fun TestScope.press(
        viewModel: ChatViewModel,
        row: Int = 0,
    ) = send(viewModel, BotKeyboardEvent.Press(MESSAGE, BotButtonPosition(row, 0)))

    private val ChatViewModel.replyKeyboard get() = botReply.value.keyboard

    private val ChatViewModel.sheet get() = keyboardShareSheet.value

    @Nested
    inner class Which {
        @Test
        fun `shows the keyboard of the message the chat names, with its options`() =
            runViewModelTest {
                val viewModel =
                    botConversation(
                        keyboard(
                            button("Menu"),
                            button("Phone", KeyboardButtonType.RequestPhoneNumber, BotButtonStyle.Primary),
                            resize = true,
                            oneTime = true,
                            placeholder = "Pick one",
                        ),
                    )

                val keyboard = checkNotNull(viewModel.replyKeyboard)
                assertEquals(
                    persistentListOf(
                        persistentListOf(BotKeyboardButton("Menu")),
                        persistentListOf(BotKeyboardButton("Phone", tone = BotButtonTone.Primary)),
                    ),
                    keyboard.buttons.rows,
                )
                assertEquals(listOf(true, true, false), listOf(keyboard.resize, keyboard.oneTime, keyboard.persistent))
                assertEquals(persistentSetOf(BotButtonPosition(0, 0)), keyboard.sendsText)
                assertEquals("Pick one", viewModel.botReply.value.placeholder)
            }

        @Test
        fun `fetches the keyboard's message when the history does not hold it`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Menu")), loaded = false)

                assertEquals(BOT to MESSAGE, messages.messageRequests.last())
                assertEquals(MESSAGE, viewModel.replyKeyboard?.messageId)
            }

        @Test
        fun `a removed keyboard, or none, shows nothing, and a bot's edit changes the buttons in place`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Page 1")))

                messages.setHistory(BOT, history(keyboardMessage(keyboard(button("Page 2")))))
                runCurrent()
                assertEquals(
                    "Page 2",
                    viewModel.replyKeyboard
                        ?.buttons
                        ?.rows
                        ?.single()
                        ?.single()
                        ?.text,
                )

                messages.setHistory(BOT, history(keyboardMessage(ReplyMarkup.RemoveKeyboard(personal = false))))
                runCurrent()
                assertNull(viewModel.replyKeyboard)

                chats.setChats(chat(id = BOT, title = "Shop"))
                runCurrent()
                assertNull(viewModel.replyKeyboard)
            }

        @Test
        fun `a topic's conversation shows no keyboard`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Menu")), topic = MessageTopic.Thread(MESSAGE))

                assertNull(viewModel.replyKeyboard)
            }
    }

    @Nested
    inner class Text {
        @Test
        fun `a text button sends its text, without a reply in a private chat`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("🛒 Cart")))

                press(viewModel)

                assertEquals(text("🛒 Cart"), messages.sent.single().second)
                assertEquals(listOf<Long?>(null), messages.sentReplies)
            }

        @Test
        fun `in a group it replies to the keyboard's message, or where the composer replies`() =
            runViewModelTest {
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                val viewModel = botConversation(keyboard(button("Menu")), chatId = GROUP_CHAT)
                messages.setHistory(
                    GROUP_CHAT,
                    history(keyboardMessage(keyboard(button("Menu")), GROUP_CHAT), message(1, GROUP_CHAT)),
                )
                runCurrent()

                press(viewModel)
                send(viewModel, ChatEvent.Reply(1))
                press(viewModel)

                assertEquals(listOf<Long?>(MESSAGE, 1), messages.sentReplies)
                assertNull((viewModel.state.composer as ComposerUiState.Available).bar)
            }

        @Test
        fun `a failure is a snackbar, and the composer's reply comes back`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Menu")))
                viewModel.effects.test {
                    send(viewModel, ChatEvent.Reply(MESSAGE))
                    assertEquals(ChatEffect.FocusComposer, awaitItem())
                    for (error in listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized)) {
                        messages.onSendText = { Outcome.Failure(error) }

                        press(viewModel)

                        assertEquals(ChatEffect.ShowError(error), awaitItem())
                    }
                }
                assertEquals(MESSAGE, (viewModel.state.composer as ComposerUiState.Available).bar?.messageId)
            }

        @Test
        fun `shows the button's progress while it is sent, and a second tap sends nothing more`() =
            runViewModelTest {
                val sent = CompletableDeferred<Outcome<Unit>>()
                messages.onSendText = { sent.await() }
                val viewModel = botConversation(keyboard(button("Menu")))

                press(viewModel)
                press(viewModel)
                assertEquals(persistentSetOf(BotButtonPosition(0, 0)), viewModel.replyKeyboard?.buttons?.pending)

                sent.complete(Outcome.Success(Unit))
                runCurrent()
                assertEquals(1, messages.sent.size)
                assertEquals(persistentSetOf<BotButtonPosition>(), viewModel.replyKeyboard?.buttons?.pending)
            }
    }

    @Nested
    inner class Requests {
        @Test
        fun `the phone number goes only after the user agreed, as their own contact`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Phone", KeyboardButtonType.RequestPhoneNumber)))

                press(viewModel)
                val dialog = viewModel.botKeyboardDialog.value as BotKeyboardDialog.SharePhone
                assertEquals(UiText.Plain("@shop_bot"), dialog.botName)
                assertTrue(attachments.sentContacts.isEmpty())

                send(viewModel, BotKeyboardEvent.SharePhone)

                assertEquals(
                    Contact("447911123456", "Ada", "Lovelace", ME) to null,
                    attachments.sentContacts.single(),
                )
                assertNull(viewModel.botKeyboardDialog.value)
            }

        @Test
        fun `dismissing the phone number question sends nothing`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Phone", KeyboardButtonType.RequestPhoneNumber)))

                press(viewModel)
                send(viewModel, BotKeyboardEvent.DismissDialog, BotKeyboardEvent.SharePhone)

                assertNull(viewModel.botKeyboardDialog.value)
                assertTrue(attachments.sentContacts.isEmpty())
            }

        @Test
        fun `a refused contact is a snackbar`() =
            runViewModelTest {
                attachments.onSendContact = { Outcome.Failure(DomainError.Network) }
                val viewModel = botConversation(keyboard(button("Phone", KeyboardButtonType.RequestPhoneNumber)))
                press(viewModel)

                viewModel.effects.test {
                    send(viewModel, BotKeyboardEvent.SharePhone)

                    assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
                }
            }

        @Test
        fun `the location opens the attach menu's sheet, and a Mini App says it is not supported`() =
            runViewModelTest {
                val viewModel =
                    botConversation(
                        keyboard(
                            button("Here", KeyboardButtonType.RequestLocation),
                            button("App", KeyboardButtonType.WebApp("https://example.org")),
                        ),
                    )

                viewModel.effects.test {
                    press(viewModel)
                    assertEquals(BotKeyboardEffect.ShareLocation, awaitItem())

                    press(viewModel, row = 1)
                    assertEquals(ChatEffect.MiniAppsUnsupported, awaitItem())
                }
                assertTrue(attachments.sentLocations.isEmpty())
            }

        @Test
        fun `a poll button opens the poll form, a quiz where the button requires one`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Quiz", KeyboardButtonType.RequestPoll(quiz = true))))

                press(viewModel)

                assertTrue(viewModel.state.poll != null)
                assertEquals(true, viewModel.pollEditor.requiredQuiz)
                assertTrue(viewModel.pollEditor.isQuiz)

                send(viewModel, ChatEvent.DismissPoll, ChatEvent.OpenPoll)
                assertNull(viewModel.pollEditor.requiredQuiz)
            }
    }

    @Nested
    inner class Users {
        private val request =
            KeyboardButtonType.RequestUsers(
                id = 4,
                isBot = false,
                isPremium = null,
                maxQuantity = 2,
                requestName = true,
                requestUsername = false,
                requestPhoto = false,
            )

        @Test
        fun `offers people of the chat list and the contacts, never the bot, Saved Messages or other bots`() =
            runViewModelTest {
                users.setUsers(me, bot, user(ADA), user(GRACE), user(OTHER_BOT, type = UserType.Bot))
                chats.mainList.value = listOf(chat(ME), chat(BOT), chat(ADA), chat(OTHER_BOT), groupChat)
                search.contactChats = listOf(chat(GRACE), chat(ADA))
                val viewModel = botConversation(keyboard(button("Friends", request)))
                users.setUsers(me, bot, user(ADA), user(GRACE), user(OTHER_BOT, type = UserType.Bot))
                runCurrent()

                press(viewModel)
                runCurrent()

                val sheet = checkNotNull(viewModel.sheet)
                assertEquals(ShareKind.Users, sheet.kind)
                assertEquals(2, sheet.maxCount)
                assertEquals(listOf(ADA, GRACE), sheet.targets.map { it.chatId })
            }

        @Test
        fun `picks up to the button's number, then shares them with the bot`() =
            runViewModelTest {
                chats.mainList.value = listOf(chat(ADA), chat(GRACE), chat(OTHER_USER))
                users.setUsers(me, bot, user(ADA), user(GRACE), user(OTHER_USER))
                val viewModel = botConversation(keyboard(button("Friends", request)))
                press(viewModel)

                send(
                    viewModel,
                    BotKeyboardEvent.ToggleTarget(ADA),
                    BotKeyboardEvent.ToggleTarget(GRACE),
                    BotKeyboardEvent.ToggleTarget(OTHER_USER),
                )
                assertEquals(persistentSetOf(ADA, GRACE), viewModel.sheet?.selected)
                assertTrue(botButtons.sharedUsers.isEmpty())

                send(viewModel, BotKeyboardEvent.Share)

                assertEquals(Triple(MESSAGE, 4, listOf(ADA, GRACE)), botButtons.sharedUsers.single())
                assertNull(viewModel.sheet)
            }

        @Test
        fun `a refused share stays in the sheet with its reason`() =
            runViewModelTest {
                botButtons.onShare = { Outcome.Failure(DomainError.Unauthorized) }
                val viewModel = botConversation(keyboard(button("Friends", request)))
                press(viewModel)

                send(viewModel, BotKeyboardEvent.ToggleTarget(ADA), BotKeyboardEvent.Share)

                assertEquals(DomainError.Unauthorized, viewModel.sheet?.error)
                assertEquals(false, viewModel.sheet?.isSharing)
            }

        @Test
        fun `dismissing the sheet shares nothing`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Friends", request)))
                press(viewModel)

                send(viewModel, BotKeyboardEvent.ToggleTarget(ADA), BotKeyboardEvent.DismissShare)
                send(viewModel, BotKeyboardEvent.Share)

                assertNull(viewModel.sheet)
                assertTrue(botButtons.sharedUsers.isEmpty())
            }
    }

    @Nested
    inner class Chats {
        private fun request(
            isChannel: Boolean = false,
            userRights: AdministratorRights? = null,
            botIsMember: Boolean = false,
        ) = KeyboardButtonType.RequestChat(
            id = 6,
            isChannel = isChannel,
            isForum = null,
            hasUsername = null,
            isCreated = false,
            userRights = userRights,
            botRights = null,
            botIsMember = botIsMember,
            requestTitle = true,
            requestUsername = false,
            requestPhoto = false,
        )

        private val channel = chat(id = CHANNEL, type = ChatType.Supergroup(SUPERGROUP_ID, isChannel = true))

        @Test
        fun `offers the groups the user is in, and shares the one picked`() =
            runViewModelTest {
                chats.mainList.value = listOf(chat(ADA), groupChat, channel)
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                groups.supergroupMap.value = mapOf(SUPERGROUP_ID to supergroup(10, isChannel = true))
                val viewModel = botConversation(keyboard(button("Group", request(botIsMember = true))))

                press(viewModel)
                runCurrent()
                val sheet = checkNotNull(viewModel.sheet)
                assertEquals(ShareKind.Group, sheet.kind)
                assertEquals(listOf(GROUP_CHAT), sheet.targets.map { it.chatId })
                assertEquals(2, sheet.notes.size)

                send(viewModel, BotKeyboardEvent.ToggleTarget(GROUP_CHAT), BotKeyboardEvent.Share)

                assertEquals(Triple(MESSAGE, 6, GROUP_CHAT), botButtons.sharedChats.single())
            }

        @Test
        fun `asking for the user's rights leaves out chats where they are no administrator`() =
            runViewModelTest {
                chats.mainList.value = listOf(groupChat, channel)
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                groups.supergroupMap.value =
                    mapOf(
                        SUPERGROUP_ID to
                            supergroup(
                                10,
                                status = ChatMemberStatus.Administrator(canPostMessages = true),
                            ),
                    )
                val rights = AdministratorRights(canPostMessages = true)
                val viewModel = botConversation(keyboard(button("Channel", request(isChannel = true, rights))))

                press(viewModel)
                runCurrent()

                assertEquals(ShareKind.Channel, viewModel.sheet?.kind)
                assertEquals(listOf(CHANNEL), viewModel.sheet?.targets?.map { it.chatId })
            }

        @Test
        fun `one chat at a time`() =
            runViewModelTest {
                chats.mainList.value = listOf(groupChat)
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                val viewModel = botConversation(keyboard(button("Group", request())))
                press(viewModel)

                send(viewModel, BotKeyboardEvent.ToggleTarget(GROUP_CHAT), BotKeyboardEvent.ToggleTarget(OTHER_GROUP))

                assertEquals(persistentSetOf(OTHER_GROUP), viewModel.sheet?.selected)
            }
    }

    @Nested
    inner class ForceReply {
        @Test
        fun `a bot's request for a reply starts one, with its hint, once`() =
            runViewModelTest {
                val viewModel = botConversation(ReplyMarkup.ForceReply(personal = false, placeholder = "Your name"))
                viewModel.effects.test { assertEquals(ChatEffect.FocusComposer, awaitItem()) }

                val composer = viewModel.state.composer as ComposerUiState.Available
                assertEquals(MESSAGE, (composer.bar as ComposerBar.Reply).messageId)
                assertEquals("Your name", viewModel.botReply.value.placeholder)
                assertNull(viewModel.replyKeyboard)

                send(viewModel, ChatEvent.CancelReply)
                messages.setHistory(
                    BOT,
                    history(keyboardMessage(ReplyMarkup.ForceReply(false, "Your name")), message(1, BOT)),
                )
                runCurrent()

                assertNull((viewModel.state.composer as ComposerUiState.Available).bar)
                assertEquals("", viewModel.botReply.value.placeholder)
            }

        @Test
        fun `not again after process death`() =
            runViewModelTest {
                val handle = SavedStateHandle()
                val first = botConversation(ReplyMarkup.ForceReply(personal = false, placeholder = ""), handle = handle)
                send(first, ChatEvent.CancelReply)
                clear(first)

                val second =
                    botConversation(ReplyMarkup.ForceReply(personal = false, placeholder = ""), handle = handle)

                assertNull((second.state.composer as ComposerUiState.Available).bar)
            }

        @Test
        fun `a keyboard that asks for a reply starts one too`() =
            runViewModelTest {
                val viewModel = botConversation(keyboard(button("Yes"), forceReply = true))

                assertEquals(MESSAGE, (viewModel.state.composer as ComposerUiState.Available).bar?.messageId)
                assertEquals(MESSAGE, viewModel.replyKeyboard?.messageId)
            }
    }

    private companion object {
        const val BOT = 500L
        const val OTHER_BOT = 501L
        const val OTHER_USER = 502L
        const val CHANNEL = -1_000_000_000_200L
        const val OTHER_GROUP = -101L
        const val MESSAGE = 40L
    }
}
