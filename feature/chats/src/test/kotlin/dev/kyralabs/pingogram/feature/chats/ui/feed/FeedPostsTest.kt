package dev.kyralabs.pingogram.feature.chats.ui.feed

import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.PhotoSize
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.text
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * [feedPosts]: a card per post, an album's messages as one card with its first four pictures and "+N", the text and
 * media each kind shows, reactions and views, and which posts are unread in their channels.
 */
class FeedPostsTest {
    private val channel = chat(1, type = ChatType.Supergroup(10, isChannel = true), title = "Film Photo Club")

    private fun post(
        id: Long,
        content: MessageContent = MessageContent.Text(text("Hello"), linkPreview = null),
        albumId: Long = 0,
        lastRead: Long = 0,
        info: InteractionInfo? = null,
    ) = FoundMessage(
        message(1, id = id, content = content).copy(mediaAlbumId = albumId, interactionInfo = info),
        channel.copy(lastReadInboxMessageId = lastRead),
    )

    private fun photo(fileId: Int) =
        MessageContent.Photo(
            photo =
                Photo(
                    persistentListOf(
                        PhotoSize("s", 90, 60, FileRef(fileId + 1000, 0)),
                        PhotoSize("y", 1280, 853, FileRef(fileId, 0)),
                    ),
                    minithumbnail = null,
                ),
            caption = text(""),
            hasSpoiler = false,
            isSecret = false,
            showCaptionAboveMedia = false,
        )

    private fun cards(vararg posts: FoundMessage) = feedPosts(posts.toList()) { "9:41 AM" }

    @Test
    fun `each post is a card with its channel, time and text`() {
        val card = cards(post(11)).single()

        assertEquals("1:11", card.key)
        assertEquals("Film Photo Club", card.channelTitle)
        assertEquals("9:41 AM", card.time)
        assertEquals("Hello", card.text?.text)
        assertNull(card.media)
    }

    @Test
    fun `an album is one card with its first four pictures and how many more`() {
        val album = (21L..27L).map { post(it, photo(it.toInt()), albumId = 5) }
        val cards = cards(post(30), *album.toTypedArray())

        assertEquals(2, cards.size)
        val media = cards[1].media as FeedMedia.Album
        assertEquals(listOf(21, 22, 23, 24), media.fileIds)
        assertEquals(3, media.more)
        assertEquals((21L..27L).toList(), cards[1].messageIds)
    }

    @Test
    fun `a photo shows its largest size, a file its name and size`() {
        val document =
            MessageContent.Document(
                Document("beta.apk", "application/vnd.android.package-archive", null, null, FileRef(7, 38_200_000)),
                caption = text("0.9.4 is out"),
            )
        val (photo, file) = cards(post(12, photo(40)), post(11, document))

        assertEquals(FeedMedia.Photo(40, 1280, 853), photo.media)
        assertEquals(FeedMedia.File("beta.apk", 38_200_000), file.media)
        assertEquals("0.9.4 is out", file.text?.text)
    }

    @Test
    fun `reactions and views come from the post's interaction info`() {
        val info =
            InteractionInfo(
                viewCount = 3_400,
                forwardCount = 0,
                replyCount = null,
                reactions =
                    persistentListOf(
                        Reaction(ReactionType.Emoji("🔥"), 214, isChosen = true),
                        Reaction(ReactionType.CustomEmoji(99), 3, isChosen = false),
                    ),
            )

        val card = cards(post(11, info = info)).single()

        assertEquals(listOf(FeedReaction("🔥", 214, true), FeedReaction(null, 3, false)), card.reactions)
        assertEquals(3_400, card.viewCount)
    }

    @Test
    fun `a post is unread while newer than its channel's last read post`() {
        val cards = cards(post(12, lastRead = 11), post(11, lastRead = 11))

        assertEquals(listOf(true, false), cards.map { it.isUnread })
    }
}
