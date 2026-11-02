package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DemoGroupsTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    @Test
    fun `the user owns the channel, so posts go out as the channel`() =
        runTest {
            val (client, _) = client()

            val post = client.ask(TdApi.SendMessage(DemoData.CHANNEL_CHAT, null, null, null, null, plainText("News")))

            assertTrue(post.isChannelPost && post.isOutgoing)
            assertEquals(DemoData.CHANNEL_CHAT, (post.senderId as TdApi.MessageSenderChat).chatId)
        }

    @Test
    fun `members are added and promoted, and the channel records it in its recent actions`() =
        runTest {
            val (client, _) = client()
            val dev = TdApi.MessageSenderUser(DemoData.DEV)

            client.ask(TdApi.AddChatMembers(DemoData.CHANNEL_CHAT, longArrayOf(DemoData.DEV)))
            client.ask(
                TdApi.SetChatMemberStatus(
                    DemoData.CHANNEL_CHAT,
                    dev,
                    TdApi.ChatMemberStatusAdministrator(true, TdApi.ChatAdministratorRights()),
                ),
            )
            val (restarted, _) = client()

            val admins =
                restarted.ask(
                    TdApi.GetSupergroupMembers(DemoData.CHANNEL, TdApi.SupergroupMembersFilterAdministrators(), 0, 10),
                )
            assertTrue(admins.members.any { (it.memberId as TdApi.MessageSenderUser).userId == DemoData.DEV })
            val actions =
                restarted
                    .ask(
                        TdApi.GetChatEventLog(DemoData.CHANNEL_CHAT, "", 0, 10, null, longArrayOf()),
                    ).events
            assertInstanceOf(TdApi.ChatEventMemberPromoted::class.java, actions.first().action)
        }

    @Test
    fun `invite links are created, and revoking the primary one makes a new one`() =
        runTest {
            val (client, _) = client()
            val primary =
                client
                    .ask(
                        TdApi.GetChatInviteLinks(DemoData.GROUP_CHAT, DemoData.ME, false, 0, "", 10),
                    ).inviteLinks
                    .single()

            client.ask(TdApi.CreateChatInviteLink(DemoData.GROUP_CHAT, "Friends", 0, 10, false))
            val revoked = client.ask(TdApi.RevokeChatInviteLink(DemoData.GROUP_CHAT, primary.inviteLink)).inviteLinks

            val active =
                client
                    .ask(
                        TdApi.GetChatInviteLinks(DemoData.GROUP_CHAT, DemoData.ME, false, 0, "", 10),
                    ).inviteLinks
            assertTrue(revoked.first().isRevoked && revoked.last().isPrimary)
            assertEquals(setOf("Friends", ""), active.map { it.name }.toSet())
            assertTrue(active.none { it.inviteLink == primary.inviteLink })
        }

    @Test
    fun `an approved join request makes a member and leaves the count`() =
        runTest {
            val (client, updates) = client()

            client.ask(TdApi.ProcessChatJoinRequest(DemoData.CHANNEL_CHAT, DemoData.FINN, true))

            val pending =
                client
                    .ask(
                        TdApi.GetChatJoinRequests(DemoData.CHANNEL_CHAT, "", "", null, 10),
                    ).requests
                    .map { it.userId }
            assertEquals(listOf(DemoData.DEV), pending)
            assertEquals(
                1,
                updates
                    .filterIsInstance<TdApi.UpdateChatPendingJoinRequests>()
                    .last()
                    .pendingJoinRequests
                    ?.totalCount,
            )
            val finn = client.ask(TdApi.GetChatMember(DemoData.CHANNEL_CHAT, TdApi.MessageSenderUser(DemoData.FINN)))
            assertInstanceOf(TdApi.ChatMemberStatusMember::class.java, finn.status)
        }

    @Test
    fun `new groups and channels open with their service message, and survive a restart`() =
        runTest {
            val (client, _) = client()

            val group = client.ask(TdApi.CreateNewBasicGroupChat(longArrayOf(DemoData.ADA), "Book club", 0))
            val channel = client.ask(TdApi.CreateNewSupergroupChat("Daily", false, true, "", null, 0, false))
            val (restarted, _) = client()

            val main = restarted.ask(TdApi.GetChats(TdApi.ChatListMain(), 20)).chatIds.toList()
            assertTrue(group.chatId in main && channel.id in main)
            val first = restarted.ask(TdApi.GetChatHistory(group.chatId, 0, 0, 1, false)).messages.single()
            assertInstanceOf(TdApi.MessageBasicGroupChatCreate::class.java, first.content)
        }

    @Test
    fun `upgrading the group makes a supergroup with its messages, and the basic group leaves the list`() =
        runTest {
            val (client, _) = client()
            val messages = client.ask(TdApi.GetChatHistory(DemoData.GROUP_CHAT, 0, 0, 50, false)).messages.size

            val supergroup = client.ask(TdApi.UpgradeBasicGroupChatToSupergroupChat(DemoData.GROUP_CHAT))

            val main = client.ask(TdApi.GetChats(TdApi.ChatListMain(), 20)).chatIds.toList()
            assertTrue(supergroup.id in main && DemoData.GROUP_CHAT !in main)
            assertEquals(messages, client.ask(TdApi.GetChatHistory(supergroup.id, 0, 0, 50, false)).messages.size)
        }

    @Test
    fun `the channel's statistics come in Telegram's chart format, and deleting a chat takes it off the list`() =
        runTest {
            val (client, _) = client()

            val statistics =
                client.ask(
                    TdApi.GetChatStatistics(DemoData.CHANNEL_CHAT, false),
                ) as TdApi.ChatStatisticsChannel
            client.ask(TdApi.DeleteChat(DemoData.BEN_CHAT))

            assertTrue(
                (statistics.memberCountGraph as TdApi.StatisticalGraphData).jsonData.startsWith(
                    "{\"columns\":[[\"x\",",
                ),
            )
            assertTrue(DemoData.BEN_CHAT !in client.ask(TdApi.GetChats(TdApi.ChatListMain(), 20)).chatIds)
        }
}
