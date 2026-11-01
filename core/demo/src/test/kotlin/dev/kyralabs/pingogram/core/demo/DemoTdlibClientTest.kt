package dev.kyralabs.pingogram.core.demo

import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.TdResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DemoTdlibClientTest {
    @TempDir
    lateinit var dir: File

    /** The journal's lines, kept across clients as the file keeps them across process deaths. */
    private val lines = mutableListOf<String>()

    private val written = mutableListOf<DemoFile>()

    private val journal =
        DemoJournal(
            object : DemoJournalStorage {
                override fun read() = lines.toList()

                override fun append(line: String) {
                    lines += line
                }
            },
        )

    private fun TestScope.client(): Pair<DemoTdlibClient, MutableList<TdApi.Update>> {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(backgroundScope.coroutineContext + dispatcher)
        val client =
            DemoTdlibClient(
                text = { "Text $it" },
                journal = journal,
                directory = dir,
                fileWriter = { file, target ->
                    written += file
                    target.parentFile?.mkdirs()
                    target.writeText(file.fileName)
                },
                scope = scope,
                io = dispatcher,
                clock = { NOW },
            )
        val updates = mutableListOf<TdApi.Update>()
        scope.launch { client.updates.collect { updates += it } }
        runCurrent()
        client.start()
        runCurrent()
        return client to updates
    }

    private suspend fun <T : TdApi.Object> DemoTdlibClient.ask(function: TdApi.Function<T>): T =
        (send(function) as TdResult.Success).value

    @Test
    fun `it is signed in at once and announces the user, the people and every chat as TDLib does`() =
        runTest {
            val (client, updates) = client()

            assertEquals(AuthState.Ready, client.authState.value)
            assertInstanceOf(
                TdApi.AuthorizationStateReady::class.java,
                (updates.first() as TdApi.UpdateAuthorizationState).authorizationState,
            )
            assertEquals(DemoData.ME, ((updates[1] as TdApi.UpdateOption).value as TdApi.OptionValueInteger).value)
            val chats = updates.filterIsInstance<TdApi.UpdateNewChat>().map { it.chat.id }
            assertEquals(
                setOf(
                    DemoData.ADA_CHAT,
                    DemoData.BEN_CHAT,
                    DemoData.GROUP_CHAT,
                    DemoData.CHANNEL_CHAT,
                    DemoData.ME,
                    DemoData.DEV_CHAT,
                ),
                chats.toSet(),
            )
            assertEquals(5, updates.filterIsInstance<TdApi.UpdateUser>().size)
            assertEquals(
                listOf(
                    DemoData.WORK_FOLDER,
                ),
                updates.filterIsInstance<TdApi.UpdateChatFolders>().single().chatFolders.map {
                    it.id
                },
            )
        }

    @Test
    fun `the main list holds every chat but the archived one, the newest first`() =
        runTest {
            val (client, _) = client()

            val main = client.ask(TdApi.GetChats(TdApi.ChatListMain(), 100)).chatIds.toList()
            val archive = client.ask(TdApi.GetChats(TdApi.ChatListArchive(), 100)).chatIds.toList()
            val work = client.ask(TdApi.GetChats(TdApi.ChatListFolder(DemoData.WORK_FOLDER), 100)).chatIds.toList()

            assertEquals(DemoData.ADA_CHAT, main.first())
            assertTrue(DemoData.DEV_CHAT !in main)
            assertEquals(listOf(DemoData.DEV_CHAT), archive)
            assertEquals(setOf(DemoData.BEN_CHAT, DemoData.GROUP_CHAT), work.toSet())
            // Everything was announced at the start, so there is nothing more to load.
            assertEquals(404, (client.send(TdApi.LoadChats(TdApi.ChatListMain(), 20)) as TdResult.Failure).error.code)
        }

    @Test
    fun `history pages newest first, as getChatHistory does`() =
        runTest {
            val (client, _) = client()

            val newest = client.ask(TdApi.GetChatHistory(DemoData.ADA_CHAT, 0, 0, 2, false)).messages.toList()
            val older =
                client
                    .ask(
                        TdApi.GetChatHistory(DemoData.ADA_CHAT, newest.last().id, 1, 10, false),
                    ).messages
                    .toList()

            assertEquals(2, newest.size)
            assertTrue(newest[0].id > newest[1].id)
            assertEquals(4, older.size)
            assertTrue(older.all { it.id < newest.last().id })
            assertInstanceOf(TdApi.MessagePhoto::class.java, older.first().content)
        }

    @Test
    fun `a sent text appears in its chat, and survives a restart from the journal`() =
        runTest {
            val (client, updates) = client()

            val sent = client.ask(TdApi.SendMessage(DemoData.BEN_CHAT, null, null, null, null, text("On my way")))
            runCurrent()

            assertTrue(sent.isOutgoing)
            assertEquals(
                sent.id,
                updates
                    .filterIsInstance<TdApi.UpdateNewMessage>()
                    .single()
                    .message.id,
            )
            assertEquals(
                sent.id,
                updates
                    .filterIsInstance<TdApi.UpdateChatLastMessage>()
                    .single()
                    .lastMessage
                    ?.id,
            )
            val (restarted, _) = client()
            val history = restarted.ask(TdApi.GetChatHistory(DemoData.BEN_CHAT, 0, 0, 1, false)).messages
            assertEquals("On my way", (history.single().content as TdApi.MessageText).text.text)
        }

    @Test
    fun `reading a chat clears its unread count`() =
        runTest {
            val (client, updates) = client()
            val newest = client.ask(TdApi.GetChatHistory(DemoData.ADA_CHAT, 0, 0, 1, false)).messages.single()

            client.ask(TdApi.ViewMessages(DemoData.ADA_CHAT, longArrayOf(newest.id), null, true))
            runCurrent()

            val read = updates.filterIsInstance<TdApi.UpdateChatReadInbox>().single()
            assertEquals(0 to newest.id, read.unreadCount to read.lastReadInboxMessageId)
            assertEquals(0, client.ask(TdApi.GetChat(DemoData.ADA_CHAT)).unreadCount)
        }

    @Test
    fun `reactions and votes change the message and come back after a restart`() =
        runTest {
            val (client, _) = client()
            val tip = client.ask(TdApi.GetChatHistory(DemoData.CHANNEL_CHAT, 0, 0, 1, false)).messages.single()
            val poll =
                client.ask(TdApi.GetChatHistory(DemoData.GROUP_CHAT, 0, 0, 10, false)).messages.first {
                    it.content is TdApi.MessagePoll
                }

            client.ask(
                TdApi.AddMessageReaction(DemoData.CHANNEL_CHAT, tip.id, TdApi.ReactionTypeEmoji("🔥"), false, false),
            )
            client.ask(TdApi.SetPollAnswer(DemoData.GROUP_CHAT, poll.id, intArrayOf(2)))
            val (restarted, _) = client()

            val message = restarted.ask(TdApi.GetMessage(DemoData.CHANNEL_CHAT, tip.id))
            val reactions = checkNotNull(message.interactionInfo?.reactions).reactions
            assertTrue(reactions.any { (it.type as TdApi.ReactionTypeEmoji).emoji == "🔥" && it.isChosen })
            val options =
                (
                    restarted
                        .ask(
                            TdApi.GetMessage(DemoData.GROUP_CHAT, poll.id),
                        ).content as TdApi.MessagePoll
                ).poll.options
            assertTrue(options[2].isChosen)
        }

    @Test
    fun `archiving moves a chat between the lists`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.AddChatToList(DemoData.ADA_CHAT, TdApi.ChatListArchive()))

            assertTrue(DemoData.ADA_CHAT in client.ask(TdApi.GetChats(TdApi.ChatListArchive(), 10)).chatIds)
            assertTrue(DemoData.ADA_CHAT !in client.ask(TdApi.GetChats(TdApi.ChatListMain(), 10)).chatIds)
        }

    @Test
    fun `files are made on the phone the first time they are downloaded`() =
        runTest {
            val (client, updates) = client()

            val before = client.ask(TdApi.GetFile(DemoFile.Photo.id))
            val downloaded = client.ask(TdApi.DownloadFile(DemoFile.Photo.id, 32, 0, 0, false))
            runCurrent()

            assertTrue(!before.local.isDownloadingCompleted && downloaded.local.isDownloadingCompleted)
            assertEquals(dir.resolve(DemoFile.Photo.fileName).path, downloaded.local.path)
            assertEquals(listOf(DemoFile.Photo), written)
            assertEquals(
                downloaded.local.path,
                updates
                    .filterIsInstance<TdApi.UpdateFile>()
                    .single()
                    .file.local.path,
            )
        }

    @Test
    fun `users, their profiles and the group's members answer`() =
        runTest {
            val (client, _) = client()

            assertEquals(DemoData.ME, client.ask(TdApi.GetMe()).id)
            assertEquals("Text ${R.string.demo_ada_bio}", client.ask(TdApi.GetUserFullInfo(DemoData.ADA)).bio?.text)
            assertEquals(4, client.ask(TdApi.GetContacts()).totalCount)
            assertEquals(
                DemoData.GROUP_MEMBERS.size,
                client.ask(TdApi.GetBasicGroupFullInfo(DemoData.GROUP)).members.size,
            )
            assertEquals(
                1,
                client
                    .ask(
                        TdApi.SearchChatMessages(
                            DemoData.GROUP_CHAT,
                            null,
                            "",
                            null,
                            0,
                            0,
                            10,
                            TdApi.SearchMessagesFilterPinned(),
                        ),
                    ).messages.size,
            )
        }

    @Test
    fun `what the demo cannot do fails as not available in the demo`() =
        runTest {
            val (client, _) = client()

            listOf(
                TdApi.CreateCall().apply { userId = DemoData.ADA },
                TdApi.JoinChatByInviteLink().apply { inviteLink = "https://t.me/+abc" },
                TdApi.SetAuthenticationPhoneNumber().apply { phoneNumber = "+447911123456" },
                TdApi.GetActiveSessions(),
                TdApi.SendMessage(DemoData.ADA_CHAT, null, null, null, null, TdApi.InputMessageContact()),
                TdApi.SendMessage(DemoData.CHANNEL_CHAT, null, null, null, null, text("Hello")),
            ).forEach { function ->
                val failure = client.send(function) as TdResult.Failure
                assertEquals(DemoTdlibClient.NOT_AVAILABLE, failure.error.message, function.javaClass.simpleName)
            }
        }

    private fun text(text: String) = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, false)

    private companion object {
        const val NOW = 1_800_000_000
    }
}
