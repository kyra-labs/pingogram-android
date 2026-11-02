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

    /** Where picked files come from: the app's upload cache, outside the demo's folder. */
    @TempDir
    lateinit var outside: File

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
                    DemoData.FORUM_CHAT,
                    DemoData.DISCUSSION_CHAT,
                    DemoData.ME,
                    DemoData.DEV_CHAT,
                ),
                chats.toSet(),
            )
            assertEquals(7, updates.filterIsInstance<TdApi.UpdateUser>().size)
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
                TdApi.BoostChat().apply { chatId = DemoData.CHANNEL_CHAT },
            ).forEach { function ->
                val failure = client.send(function) as TdResult.Failure
                assertEquals(DemoTdlibClient.NOT_AVAILABLE, failure.error.message, function.javaClass.simpleName)
            }
        }

    @Test
    fun `a sent photo is copied into the demo's folder, shown, and kept after the original is gone`() =
        runTest {
            val (client, _) = client()
            val picked = File(outside, "IMG_1.jpg").apply { writeBytes(ByteArray(64) { it.toByte() }) }

            val sent =
                client.ask(
                    TdApi.SendMessage(
                        DemoData.BEN_CHAT,
                        null,
                        null,
                        null,
                        null,
                        TdApi.InputMessagePhoto(
                            TdApi.InputPhoto(TdApi.InputFileLocal(picked.path), null, null, intArrayOf(), 640, 480),
                            TdApi.FormattedText("Look", emptyArray()),
                            false,
                            null,
                            false,
                        ),
                    ),
                )
            picked.delete()
            val (restarted, _) = client()

            val photo = (restarted.ask(TdApi.GetMessage(DemoData.BEN_CHAT, sent.id)).content as TdApi.MessagePhoto)
            val file =
                photo.photo.sizes
                    .single()
                    .photo
            assertTrue(file.local.path.startsWith(dir.path) && File(file.local.path).length() == 64L)
            assertEquals("Look", photo.caption.text)
            assertEquals(file.local.path, restarted.ask(TdApi.DownloadFile(file.id, 32, 0, 0, false)).local.path)
        }

    @Test
    fun `a voice message, a location and a poll go out as their kinds`() =
        runTest {
            val (client, _) = client()
            val voice = File(outside, "voice.ogg").apply { writeBytes(ByteArray(8)) }

            val kinds =
                listOf(
                    TdApi.InputMessageVoiceNote(
                        TdApi.InputVoiceNote(TdApi.InputFileLocal(voice.path), 3, byteArrayOf(5)),
                        null,
                        null,
                    ),
                    TdApi.InputMessageLocation(TdApi.Location(51.5, -0.12, 10.0)),
                    TdApi.InputMessagePoll().apply {
                        question = TdApi.FormattedText("Lunch?", emptyArray())
                        options = arrayOf(TdApi.InputPollOption(TdApi.FormattedText("Yes", emptyArray()), null))
                        isAnonymous = true
                        type = TdApi.InputPollTypeRegular()
                    },
                ).map { client.ask(TdApi.SendMessage(DemoData.GROUP_CHAT, null, null, null, null, it)).content }

            assertInstanceOf(TdApi.MessageVoiceNote::class.java, kinds[0])
            assertInstanceOf(TdApi.MessageLocation::class.java, kinds[1])
            assertEquals(
                "Yes",
                (kinds[2] as TdApi.MessagePoll)
                    .poll.options
                    .single()
                    .text.text,
            )
        }

    @Test
    fun `forwarding copies a message into another demo chat with where it came from`() =
        runTest {
            val (client, _) = client()
            val post = client.ask(TdApi.GetChatHistory(DemoData.CHANNEL_CHAT, 0, 0, 1, false)).messages.single()

            val forwarded =
                client.ask(
                    TdApi.ForwardMessages(
                        DemoData.ME,
                        null,
                        DemoData.CHANNEL_CHAT,
                        longArrayOf(post.id),
                        null,
                        false,
                        false,
                    ),
                )

            val origin = checkNotNull(forwarded.messages.single().forwardInfo).origin as TdApi.MessageOriginChannel
            assertEquals(DemoData.CHANNEL_CHAT to post.id, origin.chatId to origin.messageId)
            assertEquals(forwarded.messages.single().id, client.ask(TdApi.GetChat(DemoData.ME)).lastMessage?.id)
        }

    @Test
    fun `the user's own message can be edited and pinned, and others' cannot be edited`() =
        runTest {
            val (client, updates) = client()
            val own = client.ask(TdApi.SendMessage(DemoData.ADA_CHAT, null, null, null, null, text("Draft")))
            val theirs = client.ask(TdApi.GetChatHistory(DemoData.ADA_CHAT, own.id, 1, 1, false)).messages.single()

            client.ask(TdApi.EditMessageText(DemoData.ADA_CHAT, own.id, null, text("Final")))
            client.ask(TdApi.PinChatMessage(DemoData.ADA_CHAT, own.id, false, false))
            runCurrent()

            val edited = client.ask(TdApi.GetMessage(DemoData.ADA_CHAT, own.id))
            assertEquals("Final", (edited.content as TdApi.MessageText).text.text)
            assertTrue(edited.isPinned && edited.editDate == NOW)
            assertTrue(
                updates.any { it is TdApi.UpdateMessageEdited } && updates.any { it is TdApi.UpdateMessageIsPinned },
            )
            assertEquals(
                404,
                (
                    client.send(
                        TdApi.EditMessageText(DemoData.ADA_CHAT, theirs.id, null, text("x")),
                    ) as TdResult.Failure
                ).error.code,
            )
        }

    @Test
    fun `a scheduled message waits outside the history until it is sent now`() =
        runTest {
            val (client, _) = client()
            val later =
                TdApi.MessageSendOptions().apply {
                    schedulingState =
                        TdApi.MessageSchedulingStateSendAtDate(NOW + 3_600, 0)
                }

            val scheduled = client.ask(TdApi.SendMessage(DemoData.BEN_CHAT, null, null, later, null, text("Tomorrow")))

            assertTrue(client.ask(TdApi.GetChat(DemoData.BEN_CHAT)).hasScheduledMessages)
            assertEquals(
                listOf(scheduled.id),
                client.ask(TdApi.GetChatScheduledMessages(DemoData.BEN_CHAT)).messages.map { it.id },
            )
            assertTrue(
                client
                    .ask(TdApi.GetChatHistory(DemoData.BEN_CHAT, 0, 0, 1, false))
                    .messages
                    .single()
                    .id != scheduled.id,
            )

            client.ask(TdApi.EditMessageSchedulingState(DemoData.BEN_CHAT, scheduled.id, null))

            assertTrue(client.ask(TdApi.GetChatScheduledMessages(DemoData.BEN_CHAT)).messages.isEmpty())
            val newest = client.ask(TdApi.GetChatHistory(DemoData.BEN_CHAT, 0, 0, 1, false)).messages.single()
            assertEquals("Tomorrow", (newest.content as TdApi.MessageText).text.text)
        }

    @Test
    fun `an album's messages share an album id, and a message of someone else's can be deleted for the user`() =
        runTest {
            val (client, _) = client()
            val files = (1..2).map { File(outside, "$it.jpg").apply { writeBytes(ByteArray(4)) } }
            val photos =
                files.map {
                    TdApi.InputMessagePhoto(
                        TdApi.InputPhoto(TdApi.InputFileLocal(it.path), null, null, intArrayOf(), 10, 10),
                        null,
                        false,
                        null,
                        false,
                    )
                }

            val album = client.ask(TdApi.SendMessageAlbum(DemoData.ADA_CHAT, null, null, null, photos.toTypedArray()))
            val theirs = client.ask(TdApi.GetChatHistory(DemoData.ADA_CHAT, 0, 2, 1, false)).messages.single()
            client.ask(TdApi.DeleteMessages(DemoData.ADA_CHAT, longArrayOf(theirs.id), false))

            assertEquals(
                1,
                album.messages
                    .map { it.mediaAlbumId }
                    .distinct()
                    .size,
            )
            assertEquals(
                404,
                (client.send(TdApi.GetMessage(DemoData.ADA_CHAT, theirs.id)) as TdResult.Failure).error.code,
            )
        }

    private fun text(text: String) = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, false)

    private companion object {
        const val NOW = 1_800_000_000
    }
}
