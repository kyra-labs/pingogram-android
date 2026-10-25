package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReplyTarget
import dev.kyralabs.pingogram.core.domain.model.SendOptions
import dev.kyralabs.pingogram.core.domain.model.SendingState
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.domain.model.TextQuote
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeLinkPreviewSettingsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.pendingState
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateNewMessage
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** What [TdlibMessageRepository] sends for the composer: messages, silent and scheduled ones, and drafts. */
class TdlibMessageRepositorySendTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() =
        TdlibMessageRepository(
            client,
            backgroundScope,
            SecretChatLinkPreviews(client, FakeLinkPreviewSettingsRepository()),
        )

    private fun answerSend(answer: TdResult<TdApi.Message> = TdResult.Success(message(id = SENDING_ID))) =
        client.on(TdApi.SendMessage::class, answer)

    private fun sentMessage() = client.sentOf<TdApi.SendMessage>().single()

    private fun text(text: String) = FormattedText(text, persistentListOf())

    private fun TdApi.FormattedText.describe() =
        listOf(text) + entities.map { "${it.offset}+${it.length}:${it.type.javaClass.simpleName}" }

    private fun TdApi.InputMessageReplyToMessage.describe() = listOf(messageId, quote, checklistTaskId, pollOptionId)

    @Test
    fun `sendText sends the text to the chat itself and clears the draft`() =
        runTest {
            answerSend()

            val result = repository().sendText(CHAT, text("Hi"))

            val request = sentMessage()
            val content = request.inputMessageContent as TdApi.InputMessageText
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(
                listOf<Any?>(CHAT, null, null, null),
                listOf(request.chatId, request.topicId, request.replyTo, request.replyMarkup),
            )
            assertEquals(
                listOf<Any?>("Hi", true, null),
                listOf(content.text.text, content.clearDraft, content.linkPreviewOptions),
            )
        }

    @Test
    fun `a reply goes to the whole message of the same chat, without a quote`() =
        runTest {
            answerSend()

            repository().sendText(CHAT, text("Sure"), replyTo = ReplyTarget(42))

            val replyTo = sentMessage().replyTo as TdApi.InputMessageReplyToMessage
            assertEquals(listOf<Any?>(42L, null, 0, ""), replyTo.describe())
        }

    @Test
    fun `a quote reply sends the quoted part with its formatting and position`() =
        runTest {
            answerSend()
            val quoted =
                FormattedText(
                    "still on",
                    persistentListOf(TextEntity(0, 5, TextEntityType.Italic)),
                )

            repository().sendText(CHAT, text("Yes!"), replyTo = ReplyTarget(42, TextQuote(quoted, position = 15)))

            val replyTo = sentMessage().replyTo as TdApi.InputMessageReplyToMessage
            assertEquals(42L, replyTo.messageId)
            assertEquals(listOf("still on", "0+5:TextEntityTypeItalic"), replyTo.quote.text.describe())
            assertEquals(15, replyTo.quote.position)
        }

    @Test
    fun `a reply in another chat names that chat's message`() =
        runTest {
            answerSend()

            repository().sendText(CHAT, text("About this…"), replyTo = ReplyTarget(42, chatId = OTHER_CHAT))

            val replyTo = sentMessage().replyTo as TdApi.InputMessageReplyToExternalMessage
            assertEquals(
                listOf<Any?>(OTHER_CHAT, 42L, null, 0, ""),
                listOf(replyTo.chatId, replyTo.messageId, replyTo.quote, replyTo.checklistTaskId, replyTo.pollOptionId),
            )
        }

    @Test
    fun `sendText sends only the formatting a sender may set`() =
        runTest {
            answerSend()
            val text =
                FormattedText(
                    "bold link #tag",
                    persistentListOf(
                        TextEntity(0, 4, TextEntityType.Bold),
                        TextEntity(5, 4, TextEntityType.TextUrl("https://example.org")),
                        TextEntity(10, 4, TextEntityType.Hashtag),
                    ),
                )

            repository().sendText(CHAT, text)

            val content = sentMessage().inputMessageContent as TdApi.InputMessageText
            assertEquals(
                listOf("bold link #tag", "0+4:TextEntityTypeBold", "5+4:TextEntityTypeTextUrl"),
                content.text.describe(),
            )
            assertEquals("https://example.org", (content.text.entities[1].type as TdApi.TextEntityTypeTextUrl).url)
        }

    @Test
    fun `a message sent now notifies and is not scheduled`() =
        runTest {
            answerSend()

            repository().sendText(CHAT, text("Hi"))

            val options = sentMessage().options
            assertEquals(listOf<Any?>(false, null), listOf(options.disableNotification, options.schedulingState))
        }

    @Test
    fun `a silent message is sent without a notification`() =
        runTest {
            answerSend()

            repository().sendText(CHAT, text("Shh"), SendOptions(silent = true))

            assertTrue(sentMessage().options.disableNotification)
        }

    @Test
    fun `a scheduled message is sent at its date, once`() =
        runTest {
            answerSend()

            repository().sendText(CHAT, text("Later"), SendOptions(scheduledAt = SCHEDULED_AT))

            val atDate = sentMessage().options.schedulingState as TdApi.MessageSchedulingStateSendAtDate
            assertEquals(listOf(SCHEDULED_AT.toInt(), 0), listOf(atDate.sendDate, atDate.repeatPeriod))
        }

    @Test
    fun `a sent message is in the history as being sent once sendText returns`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetChatHistory::class, TdResult.Success(messages(message(id = 1, chatId = CHAT))))
            repository.loadOlder(CHAT)
            val pending = message(id = SENDING_ID, chatId = CHAT, isOutgoing = true, sendingState = pendingState())
            client.on(TdApi.SendMessage::class) {
                // As TDLib does: the update first, then the answer.
                client.emit(updateNewMessage(pending))
                TdResult.Success(pending)
            }

            repository.sendText(CHAT, text("Hi"))

            val history = repository.history(CHAT).first()
            assertEquals(listOf(SENDING_ID, 1L), history.messages.map { it.id })
            assertEquals(SendingState.Pending, history.messages.first().sendingState)
        }

    @Test
    fun `a flood wait while sending fails with FloodWait`() =
        runTest {
            val wait = TdApiFixtures.floodWait(30)
            answerSend(TdResult.Failure(TdError(wait.code, wait.message)))

            val result = repository().sendText(CHAT, text("Hi"))

            assertEquals(Outcome.Failure(DomainError.FloodWait(30)), result)
        }

    @Test
    fun `setDraft keeps a text draft of the chat itself`() =
        runTest {
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))
            val text = FormattedText("Later", persistentListOf(TextEntity(0, 5, TextEntityType.Italic)))

            val result = repository().setDraft(CHAT, text)

            val request = client.sentOf<TdApi.SetChatDraftMessage>().single()
            val content = request.draftMessage.content as TdApi.DraftMessageContentText
            assertEquals(Outcome.Success(Unit), result)
            assertEquals(
                listOf<Any?>(CHAT, null, null),
                listOf(request.chatId, request.topicId, request.draftMessage.replyTo),
            )
            assertEquals(listOf("Later", "0+5:TextEntityTypeItalic"), content.text.describe())
        }

    @Test
    fun `an empty draft removes the chat's draft`() =
        runTest {
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))

            repository().setDraft(CHAT, FormattedText.Empty)

            assertNull(client.sentOf<TdApi.SetChatDraftMessage>().single().draftMessage)
        }

    @Test
    fun `a draft keeps the message it replies to`() =
        runTest {
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))

            repository().setDraft(CHAT, text("Sure"), replyTo = ReplyTarget(42))

            val draft = client.sentOf<TdApi.SetChatDraftMessage>().single().draftMessage
            val replyTo = draft.replyTo as TdApi.InputMessageReplyToMessage
            assertEquals(listOf<Any?>(42L, null, 0, ""), replyTo.describe())
            assertEquals(listOf("Sure"), (draft.content as TdApi.DraftMessageContentText).text.describe())
        }

    @Test
    fun `a draft keeps a quote and a reply in another chat`() =
        runTest {
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))
            val quote = TextQuote(text("tonight"), position = 26)

            repository().setDraft(CHAT, text("Sure"), replyTo = ReplyTarget(42, quote, chatId = OTHER_CHAT))

            val replyTo =
                client
                    .sentOf<TdApi.SetChatDraftMessage>()
                    .single()
                    .draftMessage.replyTo
                    as TdApi.InputMessageReplyToExternalMessage
            assertEquals(
                listOf<Any?>(OTHER_CHAT, 42L, 26),
                listOf(replyTo.chatId, replyTo.messageId, replyTo.quote.position),
            )
            assertEquals(listOf("tonight"), replyTo.quote.text.describe())
        }

    @Test
    fun `a reply without a text is still a draft`() =
        runTest {
            client.on(TdApi.SetChatDraftMessage::class, TdResult.Success(TdApi.Ok()))

            repository().setDraft(CHAT, FormattedText.Empty, replyTo = ReplyTarget(42))

            val draft = client.sentOf<TdApi.SetChatDraftMessage>().single().draftMessage
            assertEquals(42L, (draft.replyTo as TdApi.InputMessageReplyToMessage).messageId)
            assertEquals(listOf(""), (draft.content as TdApi.DraftMessageContentText).text.describe())
        }

    // Messages for the reply and edit bars

    @Test
    fun `message asks TDLib for a message that is not loaded`() =
        runTest {
            client.on(TdApi.GetMessage::class, TdResult.Success(message(id = 42, chatId = CHAT)))

            val result = repository().message(CHAT, 42)

            val request = client.sentOf<TdApi.GetMessage>().single()
            assertEquals(listOf(CHAT, 42L), listOf(request.chatId, request.messageId))
            assertEquals(42L, (result as Outcome.Success).value.id)
        }

    @Test
    fun `a loaded message needs no request`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetChatHistory::class, TdResult.Success(messages(message(id = 42, chatId = CHAT))))
            repository.loadOlder(CHAT)

            val result = repository.message(CHAT, 42)

            assertEquals(42L, (result as Outcome.Success).value.id)
            assertTrue(client.sentOf<TdApi.GetMessage>().isEmpty())
        }

    @Test
    fun `a deleted message fails with NotFound`() =
        runTest {
            client.on(TdApi.GetMessage::class, TdResult.Failure(TdError(404, "Not Found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().message(CHAT, 42))
        }

    private companion object {
        const val CHAT = 100L

        /** A chat a reply in another chat replies to. */
        const val OTHER_CHAT = 200L

        /** The temporary id of a message being sent: larger than every sent one. */
        const val SENDING_ID = 1_000_000L

        const val SCHEDULED_AT = 1_800_000_000L
    }
}
