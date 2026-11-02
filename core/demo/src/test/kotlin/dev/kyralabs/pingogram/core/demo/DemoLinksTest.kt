package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DemoLinksTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    @Test
    fun `links are told apart as TDLib tells them`() {
        val parse = DemoLinkParser::parse

        assertInstanceOf(TdApi.InternalLinkTypeChatInvite::class.java, parse("https://t.me/+DemoPhotoWalks"))
        assertInstanceOf(TdApi.InternalLinkTypeChatInvite::class.java, parse("t.me/joinchat/abc"))
        assertInstanceOf(TdApi.InternalLinkTypeUserPhoneNumber::class.java, parse("https://t.me/+447700900001"))
        assertInstanceOf(TdApi.InternalLinkTypeChatFolderInvite::class.java, parse("https://t.me/addlist/DemoHobbies"))
        assertInstanceOf(TdApi.InternalLinkTypeMessage::class.java, parse("https://t.me/sample_news/3"))
        assertInstanceOf(TdApi.InternalLinkTypeMessage::class.java, parse("https://t.me/c/300001/2"))
        assertInstanceOf(TdApi.InternalLinkTypeSavedMessages::class.java, parse("tg://settings/saved-messages"))
        assertInstanceOf(TdApi.InternalLinkTypeNewPrivateChat::class.java, parse("tg://new"))
        assertInstanceOf(TdApi.InternalLinkTypeSearch::class.java, parse("tg://chats/search"))
        assertInstanceOf(TdApi.InternalLinkTypeQrCodeAuthentication::class.java, parse("tg://login?token=abc"))
        assertInstanceOf(TdApi.InternalLinkTypeUnknownDeepLink::class.java, parse("tg://nothing"))
        assertNull(parse("https://example.org/page"))

        val folders = parse("tg://settings/folders") as TdApi.InternalLinkTypeSettings
        assertInstanceOf(TdApi.SettingsSectionChatFolders::class.java, folders.section)
        val bot = parse("https://t.me/sample_login_bot?start=hello") as TdApi.InternalLinkTypeBotStart
        assertEquals("sample_login_bot" to "hello", bot.botUsername to bot.startParameter)
        val proxy = parse("tg://proxy?server=203.0.113.5&port=443&secret=ab") as TdApi.InternalLinkTypeProxy
        assertEquals("203.0.113.5" to 443, proxy.proxy?.server to proxy.proxy?.port)
        val draft =
            parse(
                "https://t.me/share/url?url=https%3A%2F%2Fexample.org&text=Look",
            ) as TdApi.InternalLinkTypeMessageDraft
        assertEquals("https://example.org\nLook", draft.text.text)
    }

    @Test
    fun `the photo walks link opens a join sheet, joining adds the group, and leaving keeps its history`() =
        runTest {
            val (client, _) = client()

            val before = client.ask(TdApi.CheckChatInviteLink(DemoData.PHOTO_WALKS_LINK))
            client.ask(TdApi.JoinChatByInviteLink(DemoData.PHOTO_WALKS_LINK))
            val main = client.ask(TdApi.GetChats(TdApi.ChatListMain(), 50)).chatIds.toList()
            val after = client.ask(TdApi.CheckChatInviteLink("t.me/+DemoPhotoWalks"))
            val history = client.ask(TdApi.GetChatHistory(DemoData.PHOTO_WALKS_CHAT, 0, 0, 10, false)).messages

            assertEquals(0L to DemoData.PHOTO_WALKS_MEMBERS.size, before.chatId to before.memberCount)
            assertTrue(DemoData.PHOTO_WALKS_CHAT in main)
            assertEquals(DemoData.PHOTO_WALKS_CHAT, after.chatId)
            assertInstanceOf(TdApi.MessageChatJoinByLink::class.java, history.first().content)

            client.ask(TdApi.LeaveChat(DemoData.PHOTO_WALKS_CHAT))
            val (restarted, _) = client()
            assertFalse(
                DemoData.PHOTO_WALKS_CHAT in restarted.ask(TdApi.GetChats(TdApi.ChatListMain(), 50)).chatIds.toList(),
            )
            assertEquals(2, restarted.ask(TdApi.GetChatHistory(DemoData.PHOTO_WALKS_CHAT, 0, 0, 10, false)).totalCount)
        }

    @Test
    fun `the book swap link asks the admins first`() =
        runTest {
            val (client, _) = client()

            val info = client.ask(TdApi.CheckChatInviteLink(DemoData.BOOK_SWAP_LINK))
            val join = client.send(TdApi.JoinChatByInviteLink(DemoData.BOOK_SWAP_LINK)) as TdResult.Failure

            assertTrue(info.createsJoinRequest)
            assertEquals("INVITE_REQUEST_SENT", join.error.message)
        }

    @Test
    fun `the public channel is found by its username, joined, and its posts have public links`() =
        runTest {
            val (client, _) = client()

            val chat = client.ask(TdApi.SearchPublicChat("sample_news"))
            client.ask(TdApi.JoinChat(chat.id))
            val post = client.ask(TdApi.GetChatHistory(chat.id, 0, 0, 10, false)).messages.single()
            val link = client.ask(TdApi.GetMessageLink(chat.id, post.id, 0, 0, "", false, false))
            val info = client.ask(TdApi.GetMessageLinkInfo(link.link))

            assertEquals(DemoData.NEWS_CHAT, chat.id)
            assertTrue(link.isPublic)
            assertEquals(post.id, info.message?.id)
            assertTrue(DemoData.NEWS_CHAT in client.ask(TdApi.GetChats(TdApi.ChatListMain(), 50)).chatIds.toList())
        }

    @Test
    fun `Ben's shared folder joins the chats picked and holds all three`() =
        runTest {
            val (client, _) = client()

            val info = client.ask(TdApi.CheckChatFolderInviteLink(DemoData.HOBBIES_LINK))
            client.ask(TdApi.AddChatFolderByInviteLink(DemoData.HOBBIES_LINK, info.missingChatIds))
            val again = client.ask(TdApi.CheckChatFolderInviteLink(DemoData.HOBBIES_LINK))

            assertEquals(setOf(DemoData.PHOTO_WALKS_CHAT, DemoData.NEWS_CHAT), info.missingChatIds.toSet())
            assertEquals(listOf(DemoData.FORUM_CHAT), info.addedChatIds.toList())
            assertTrue(again.chatFolderInfo.id > 0)
            val inFolder = client.ask(TdApi.GetChats(TdApi.ChatListFolder(again.chatFolderInfo.id), 20)).chatIds.toSet()
            assertEquals(setOf(DemoData.FORUM_CHAT, DemoData.PHOTO_WALKS_CHAT, DemoData.NEWS_CHAT), inFolder)
        }

    @Test
    fun `a private chat with someone new is made, and joins the list with its first message`() =
        runTest {
            val (client, _) = client()

            val chat = client.ask(TdApi.CreatePrivateChat(DemoData.CHLOE, false))
            val listedBefore = DemoData.CHLOE in client.ask(TdApi.GetChats(TdApi.ChatListMain(), 50)).chatIds.toList()
            client.ask(TdApi.SendMessage(chat.id, null, null, null, null, plainText("Hi Chloe")))
            val (restarted, _) = client()

            assertFalse(listedBefore)
            assertTrue(DemoData.CHLOE in restarted.ask(TdApi.GetChats(TdApi.ChatListMain(), 50)).chatIds.toList())
            assertEquals(1, restarted.ask(TdApi.GetChatHistory(DemoData.CHLOE, 0, 0, 10, false)).totalCount)
        }

    @Test
    fun `a report asks for a reason, then a comment for something else, and goes nowhere`() =
        runTest {
            val (client, _) = client()

            val reasons =
                client.ask(
                    TdApi.ReportChat(DemoData.BEN_CHAT, null, longArrayOf(), ""),
                ) as TdApi.ReportChatResultOptionRequired
            val other = reasons.options.last().id
            val comment = client.ask(TdApi.ReportChat(DemoData.BEN_CHAT, other, longArrayOf(), ""))
            val done = client.ask(TdApi.ReportChat(DemoData.BEN_CHAT, other, longArrayOf(), "Sample report"))

            assertEquals(3, reasons.options.size)
            assertInstanceOf(TdApi.ReportChatResultTextRequired::class.java, comment)
            assertInstanceOf(TdApi.ReportChatResultOk::class.java, done)
        }

    @Test
    fun `unlinking the discussion group stops forwarding new posts to it`() =
        runTest {
            val (client, _) = client()
            val before = client.ask(TdApi.GetChatHistory(DemoData.DISCUSSION_CHAT, 0, 0, 50, false)).totalCount

            client.ask(TdApi.SetChatDiscussionGroup(DemoData.CHANNEL_CHAT, 0))
            client.ask(TdApi.SendMessage(DemoData.CHANNEL_CHAT, null, null, null, null, plainText("News")))

            assertEquals(0L, client.ask(TdApi.GetSupergroupFullInfo(DemoData.CHANNEL)).linkedChatId)
            assertEquals(before, client.ask(TdApi.GetChatHistory(DemoData.DISCUSSION_CHAT, 0, 0, 50, false)).totalCount)
            val suitable = client.ask(TdApi.GetSuitableDiscussionChats()).chatIds.toList()
            assertTrue(DemoData.DISCUSSION_CHAT in suitable)
        }
}
