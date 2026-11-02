package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.TdResult
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

class DemoSettingsTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    @Test
    fun `a 2-step password with a recovery email is set, kept, and lets the user hand the channel over`() =
        runTest {
            val (client, _) = client()

            assertInstanceOf(
                TdApi.CanTransferOwnershipResultPasswordNeeded::class.java,
                client.ask(TdApi.CanTransferOwnership()),
            )
            client.ask(TdApi.SetPassword("", "secret", "a word", true, "sam@example.org"))
            client.ask(TdApi.CheckRecoveryEmailAddressCode("12345"))
            val (restarted, _) = client()

            val state = restarted.ask(TdApi.GetPasswordState())
            assertTrue(state.hasPassword && state.hasRecoveryEmailAddress)
            assertEquals("sam@example.org", restarted.ask(TdApi.GetRecoveryEmailAddress("secret")).recoveryEmailAddress)
            assertInstanceOf(
                TdApi.CanTransferOwnershipResultOk::class.java,
                restarted.ask(TdApi.CanTransferOwnership()),
            )
            restarted.ask(TdApi.TransferChatOwnership(DemoData.CHANNEL_CHAT, DemoData.ADA, "secret"))
            val ada = restarted.ask(TdApi.GetChatMember(DemoData.CHANNEL_CHAT, TdApi.MessageSenderUser(DemoData.ADA)))
            assertInstanceOf(TdApi.ChatMemberStatusCreator::class.java, ada.status)
        }

    @Test
    fun `the phone number changes with any code of the length asked, but not to a sample person's number`() =
        runTest {
            val (client, _) = client()
            val ada = client.ask(TdApi.GetUser(DemoData.ADA))

            val taken = client.send(TdApi.SendPhoneNumberCode(ada.phoneNumber, null, null)) as TdResult.Failure
            client.ask(TdApi.SendPhoneNumberCode("+44 7700 900123", null, null))
            client.ask(TdApi.CheckPhoneNumberCode("54321"))

            assertEquals("PHONE_NUMBER_OCCUPIED", taken.error.message)
            assertEquals("447700900123", client.ask(TdApi.GetMe()).phoneNumber)
        }

    @Test
    fun `notification exceptions are the muted chats, and a kind's settings are kept`() =
        runTest {
            val (client, _) = client()
            val muted = DemoNotifications.chatSettings(muteFor = 3_600)

            client.ask(TdApi.SetChatNotificationSettings(DemoData.BEN_CHAT, muted))
            val settings = DemoNotifications.scopeSettings().apply { showPreview = false }
            client.ask(TdApi.SetScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats(), settings))

            val exceptions =
                client.ask(
                    TdApi.GetChatNotificationSettingsExceptions(TdApi.NotificationSettingsScopePrivateChats(), false),
                )
            assertEquals(listOf(DemoData.BEN_CHAT), exceptions.chatIds.toList())
            assertEquals(
                false,
                client.ask(TdApi.GetScopeNotificationSettings(TdApi.NotificationSettingsScopeGroupChats())).showPreview,
            )
        }

    @Test
    fun `proxies are kept and switched, never connected through`() =
        runTest {
            val (client, _) = client()
            val proxy = TdApi.Proxy("203.0.113.5", 1080, TdApi.ProxyTypeSocks5("", ""))

            val first = client.ask(TdApi.AddProxy(proxy, true, "Home"))
            val second = client.ask(TdApi.AddProxy(proxy, false, "Work"))
            client.ask(TdApi.EnableProxy(second.id))

            val proxies = client.ask(TdApi.GetProxies()).proxies
            assertEquals(listOf(first.id to false, second.id to true), proxies.map { it.id to it.isEnabled })
            assertTrue(client.ask(TdApi.PingProxy(proxy)).seconds > 0)
        }

    @Test
    fun `a new folder holds the chats its rules pick, and leaves with them when deleted`() =
        runTest {
            val (client, _) = client()
            val groups =
                TdApi.ChatFolder().apply {
                    name = TdApi.ChatFolderName(TdApi.FormattedText("Groups", emptyArray()), false)
                    icon = TdApi.ChatFolderIcon("Groups")
                    colorId = -1
                    pinnedChatIds = longArrayOf()
                    includedChatIds = longArrayOf()
                    excludedChatIds = longArrayOf()
                    includeGroups = true
                }

            val created = client.ask(TdApi.CreateChatFolder(groups))
            val inFolder = client.ask(TdApi.GetChats(TdApi.ChatListFolder(created.id), 20)).chatIds.toList()
            client.ask(TdApi.DeleteChatFolder(created.id, longArrayOf()))

            assertEquals(setOf(DemoData.GROUP_CHAT, DemoData.FORUM_CHAT, DemoData.DISCUSSION_CHAT), inFolder.toSet())
            assertTrue(client.ask(TdApi.GetChats(TdApi.ChatListFolder(created.id), 20)).chatIds.isEmpty())
        }

    @Test
    fun `marking the main list read clears its unread chats, and the archive's settings are kept`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.ReadChatList(TdApi.ChatListMain()))
            client.ask(TdApi.SetArchiveChatListSettings(TdApi.ArchiveChatListSettings(false, true, true)))

            val chats =
                client
                    .ask(
                        TdApi.GetChats(TdApi.ChatListMain(), 20),
                    ).chatIds
                    .map { client.ask(TdApi.GetChat(it)) }
            assertTrue(chats.all { it.unreadCount == 0 })
            assertTrue(client.ask(TdApi.GetArchiveChatListSettings()).keepUnmutedChatsArchived)
        }

    @Test
    fun `after a restart the folders and groups announced are those the user left`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.DeleteChatFolder(DemoData.WORK_FOLDER, longArrayOf()))
            val made = client.ask(TdApi.CreateNewSupergroupChat("Sample", false, false, "", null, 0, false))
            val (_, updates) = client()

            val folders = updates.filterIsInstance<TdApi.UpdateChatFolders>().last().chatFolders
            assertTrue(folders.isEmpty())
            val supergroupId = (made.type as TdApi.ChatTypeSupergroup).supergroupId
            assertTrue(updates.filterIsInstance<TdApi.UpdateSupergroup>().any { it.supergroup.id == supergroupId })
        }
}
