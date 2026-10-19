package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The requests [TdlibMessageActionRepository] sends for the message actions (`REQUIREMENTS.md` FR-C6, FR-C7), and how
 * it maps TDLib's answers: properties, available reactions without Premium-only or paid ones, reactions, edits, delete
 * for oneself or everyone, pins with their options, and resending.
 */
class TdlibMessageActionRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibMessageActionRepository(client)

    private fun answerOk() {
        client.on(TdApi.AddMessageReaction::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.RemoveMessageReaction::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.DeleteMessages::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.PinChatMessage::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.UnpinChatMessage::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.EditMessageText::class, TdResult.Success(message(id = MESSAGE, chatId = CHAT)))
        client.on(TdApi.EditMessageCaption::class, TdResult.Success(message(id = MESSAGE, chatId = CHAT)))
    }

    @Nested
    inner class Properties {
        @Test
        fun `asks TDLib for the message's properties and maps what the actions need`() =
            runTest {
                val properties =
                    TdApi.MessageProperties().apply {
                        canBeReplied = true
                        canBeEdited = true
                        canBePinned = false
                        canBeDeletedOnlyForSelf = true
                        canBeDeletedForAllUsers = false
                        canBeSaved = true
                        customEmojiIds = longArrayOf()
                    }
                client.on(TdApi.GetMessageProperties::class, TdResult.Success(properties))

                val result = repository.properties(CHAT, MESSAGE)

                val request = client.sentOf<TdApi.GetMessageProperties>().single()
                assertEquals(listOf(CHAT, MESSAGE), listOf(request.chatId, request.messageId))
                assertEquals(
                    Outcome.Success(
                        MessageProperties(
                            canBeReplied = true,
                            canBeEdited = true,
                            canBePinned = false,
                            canBeDeletedOnlyForSelf = true,
                            canBeDeletedForAllUsers = false,
                            canBeSaved = true,
                        ),
                    ),
                    result,
                )
            }

        @Test
        fun `protected content cannot be saved, nor copied`() =
            runTest {
                val properties =
                    TdApi.MessageProperties().apply {
                        canBeSaved = false
                        hasProtectedContentByOtherUser = true
                        customEmojiIds = longArrayOf()
                    }
                client.on(TdApi.GetMessageProperties::class, TdResult.Success(properties))

                val result = repository.properties(CHAT, MESSAGE) as Outcome.Success

                assertEquals(false, result.value.canBeSaved)
            }

        @Test
        fun `an unknown message fails with NotFound`() =
            runTest {
                client.on(TdApi.GetMessageProperties::class, TdResult.Failure(TdError(404, "Not Found")))

                assertEquals(Outcome.Failure(DomainError.NotFound), repository.properties(CHAT, MESSAGE))
            }
    }

    @Nested
    inner class Reactions {
        private fun available(vararg top: TdApi.AvailableReaction) =
            TdApi.AvailableReactions(arrayOf(*top), emptyArray(), emptyArray(), false, false, null)

        private fun emoji(
            emoji: String,
            needsPremium: Boolean = false,
        ) = TdApi.AvailableReaction(TdApi.ReactionTypeEmoji(emoji), needsPremium)

        @Test
        fun `the top reactions come in TDLib's order, for rows of eight`() =
            runTest {
                client.on(
                    TdApi.GetMessageAvailableReactions::class,
                    TdResult.Success(
                        available(
                            emoji("👍"),
                            emoji("❤️"),
                            TdApi.AvailableReaction(TdApi.ReactionTypeCustomEmoji(5), false),
                        ),
                    ),
                )

                val result = repository.availableReactions(CHAT, MESSAGE)

                val request = client.sentOf<TdApi.GetMessageAvailableReactions>().single()
                assertEquals(
                    listOf(CHAT, MESSAGE, 8L),
                    listOf(request.chatId, request.messageId, request.rowSize.toLong()),
                )
                assertEquals(
                    Outcome.Success(
                        AvailableReactions(
                            persistentListOf(
                                ReactionType.Emoji("👍"),
                                ReactionType.Emoji("❤️"),
                                ReactionType.CustomEmoji(5),
                            ),
                        ),
                    ),
                    result,
                )
            }

        @Test
        fun `reactions that need Premium and the paid reaction are left out`() =
            runTest {
                client.on(
                    TdApi.GetMessageAvailableReactions::class,
                    TdResult.Success(
                        available(
                            TdApi.AvailableReaction(TdApi.ReactionTypePaid(), false),
                            emoji("👍"),
                            TdApi.AvailableReaction(TdApi.ReactionTypeCustomEmoji(5), true),
                        ),
                    ),
                )

                val result = repository.availableReactions(CHAT, MESSAGE)

                assertEquals(Outcome.Success(AvailableReactions(persistentListOf(ReactionType.Emoji("👍")))), result)
            }

        @Test
        fun `a reaction is added small and to the recent reactions`() =
            runTest {
                answerOk()

                val result = repository.addReaction(CHAT, MESSAGE, ReactionType.Emoji("🔥"))

                val request = client.sentOf<TdApi.AddMessageReaction>().single()
                assertEquals(Outcome.Success(Unit), result)
                assertEquals(
                    listOf<Any>(CHAT, MESSAGE, "🔥", false, true),
                    listOf(
                        request.chatId,
                        request.messageId,
                        (request.reactionType as TdApi.ReactionTypeEmoji).emoji,
                        request.isBig,
                        request.updateRecentReactions,
                    ),
                )
            }

        @Test
        fun `a custom emoji reaction is added by its id`() =
            runTest {
                answerOk()

                repository.addReaction(CHAT, MESSAGE, ReactionType.CustomEmoji(5))

                val type = client.sentOf<TdApi.AddMessageReaction>().single().reactionType
                assertEquals(5L, (type as TdApi.ReactionTypeCustomEmoji).customEmojiId)
            }

        @Test
        fun `a reaction is removed`() =
            runTest {
                answerOk()

                val result = repository.removeReaction(CHAT, MESSAGE, ReactionType.Emoji("🔥"))

                val request = client.sentOf<TdApi.RemoveMessageReaction>().single()
                assertEquals(Outcome.Success(Unit), result)
                assertEquals(
                    listOf<Any>(CHAT, MESSAGE, "🔥"),
                    listOf(request.chatId, request.messageId, (request.reactionType as TdApi.ReactionTypeEmoji).emoji),
                )
            }

        @Test
        fun `the paid reaction is never sent`() =
            runTest {
                answerOk()

                val added = repository.addReaction(CHAT, MESSAGE, ReactionType.Paid)
                val removed = repository.removeReaction(CHAT, MESSAGE, ReactionType.Paid)

                assertTrue(added is Outcome.Failure && removed is Outcome.Failure)
                assertEquals(emptyList<TdApi.Function<*>>(), client.sent)
            }

        @Test
        fun `a flood wait fails with FloodWait`() =
            runTest {
                val wait = TdApiFixtures.floodWait(12)
                client.on(TdApi.AddMessageReaction::class, TdResult.Failure(TdError(wait.code, wait.message)))

                val result = repository.addReaction(CHAT, MESSAGE, ReactionType.Emoji("🔥"))

                assertEquals(Outcome.Failure(DomainError.FloodWait(12)), result)
            }
    }

    @Nested
    inner class Editing {
        private val formatted =
            FormattedText(
                "bold #tag",
                persistentListOf(TextEntity(0, 4, TextEntityType.Bold), TextEntity(5, 4, TextEntityType.Hashtag)),
            )

        @Test
        fun `a text is edited with its formatting, without touching the draft`() =
            runTest {
                answerOk()

                val result = repository.editText(CHAT, MESSAGE, formatted)

                val request = client.sentOf<TdApi.EditMessageText>().single()
                val content = request.inputMessageContent as TdApi.InputMessageText
                assertEquals(Outcome.Success(Unit), result)
                assertEquals(
                    listOf<Any?>(CHAT, MESSAGE, null),
                    listOf(request.chatId, request.messageId, request.replyMarkup),
                )
                assertEquals(
                    listOf<Any?>("bold #tag", 1, "TextEntityTypeBold", false, null),
                    listOf(
                        content.text.text,
                        content.text.entities.size,
                        content.text.entities[0]
                            .type.javaClass.simpleName,
                        content.clearDraft,
                        content.linkPreviewOptions,
                    ),
                )
            }

        @Test
        fun `a caption is edited where it is, above or below the media`() =
            runTest {
                answerOk()

                repository.editCaption(CHAT, MESSAGE, formatted, showCaptionAboveMedia = true)

                val request = client.sentOf<TdApi.EditMessageCaption>().single()
                assertEquals(
                    listOf<Any?>(CHAT, MESSAGE, null, "bold #tag", 1, true),
                    listOf(
                        request.chatId,
                        request.messageId,
                        request.replyMarkup,
                        request.caption.text,
                        request.caption.entities.size,
                        request.showCaptionAboveMedia,
                    ),
                )
            }

        @Test
        fun `a refused edit fails with its error`() =
            runTest {
                client.on(TdApi.EditMessageText::class, TdResult.Failure(TdError(400, "MESSAGE_EDIT_TIME_EXPIRED")))

                val result = repository.editText(CHAT, MESSAGE, formatted)

                assertEquals(Outcome.Failure(DomainError.Unknown(400, "MESSAGE_EDIT_TIME_EXPIRED")), result)
            }
    }

    @Nested
    inner class DeletingAndPinning {
        @Test
        fun `deleting for everyone revokes the messages`() =
            runTest {
                answerOk()

                repository.delete(CHAT, listOf(MESSAGE, MESSAGE + 1), forEveryone = true)

                val request = client.sentOf<TdApi.DeleteMessages>().single()
                assertEquals(
                    listOf<Any>(CHAT, listOf(MESSAGE, MESSAGE + 1), true),
                    listOf(request.chatId, request.messageIds.toList(), request.revoke),
                )
            }

        @Test
        fun `deleting for oneself does not revoke them`() =
            runTest {
                answerOk()

                repository.delete(CHAT, listOf(MESSAGE), forEveryone = false)

                assertEquals(false, client.sentOf<TdApi.DeleteMessages>().single().revoke)
            }

        @Test
        fun `pinning notifies, for everyone`() =
            runTest {
                answerOk()

                repository.pin(CHAT, MESSAGE, onlyForSelf = false)

                val request = client.sentOf<TdApi.PinChatMessage>().single()
                assertEquals(
                    listOf<Any>(CHAT, MESSAGE, false, false),
                    listOf(request.chatId, request.messageId, request.disableNotification, request.onlyForSelf),
                )
            }

        @Test
        fun `pinning for oneself only`() =
            runTest {
                answerOk()

                repository.pin(CHAT, MESSAGE, onlyForSelf = true)

                val request = client.sentOf<TdApi.PinChatMessage>().single()
                assertEquals(listOf(false, true), listOf(request.disableNotification, request.onlyForSelf))
            }

        @Test
        fun `unpinning`() =
            runTest {
                answerOk()

                val result = repository.unpin(CHAT, MESSAGE)

                val request = client.sentOf<TdApi.UnpinChatMessage>().single()
                assertEquals(Outcome.Success(Unit), result)
                assertEquals(listOf(CHAT, MESSAGE), listOf(request.chatId, request.messageId))
            }
    }

    @Nested
    inner class Resending {
        @Test
        fun `failed messages are sent again in increasing order, without a quote or Stars`() =
            runTest {
                client.on(TdApi.ResendMessages::class) { request ->
                    TdResult.Success(messages(*request.messageIds.map { message(id = it + 100) }.toTypedArray()))
                }

                val result = repository.resend(CHAT, listOf(MESSAGE + 1, MESSAGE))

                val request = client.sentOf<TdApi.ResendMessages>().single()
                assertEquals(Outcome.Success(Unit), result)
                assertEquals(
                    listOf<Any?>(CHAT, listOf(MESSAGE, MESSAGE + 1), null, 0L),
                    listOf(request.chatId, request.messageIds.toList(), request.quote, request.paidMessageStarCount),
                )
            }

        @Test
        fun `a message TDLib cannot send again fails`() =
            runTest {
                client.on(TdApi.ResendMessages::class, TdResult.Success(messages(null)))

                val result = repository.resend(CHAT, listOf(MESSAGE))

                assertTrue(result is Outcome.Failure)
            }
    }

    private companion object {
        const val CHAT = 100L
        const val MESSAGE = 10L
    }
}
