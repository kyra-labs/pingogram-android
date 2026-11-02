package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DemoBotsTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    private suspend fun DemoTdlibClient.botMessages() =
        ask(TdApi.GetChatHistory(DemoData.LOGIN_BOT, 0, 0, 20, false)).messages.toList()

    @Test
    fun `the bot is found by its mention, tells what it does, and starts with its buttons and keyboard`() =
        runTest {
            val (client, updates) = client()

            val chat = client.ask(TdApi.SearchPublicChat("sample_login_bot"))
            val info = client.ask(TdApi.GetUserFullInfo(DemoData.LOGIN_BOT)).botInfo
            client.ask(TdApi.SendBotStartMessage(DemoData.LOGIN_BOT, chat.id, ""))
            runCurrent()

            assertEquals(listOf("start", "help", "keyboard"), info?.commands?.map { it.command })
            val (keyboard, buttons, start) = client.botMessages()
            assertEquals("/start", (start.content as TdApi.MessageText).text.text)
            assertInstanceOf(TdApi.ReplyMarkupInlineKeyboard::class.java, buttons.replyMarkup)
            assertInstanceOf(TdApi.ReplyMarkupShowKeyboard::class.java, keyboard.replyMarkup)
            val markup = updates.filterIsInstance<TdApi.UpdateChatReplyMarkup>().last()
            assertEquals(keyboard.id, markup.replyMarkupMessage?.id)
        }

    @Test
    fun `the bot answers its buttons, the login button, and what is shared with it`() =
        runTest {
            val (client, _) = client()
            client.ask(TdApi.SendBotStartMessage(DemoData.LOGIN_BOT, DemoData.LOGIN_BOT, ""))
            val buttons = client.botMessages()[1]

            val hello =
                client.ask(
                    TdApi.GetCallbackQueryAnswer(
                        DemoData.LOGIN_BOT,
                        buttons.id,
                        TdApi.CallbackQueryPayloadData("hello".encodeToByteArray()),
                    ),
                )
            val alert =
                client.ask(
                    TdApi.GetCallbackQueryAnswer(
                        DemoData.LOGIN_BOT,
                        buttons.id,
                        TdApi.CallbackQueryPayloadData("alert".encodeToByteArray()),
                    ),
                )
            val login = client.ask(TdApi.GetLoginUrlInfo(DemoData.LOGIN_BOT, buttons.id, 1))
            val source = TdApi.KeyboardButtonSourceMessage(DemoData.LOGIN_BOT, buttons.id)
            client.ask(TdApi.ShareUsersWithBot(source, 1, longArrayOf(DemoData.ADA), false))

            assertTrue(hello.text.isNotEmpty() && !hello.showAlert)
            assertTrue(alert.showAlert)
            assertEquals("example.org", (login as TdApi.LoginUrlInfoRequestConfirmation).domain)
            val thanks = client.botMessages().first().content as TdApi.MessageText
            assertTrue("Text" in thanks.text.text)
        }

    @Test
    fun `commands and texts get their answers, which a restart keeps`() =
        runTest {
            val (client, _) = client()
            client.ask(TdApi.CreatePrivateChat(DemoData.LOGIN_BOT, false))

            client.ask(TdApi.SendMessage(DemoData.LOGIN_BOT, null, null, null, null, plainText("/help")))
            client.ask(TdApi.SendMessage(DemoData.LOGIN_BOT, null, null, null, null, plainText("Hi")))
            val (restarted, _) = client()

            val messages = restarted.botMessages()
            assertEquals(4, messages.size)
            assertEquals(
                listOf(DemoData.LOGIN_BOT, DemoData.ME),
                messages.take(2).map {
                    (it.senderId as TdApi.MessageSenderUser).userId
                },
            )
        }

    @Test
    fun `inline results are found by title and sent via the bot`() =
        runTest {
            val (client, _) = client()

            val all = client.ask(TdApi.GetInlineQueryResults(DemoData.LOGIN_BOT, DemoData.ADA_CHAT, null, "", ""))
            val found =
                client.ask(
                    TdApi.GetInlineQueryResults(
                        DemoData.LOGIN_BOT,
                        DemoData.ADA_CHAT,
                        null,
                        (all.results[1] as TdApi.InlineQueryResultArticle).title,
                        "",
                    ),
                )
            val result = found.results.single() as TdApi.InlineQueryResultArticle
            val sent =
                client.ask(
                    TdApi.SendInlineQueryResultMessage(
                        DemoData.ADA_CHAT,
                        null,
                        null,
                        null,
                        found.inlineQueryId,
                        result.id,
                        false,
                    ),
                )

            assertEquals(3, all.results.size)
            assertEquals(DemoData.LOGIN_BOT, sent.viaBotUserId)
            assertTrue((sent.content as TdApi.MessageText).text.text.startsWith(result.title))
        }
}
