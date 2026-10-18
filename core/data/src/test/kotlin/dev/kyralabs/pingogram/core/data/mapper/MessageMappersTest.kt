package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FileRef
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardInfo
import dev.kyralabs.pingogram.core.domain.model.InteractionInfo
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageOrigin
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReplyTo
import dev.kyralabs.pingogram.core.domain.model.SelfDestruct
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class MessageMappersTest {
    @Test
    fun `a message maps its ids, sender, dates, flags and content`() {
        val message =
            TdApiFixtures.message(
                id = 5,
                chatId = 100,
                sender = TdApi.MessageSenderUser(42),
                date = 1_700_000_000,
                editDate = 1_700_000_060,
                isOutgoing = true,
                isPinned = true,
                containsUnreadMention = true,
                mediaAlbumId = 9,
                viaBotUserId = 77,
                content = TdApiFixtures.messageText("Hi"),
            )

        assertEquals(
            Message(
                id = 5,
                chatId = 100,
                sender = MessageSender.User(42),
                date = 1_700_000_000,
                editDate = 1_700_000_060,
                isOutgoing = true,
                sendingState = null,
                isPinned = true,
                isChannelPost = false,
                containsUnreadMention = true,
                mediaAlbumId = 9,
                authorSignature = "",
                viaBotUserId = 77,
                replyTo = null,
                forwardInfo = null,
                interactionInfo = null,
                selfDestruct = null,
                content = MessageContent.Text(text("Hi"), linkPreview = null),
            ),
            message.toMessage(),
        )
    }

    @Test
    fun `a channel post is sent by the channel and keeps its signature`() {
        val message =
            TdApiFixtures
                .message(
                    sender = TdApi.MessageSenderChat(-1001),
                    isChannelPost = true,
                    authorSignature = "Editor",
                ).toMessage()

        assertEquals(
            Triple(MessageSender.Chat(-1001), true, "Editor"),
            Triple(message.sender, message.isChannelPost, message.authorSignature),
        )
    }

    @Test
    fun `a pending message is pending`() {
        val message = TdApiFixtures.message(isOutgoing = true, sendingState = TdApi.MessageSendingStatePending(1))

        assertEquals(SendingState.Pending, message.toMessage().sendingState)
    }

    @Test
    fun `a failed message can be retried after the delay TDLib gives`() {
        val failed =
            TdApi.MessageSendingStateFailed(TdApiFixtures.floodWait(30), true, false, false, false, 0, 30.0)

        assertEquals(
            SendingState.Failed(DomainError.FloodWait(30), canRetry = true, retryAfter = 30.seconds),
            TdApiFixtures.message(isOutgoing = true, sendingState = failed).toMessage().sendingState,
        )
    }

    @Test
    fun `a failed message without a delay has no retry-after`() {
        val failed =
            TdApi.MessageSendingStateFailed(
                TdApiFixtures.error(403, "CHAT_WRITE_FORBIDDEN"),
                false,
                false,
                false,
                false,
                0,
                0.0,
            )

        assertEquals(
            SendingState.Failed(DomainError.Unknown(403, "CHAT_WRITE_FORBIDDEN"), canRetry = false, retryAfter = null),
            TdApiFixtures.message(isOutgoing = true, sendingState = failed).toMessage().sendingState,
        )
    }

    @Test
    fun `a reply in the same chat keeps the quote`() {
        val replyTo =
            TdApi.MessageReplyToMessage(
                100,
                4,
                TdApi.TextQuote(TdApiFixtures.formattedText("quoted part"), 6, true),
                0,
                "",
                null,
                0,
                null,
            )

        assertEquals(
            ReplyTo.Message(100, 4, quote = text("quoted part"), origin = null, originDate = 0, content = null),
            TdApiFixtures.message(replyTo = replyTo).toMessage().replyTo,
        )
    }

    @Test
    fun `a reply to another chat carries the replied message's origin, date and media`() {
        val replyTo =
            TdApi.MessageReplyToMessage(
                -1002,
                8,
                null,
                0,
                "",
                TdApi.MessageOriginChannel(-1002, 8, "Editor"),
                1_699_000_000,
                TdApiFixtures.messageDocument(),
            )

        val reply = TdApiFixtures.message(replyTo = replyTo).toMessage().replyTo as ReplyTo.Message

        assertEquals(
            Triple(MessageOrigin.Channel(-1002, 8, "Editor"), 1_699_000_000L, FileRef(40, 1_024)),
            Triple(reply.origin, reply.originDate, (reply.content as MessageContent.Document).document.file),
        )
    }

    @Test
    fun `a reply to a story`() {
        val replyTo = TdApi.MessageReplyToStory(-1003, 12)

        assertEquals(ReplyTo.Story(-1003, 12), TdApiFixtures.message(replyTo = replyTo).toMessage().replyTo)
    }

    @Test
    fun `forwards keep the origin and the original date`() {
        val origins =
            listOf(
                TdApi.MessageOriginUser(42),
                TdApi.MessageOriginHiddenUser("Hidden Name"),
                TdApi.MessageOriginChat(-1004, "Admin"),
                TdApi.MessageOriginChannel(-1005, 33, ""),
            ).map { origin ->
                TdApiFixtures
                    .message(forwardInfo = TdApi.MessageForwardInfo(origin, 1_600_000_000, null, ""))
                    .toMessage()
                    .forwardInfo
            }

        assertEquals(
            listOf(
                ForwardInfo(MessageOrigin.User(42), 1_600_000_000),
                ForwardInfo(MessageOrigin.HiddenUser("Hidden Name"), 1_600_000_000),
                ForwardInfo(MessageOrigin.Chat(-1004, "Admin"), 1_600_000_000),
                ForwardInfo(MessageOrigin.Channel(-1005, 33, ""), 1_600_000_000),
            ),
            origins,
        )
    }

    @Test
    fun `interaction info carries views, forwards, comments and reactions`() {
        val reactions =
            TdApi.MessageReactions(
                arrayOf(
                    TdApi.MessageReaction(TdApi.ReactionTypeEmoji("👍"), 12, true, null, emptyArray()),
                    TdApi.MessageReaction(TdApi.ReactionTypeCustomEmoji(5_000_000_001), 3, false, null, emptyArray()),
                    TdApi.MessageReaction(TdApi.ReactionTypePaid(), 50, false, null, emptyArray()),
                ),
                false,
                emptyArray(),
                false,
            )
        val info = TdApi.MessageInteractionInfo(1_000, 20, TdApi.MessageReplyInfo(7, emptyArray(), 0, 0, 0), reactions)

        assertEquals(
            InteractionInfo(
                viewCount = 1_000,
                forwardCount = 20,
                replyCount = 7,
                reactions =
                    persistentListOf(
                        Reaction(ReactionType.Emoji("👍"), 12, isChosen = true),
                        Reaction(ReactionType.CustomEmoji(5_000_000_001), 3, isChosen = false),
                        Reaction(ReactionType.Paid, 50, isChosen = false),
                    ),
            ),
            TdApiFixtures.message(interactionInfo = info).toMessage().interactionInfo,
        )
    }

    @Test
    fun `interaction info without a thread or reactions has no reply count and no reactions`() {
        val info = TdApi.MessageInteractionInfo(10, 0, null, null)

        assertEquals(
            InteractionInfo(10, 0, replyCount = null, reactions = persistentListOf()),
            TdApiFixtures.message(interactionInfo = info).toMessage().interactionInfo,
        )
    }

    @Test
    fun `self-destruct types map to immediately and timer`() {
        val types =
            listOf(TdApi.MessageSelfDestructTypeImmediately(), TdApi.MessageSelfDestructTypeTimer(30), null)
                .map { TdApiFixtures.message(selfDestructType = it).toMessage().selfDestruct }

        assertEquals(listOf(SelfDestruct.Immediately, SelfDestruct.Timer(30), null), types)
    }

    @Test
    fun `mapping one TDLib message twice gives equal snapshots`() {
        val message = richMessage()

        assertEquals(message.toMessage(), message.toMessage())
        assertEquals(message.toMessage().hashCode(), message.toMessage().hashCode())
    }

    @Test
    fun `equal TDLib messages with their own byte arrays map to equal snapshots`() {
        assertEquals(richMessage().toMessage(), richMessage().toMessage())
    }

    @Test
    fun `a content type added by a newer TDLib keeps the message, as unsupported`() {
        val newContent =
            object : TdApi.MessageContent() {
                override fun getConstructor() = 0
            }

        val message = TdApiFixtures.message(id = 6, content = newContent).toMessage()

        assertEquals(6L to MessageContent.Unsupported, message.id to message.content)
    }

    @Test
    fun `a sender type added by a newer TDLib attributes the message to the chat`() {
        val newSender =
            object : TdApi.MessageSender() {
                override fun getConstructor() = 0
            }

        assertEquals(
            MessageSender.Chat(100),
            TdApiFixtures.message(chatId = 100, sender = newSender).toMessage().sender,
        )
    }

    @Test
    fun `a message that is not a reply has no reply`() {
        assertNull(TdApiFixtures.message().toMessage().replyTo)
    }

    /** A message touching every part of the model that holds arrays: entities, sizes, bytes, options, reactions. */
    private fun richMessage() =
        TdApiFixtures.message(
            content =
                TdApiFixtures.messagePhoto(
                    caption =
                        TdApi.FormattedText(
                            "Look",
                            arrayOf(TdApiFixtures.textEntity(0, 4, TdApi.TextEntityTypeBold())),
                        ),
                ),
            replyTo =
                TdApi.MessageReplyToMessage(
                    100,
                    2,
                    null,
                    0,
                    "",
                    TdApi.MessageOriginUser(43),
                    1_690_000_000,
                    TdApiFixtures.messageVoiceNote(),
                ),
            interactionInfo =
                TdApi.MessageInteractionInfo(
                    5,
                    1,
                    null,
                    TdApi.MessageReactions(
                        arrayOf(TdApi.MessageReaction(TdApi.ReactionTypeEmoji("🔥"), 2, false, null, emptyArray())),
                        false,
                        emptyArray(),
                        false,
                    ),
                ),
        )

    private fun text(text: String) = FormattedText(text, persistentListOf())
}
