package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.BotButtonStyle
import dev.kyralabs.pingogram.core.domain.model.CallbackAnswer
import dev.kyralabs.pingogram.core.domain.model.CallbackData
import dev.kyralabs.pingogram.core.domain.model.CallbackPayload
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButton
import dev.kyralabs.pingogram.core.domain.model.InlineKeyboardButtonType
import dev.kyralabs.pingogram.core.domain.model.LoginUrlInfo
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.ReplyMarkup
import dev.kyralabs.pingogram.core.domain.model.SwitchInlineTarget
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.BASIC_GROUP_ID
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.basicGroup
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.groupChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonDialog
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonMark
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonPosition
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotButtonTone
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboard
import dev.kyralabs.pingogram.feature.chat.ui.bots.BotKeyboardButton
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The buttons bots attach under messages in [ChatViewModel]: the keyboard each bubble shows, kept current by the
 * bot's edits, and what a tap on each kind of button does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatBotButtonsTest : ChatViewModelHarness() {
    private val bot =
        user(
            BOT,
            firstName = "Poll Bot",
            type = UserType.Bot,
        ).copy(usernames = persistentListOf("pollbot"))
    private val botChat = chat(id = BOT, title = "Poll Bot")

    /** The chat with the bot, its message [MESSAGE] carrying [buttons] in one row each. */
    private fun TestScope.botConversation(vararg buttons: InlineKeyboardButton): ChatViewModel {
        users.setUsers(user(ME, firstName = "Ada", lastName = "Lovelace"), bot)
        chats.setChats(botChat)
        messages.setHistory(BOT, history(botMessage(*buttons)))
        val viewModel = viewModel(BOT)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.botButtonDialog.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.switchInlineSheet.collect {} }
        runCurrent()
        return viewModel
    }

    private fun botMessage(vararg buttons: InlineKeyboardButton) =
        message(MESSAGE, BOT, sender = MessageSender.User(BOT)).copy(
            replyMarkup =
                ReplyMarkup.InlineKeyboard(buttons.map { persistentListOf(it) }.toImmutableList()),
        )

    private fun TestScope.press(
        viewModel: ChatViewModel,
        row: Int = 0,
    ) = send(viewModel, BotButtonEvent.Press(MESSAGE, BotButtonPosition(row, 0)))

    private val ChatViewModel.keyboard get() = bubble(MESSAGE).keyboard

    private val ChatViewModel.dialog get() = botButtonDialog.value

    @Nested
    inner class Keyboard {
        @Test
        fun `shows the bot's buttons under the bubble, marked and toned as they act`() =
            runViewModelTest {
                val viewModel =
                    botConversation(
                        callback("Vote"),
                        button("Site", InlineKeyboardButtonType.Url("https://example.org")),
                        button("Share", InlineKeyboardButtonType.SwitchInline("q", SwitchInlineTarget.CurrentChat)),
                        button("Delete", InlineKeyboardButtonType.Callback(DATA), BotButtonStyle.Danger),
                        button("Off", InlineKeyboardButtonType.Disabled),
                    )

                assertEquals(
                    BotKeyboard(
                        messageId = MESSAGE,
                        rows =
                            persistentListOf(
                                persistentListOf(BotKeyboardButton("Vote")),
                                persistentListOf(BotKeyboardButton("Site", BotButtonMark.Link)),
                                persistentListOf(BotKeyboardButton("Share", BotButtonMark.SwitchInline)),
                                persistentListOf(BotKeyboardButton("Delete", tone = BotButtonTone.Danger)),
                                persistentListOf(BotKeyboardButton("Off", isEnabled = false)),
                            ),
                    ),
                    viewModel.keyboard,
                )
            }

        @Test
        fun `a bot's edit changes the buttons in place`() =
            runViewModelTest {
                val viewModel = botConversation(callback("Page 1"))

                messages.setHistory(BOT, history(botMessage(callback("Page 2"))))
                runCurrent()

                assertEquals(
                    "Page 2",
                    viewModel.keyboard
                        ?.rows
                        ?.single()
                        ?.single()
                        ?.text,
                )
                messages.setHistory(BOT, history(message(MESSAGE, BOT, sender = MessageSender.User(BOT))))
                runCurrent()
                assertNull(viewModel.keyboard)
            }

        @Test
        fun `messages without an inline keyboard show none`() =
            runViewModelTest {
                chats.setChats(botChat)
                val showKeyboard =
                    ReplyMarkup.ShowKeyboard(
                        persistentListOf(),
                        resize = true,
                        oneTime = false,
                        persistent = false,
                        personal = false,
                        placeholder = "",
                    )
                messages.setHistory(BOT, history(message(MESSAGE, BOT).copy(replyMarkup = showKeyboard)))

                assertNull(viewModel(BOT).keyboard)
            }
    }

    @Nested
    inner class Callback {
        @Test
        fun `shows the button's progress while the bot is asked, then the answer in a snackbar`() =
            runViewModelTest {
                val answer = CompletableDeferred<Outcome<CallbackAnswer>>()
                botButtons.onAnswer = { answer.await() }
                val viewModel = botConversation(callback("Vote"))

                viewModel.effects.test {
                    press(viewModel)
                    assertEquals(persistentSetOf(BotButtonPosition(0, 0)), viewModel.keyboard?.pending)
                    assertEquals(MESSAGE to CallbackPayload.Data(DATA), botButtons.answered.single())

                    answer.complete(Outcome.Success(CallbackAnswer.Answered("Thanks!", showAlert = false, url = "")))
                    runCurrent()

                    assertEquals(BotButtonEffect.Answer("Thanks!"), awaitItem())
                }
                assertEquals(persistentSetOf<BotButtonPosition>(), viewModel.keyboard?.pending)
            }

        @Test
        fun `a second tap while the bot is asked sends nothing more`() =
            runViewModelTest {
                botButtons.onAnswer = { CompletableDeferred<Outcome<CallbackAnswer>>().await() }
                val viewModel = botConversation(callback("Vote"))

                press(viewModel)
                press(viewModel)

                assertEquals(1, botButtons.answered.size)
            }

        @Test
        fun `an alert answer shows in a dialog, and its link opens`() =
            runViewModelTest {
                botButtons.onAnswer = {
                    Outcome.Success(CallbackAnswer.Answered("Voted!", showAlert = true, url = "https://t.me/pollbot"))
                }
                val viewModel = botConversation(callback("Vote"))

                viewModel.effects.test {
                    press(viewModel)

                    assertEquals(ChatEffect.OpenUrl("https://t.me/pollbot"), awaitItem())
                }
                assertEquals(BotButtonDialog.Alert("Voted!"), viewModel.dialog)

                send(viewModel, BotButtonEvent.DismissDialog)
                assertNull(viewModel.dialog)
            }

        @Test
        fun `a bot that does not answer just ends the progress`() =
            runViewModelTest {
                botButtons.onAnswer = { Outcome.Success(CallbackAnswer.NoAnswer) }
                val viewModel = botConversation(callback("Vote"))

                viewModel.effects.test {
                    press(viewModel)

                    expectNoEvents()
                }
                assertEquals(persistentSetOf<BotButtonPosition>(), viewModel.keyboard?.pending)
                assertNull(viewModel.dialog)
            }

        @Test
        fun `failures are snackbars`() =
            runViewModelTest {
                val viewModel = botConversation(callback("Vote"))
                for (error in listOf(DomainError.FloodWait(30), DomainError.Network, DomainError.Unauthorized)) {
                    botButtons.onAnswer = { Outcome.Failure(error) }

                    viewModel.effects.test {
                        press(viewModel)

                        assertEquals(ChatEffect.ShowError(error), awaitItem())
                    }
                }
            }

        @Test
        fun `a button no longer there is not possible any more`() =
            runViewModelTest {
                val viewModel = botConversation(callback("Vote"))

                viewModel.effects.test {
                    press(viewModel, row = 3)

                    assertEquals(ChatEffect.ActionUnavailable, awaitItem())
                }
                assertTrue(botButtons.answered.isEmpty())
            }
    }

    @Nested
    inner class Password {
        @Test
        fun `asks for the password first and sends it with the button's data`() =
            runViewModelTest {
                botButtons.onAnswer = { Outcome.Success(CallbackAnswer.Answered("Transferred", false, "")) }
                val viewModel = botConversation(button("Transfer", InlineKeyboardButtonType.CallbackWithPassword(DATA)))

                press(viewModel)
                assertEquals(BotButtonDialog.Password(UiText.Plain("@pollbot")), viewModel.dialog)
                assertTrue(botButtons.answered.isEmpty())

                viewModel.botButtonPassword.setTextAndPlaceCursorAtEnd("secret")
                viewModel.effects.test {
                    send(viewModel, BotButtonEvent.SubmitPassword)

                    assertEquals(BotButtonEffect.Answer("Transferred"), awaitItem())
                }
                assertEquals(CallbackPayload.DataWithPassword("secret", DATA), botButtons.answered.single().second)
                assertNull(viewModel.dialog)
                assertEquals("", viewModel.botButtonPassword.text.toString())
            }

        @Test
        fun `a wrong password is said in the question, which stays`() =
            runViewModelTest {
                botButtons.onAnswer = { Outcome.Failure(DomainError.PasswordInvalid) }
                val viewModel = botConversation(button("Transfer", InlineKeyboardButtonType.CallbackWithPassword(DATA)))
                press(viewModel)

                viewModel.botButtonPassword.setTextAndPlaceCursorAtEnd("wrong")
                send(viewModel, BotButtonEvent.SubmitPassword)

                assertEquals(
                    BotButtonDialog.Password(
                        UiText.Plain("@pollbot"),
                        isChecking = false,
                        error = DomainError.PasswordInvalid,
                    ),
                    viewModel.dialog,
                )
            }

        @Test
        fun `the password never reaches the saved state`() =
            runViewModelTest {
                val viewModel = botConversation(button("Transfer", InlineKeyboardButtonType.CallbackWithPassword(DATA)))
                press(viewModel)

                viewModel.botButtonPassword.setTextAndPlaceCursorAtEnd("secret")

                assertFalse(savedState.keys().any { savedState.get<Any?>(it).toString().contains("secret") })
            }
    }

    @Nested
    inner class Links {
        @Test
        fun `a Telegram link, or one shown as its text, opens at once`() =
            runViewModelTest {
                val viewModel =
                    botConversation(
                        button("Channel", InlineKeyboardButtonType.Url("https://t.me/material")),
                        button("https://example.org", InlineKeyboardButtonType.Url("https://example.org")),
                    )

                viewModel.effects.test {
                    press(viewModel, row = 0)
                    assertEquals(ChatEffect.OpenUrl("https://t.me/material"), awaitItem())
                    press(viewModel, row = 1)
                    assertEquals(ChatEffect.OpenUrl("https://example.org"), awaitItem())
                }
                assertNull(viewModel.dialog)
            }

        @Test
        fun `a link its text hides opens only once confirmed`() =
            runViewModelTest {
                val viewModel =
                    botConversation(button("Claim prize", InlineKeyboardButtonType.Url("https://x.example")))

                press(viewModel)
                assertEquals(BotButtonDialog.OpenLink("https://x.example"), viewModel.dialog)

                viewModel.effects.test {
                    send(viewModel, BotButtonEvent.OpenLink)

                    assertEquals(ChatEffect.OpenUrl("https://x.example"), awaitItem())
                }
                assertNull(viewModel.dialog)
            }

        @Test
        fun `a login asks first, then opens the link Telegram makes`() =
            runViewModelTest {
                botButtons.onLoginUrlInfo = {
                    Outcome.Success(LoginUrlInfo.RequestConfirmation("https://a.example", "a.example", BOT, true))
                }
                val viewModel = botConversation(loginButton())

                press(viewModel)
                assertEquals(
                    BotButtonDialog.Login(
                        domain = "a.example",
                        botName = UiText.Plain("@pollbot"),
                        myName = UiText.Plain("Ada Lovelace"),
                        requestWriteAccess = true,
                    ),
                    viewModel.dialog,
                )

                viewModel.effects.test {
                    send(viewModel, BotButtonEvent.Login(logIn = true, allowWriteAccess = false))

                    assertEquals(ChatEffect.OpenUrl("https://example.org/?auth=1"), awaitItem())
                }
                assertEquals(LOGIN_BUTTON to false, botButtons.loggedIn.single())
                assertNull(viewModel.dialog)
            }

        @Test
        fun `a login declined opens the link as it is`() =
            runViewModelTest {
                botButtons.onLoginUrlInfo = {
                    Outcome.Success(LoginUrlInfo.RequestConfirmation("https://a.example", "a.example", BOT, false))
                }
                val viewModel = botConversation(loginButton())
                press(viewModel)

                viewModel.effects.test {
                    send(viewModel, BotButtonEvent.Login(logIn = false, allowWriteAccess = true))

                    assertEquals(ChatEffect.OpenUrl("https://a.example"), awaitItem())
                }
                assertTrue(botButtons.loggedIn.isEmpty())
            }

        @Test
        fun `a login link that needs no question opens at once`() =
            runViewModelTest {
                botButtons.onLoginUrlInfo = { Outcome.Success(LoginUrlInfo.Open("https://b.example", true)) }
                val viewModel = botConversation(loginButton())

                viewModel.effects.test {
                    press(viewModel)

                    assertEquals(ChatEffect.OpenUrl("https://b.example"), awaitItem())
                }
            }
    }

    @Nested
    inner class SwitchInline {
        @Test
        fun `puts the bot's inline query into this chat's composer`() =
            runViewModelTest {
                val viewModel =
                    botConversation(
                        button("Search", InlineKeyboardButtonType.SwitchInline("cats", SwitchInlineTarget.CurrentChat)),
                    )

                viewModel.effects.test {
                    press(viewModel)

                    assertEquals(ChatEffect.FocusComposer, awaitItem())
                }
                assertEquals("@pollbot cats", viewModel.composerText.content.text)
            }

        @Test
        fun `offers the chats the bot allows, and opens the one picked with the query in its composer`() =
            runViewModelTest {
                val target = SwitchInlineTarget.ChosenChat(users = true, bots = false, groups = true, channels = false)
                val otherBot = chat(id = OTHER_BOT, title = "Other bot")
                val channel = chat(id = CHANNEL, type = ChatType.Supergroup(9, isChannel = true), title = "News")
                chats.mainList.value = listOf(chat(ADA, title = "Ada"), groupChat, otherBot, channel)
                groups.basicGroupMap.value = mapOf(BASIC_GROUP_ID to basicGroup(3))
                val viewModel = botConversation(button("Share", InlineKeyboardButtonType.SwitchInline("q", target)))
                users.setUsers(user(ME), bot, user(ADA), user(OTHER_BOT, type = UserType.Bot))
                runCurrent()

                press(viewModel)
                runCurrent()
                assertEquals(
                    listOf(ADA, GROUP_CHAT),
                    viewModel.switchInlineSheet.value
                        ?.targets
                        ?.map { it.chatId },
                )
                assertEquals("@pollbot q", viewModel.switchInlineSheet.value?.query)

                viewModel.effects.test {
                    send(viewModel, BotButtonEvent.PickChat(ADA))

                    assertEquals(ChatEffect.OpenChat(ADA), awaitItem())
                }
                assertNull(viewModel.switchInlineSheet.value)
                val delivered = CompletableDeferred<String?>()
                backgroundScope.launch {
                    shareInbox.deliveredTo(ADA).collect { delivered.complete(it?.text?.text) }
                }
                runCurrent()
                assertEquals("@pollbot q", delivered.await())
            }
    }

    @Nested
    inner class Others {
        @Test
        fun `copies a text, opens a profile, and says what Pingogram does not offer yet`() =
            runViewModelTest {
                val viewModel =
                    botConversation(
                        button("Code", InlineKeyboardButtonType.CopyText("PROMO")),
                        button("Author", InlineKeyboardButtonType.User(ADA)),
                        button("App", InlineKeyboardButtonType.WebApp("https://app.example")),
                        button("Play", InlineKeyboardButtonType.CallbackGame),
                        button("Pay", InlineKeyboardButtonType.Buy),
                    )

                viewModel.effects.test {
                    (0..4).forEach { press(viewModel, row = it) }

                    assertEquals(ChatEffect.CopyText("PROMO"), awaitItem())
                    assertEquals(ChatEffect.OpenProfile(ADA), awaitItem())
                    assertEquals(BotButtonEffect.Unsupported(UnsupportedButton.MiniApp), awaitItem())
                    assertEquals(BotButtonEffect.Unsupported(UnsupportedButton.Game), awaitItem())
                    assertEquals(BotButtonEffect.Unsupported(UnsupportedButton.Payment), awaitItem())
                }
                assertTrue(botButtons.answered.isEmpty())
            }

        @Test
        fun `a disabled button does nothing`() =
            runViewModelTest {
                val viewModel = botConversation(button("Off", InlineKeyboardButtonType.Disabled))

                viewModel.effects.test {
                    press(viewModel)

                    expectNoEvents()
                }
            }
    }

    private fun button(
        text: String,
        type: InlineKeyboardButtonType,
        style: BotButtonStyle = BotButtonStyle.Default,
    ) = InlineKeyboardButton(text, style, type)

    private fun callback(text: String) = button(text, InlineKeyboardButtonType.Callback(DATA))

    private fun loginButton() =
        button("Log in", InlineKeyboardButtonType.LoginUrl("https://a.example", LOGIN_BUTTON, forwardText = ""))

    private companion object {
        const val BOT = 500L
        const val OTHER_BOT = 501L
        const val CHANNEL = -1_000_000_000_900L
        const val MESSAGE = 10L
        const val LOGIN_BUTTON = 77L
        val DATA = CallbackData(byteArrayOf(1, 2, 3))
    }
}
