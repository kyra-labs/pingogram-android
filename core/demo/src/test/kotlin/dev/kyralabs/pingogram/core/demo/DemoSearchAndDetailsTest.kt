package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DemoSearchAndDetailsTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun kotlinx.coroutines.test.TestScope.client() = with(harness) { start() }

    @Test
    fun `global search finds chats by title and messages by their words`() =
        runTest {
            val (client, _) = client()

            val chats = client.ask(TdApi.SearchChats("text ${R.string.demo_group_title}", null, 10)).chatIds.toList()
            val found =
                client.ask(
                    TdApi.SearchMessages(null, "Text ${R.string.demo_ben_slides}", "", 10, null, null, 0, 0),
                )

            assertEquals(listOf(DemoData.GROUP_CHAT), chats)
            assertEquals(listOf(DemoData.BEN_CHAT), found.messages.map { it.chatId })
        }

    @Test
    fun `search in a chat filters by sender, and shared media by kind`() =
        runTest {
            val (client, _) = client()

            val fromBen =
                client.ask(
                    TdApi.SearchChatMessages(
                        DemoData.GROUP_CHAT,
                        null,
                        "",
                        TdApi.MessageSenderUser(DemoData.BEN),
                        0,
                        0,
                        10,
                        null,
                    ),
                )
            val photos =
                client.ask(
                    TdApi.SearchChatMessages(
                        DemoData.ADA_CHAT,
                        null,
                        "",
                        null,
                        0,
                        0,
                        10,
                        TdApi.SearchMessagesFilterPhotoAndVideo(),
                    ),
                )

            assertTrue(
                fromBen.messages.isNotEmpty() &&
                    fromBen.messages.all { (it.senderId as TdApi.MessageSenderUser).userId == DemoData.BEN },
            )
            assertEquals(1, photos.totalCount)
            assertTrue(photos.messages.single().content is TdApi.MessagePhoto)
        }

    @Test
    fun `recently found chats are kept after a restart until cleared`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.AddRecentlyFoundChat(DemoData.ADA_CHAT))
            val (restarted, _) = client()
            val kept = restarted.ask(TdApi.SearchRecentlyFoundChats("", null, 10)).chatIds.toList()
            restarted.ask(TdApi.ClearRecentlyFoundChats())

            assertEquals(listOf(DemoData.ADA_CHAT), kept)
            assertTrue(restarted.ask(TdApi.SearchRecentlyFoundChats("", null, 10)).chatIds.isEmpty())
        }

    @Test
    fun `a typed text's links, mentions, hashtags and emails are found`() =
        runTest {
            val (client, _) = client()

            val entities =
                client
                    .ask(
                        TdApi.GetTextEntities("See https://example.org, ask @ada_example #launch or ada@example.org"),
                    ).entities

            assertEquals(
                listOf(
                    TdApi.TextEntityTypeUrl::class.java,
                    TdApi.TextEntityTypeMention::class.java,
                    TdApi.TextEntityTypeHashtag::class.java,
                    TdApi.TextEntityTypeEmailAddress::class.java,
                ),
                entities.map { it.type.javaClass },
            )
        }

    @Test
    fun `the group's members saw the user's message, and reactions and votes list who added them`() =
        runTest {
            val (client, _) = client()
            val own = client.ask(TdApi.SendMessage(DemoData.GROUP_CHAT, null, null, null, null, plainText("Hello")))
            val poll =
                client.ask(TdApi.GetChatHistory(DemoData.GROUP_CHAT, 0, 0, 10, false)).messages.first {
                    it.content is TdApi.MessagePoll
                }
            val reacted = client.ask(TdApi.GetChatHistory(DemoData.ADA_CHAT, 0, 0, 1, false)).messages.single()

            val viewers = client.ask(TdApi.GetMessageViewers(DemoData.GROUP_CHAT, own.id)).viewers.map { it.userId }
            val reactions = client.ask(TdApi.GetMessageAddedReactions(DemoData.ADA_CHAT, reacted.id, null, "", 10))
            val voters = client.ask(TdApi.GetPollVoters(DemoData.GROUP_CHAT, poll.id, 1, 0, 10))

            assertEquals(setOf(DemoData.ADA, DemoData.BEN, DemoData.CHLOE), viewers.toSet())
            assertEquals(DemoData.ME, (reactions.reactions.single().senderId as TdApi.MessageSenderUser).userId)
            assertEquals(
                listOf(DemoData.ADA, DemoData.CHLOE),
                voters.voters.map { (it.voterId as TdApi.MessageSenderUser).userId },
            )
        }

    @Test
    fun `the message in Spanish translates into English`() =
        runTest {
            val (client, _) = client()
            val spanish =
                client.ask(TdApi.GetChatHistory(DemoData.GROUP_CHAT, 0, 0, 10, false)).messages.first {
                    (it.content as? TdApi.MessageText)?.text?.text == "Text ${R.string.demo_group_spanish}"
                }

            val translated = client.ask(TdApi.TranslateMessageText(DemoData.GROUP_CHAT, spanish.id, "en", ""))

            assertEquals("Text ${R.string.demo_group_spanish_translation}", translated.text)
        }

    @Test
    fun `searching sticker sets finds the sample sets by title or name, and nothing for other words`() =
        runTest {
            val (client, _) = client()

            val rings = client.ask(TdApi.SearchStickerSets(TdApi.StickerTypeRegular(), "ring")).sets.map { it.id }
            val none = client.ask(TdApi.SearchStickerSets(TdApi.StickerTypeRegular(), "zebra")).sets

            assertEquals(listOf(DemoData.SHAPES_SET), rings)
            assertTrue(none.isEmpty())
        }

    @Test
    fun `sticker sets install, archive and reorder, kept after a restart, and their stickers can be sent`() =
        runTest {
            val (client, updates) = client()

            val trending =
                client
                    .ask(
                        TdApi.GetTrendingStickerSets(TdApi.StickerTypeRegular(), 0, 10),
                    ).sets
                    .map { it.id }
            client.ask(TdApi.ChangeStickerSet(DemoData.SHAPES_SET, true, false))
            client.ask(TdApi.ChangeStickerSet(DemoData.PINGS_SET, false, true))
            val (restarted, _) = client()

            assertEquals(listOf(DemoData.SHAPES_SET), trending)
            assertEquals(
                listOf(
                    DemoData.SHAPES_SET,
                ),
                restarted.ask(TdApi.GetInstalledStickerSets(TdApi.StickerTypeRegular())).sets.map {
                    it.id
                },
            )
            assertEquals(
                listOf(
                    DemoData.PINGS_SET,
                ),
                restarted.ask(TdApi.GetArchivedStickerSets(TdApi.StickerTypeRegular(), 0, 10)).sets.map {
                    it.id
                },
            )
            assertTrue(updates.any { it is TdApi.UpdateInstalledStickerSets })

            val sticker = restarted.ask(TdApi.GetStickerSet(DemoData.SHAPES_SET)).stickers.first()
            val sent =
                restarted.ask(
                    TdApi.SendMessage(
                        DemoData.ADA_CHAT,
                        null,
                        null,
                        null,
                        null,
                        TdApi.InputMessageSticker(
                            TdApi.InputSticker(TdApi.InputFileId(sticker.sticker.id), null, 512, 512),
                            sticker.emoji,
                        ),
                    ),
                )
            assertEquals(sticker.emoji, (sent.content as TdApi.MessageSticker).sticker.emoji)
        }
}
