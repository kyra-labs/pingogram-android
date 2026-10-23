package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AvailableReactions
import dev.kyralabs.pingogram.core.domain.model.ChatReportResult
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ForwardOptions
import dev.kyralabs.pingogram.core.domain.model.MessageProperties
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.ReportOption
import dev.kyralabs.pingogram.core.domain.model.ReportOptionId
import dev.kyralabs.pingogram.core.domain.model.Sticker
import dev.kyralabs.pingogram.core.domain.model.StickerFormat
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.message
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.messages
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The requests [TdlibMessageActionRepository] sends for the message actions (`REQUIREMENTS.md` FR-C6, FR-C7), and how
 * it maps TDLib's answers: properties, available reactions without Premium-only or paid ones, reactions, edits, delete
 * for oneself or everyone, pins with their options, resending, forwarding with a comment and options, and reporting.
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
                        canBeForwarded = true
                        canBeCopied = true
                        canBeCopiedToSecretChat = false
                        canReportChat = true
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
                            canBeForwarded = true,
                            canBeCopied = true,
                            canBeCopiedToSecretChat = false,
                            canBeReported = true,
                        ),
                    ),
                    result,
                )
            }

        @Test
        fun `protected content cannot be saved, copied nor forwarded`() =
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
                assertEquals(false, result.value.canBeForwarded)
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
        private fun available(
            vararg top: TdApi.AvailableReaction,
            recent: Array<TdApi.AvailableReaction> = emptyArray(),
            popular: Array<TdApi.AvailableReaction> = emptyArray(),
        ) = TdApi.AvailableReactions(arrayOf(*top), recent, popular, true, false, null)

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
        fun `the recent and popular reactions follow, each reaction in its first list only`() =
            runTest {
                client.on(
                    TdApi.GetMessageAvailableReactions::class,
                    TdResult.Success(
                        available(
                            emoji("👍"),
                            recent =
                                arrayOf(
                                    emoji("🔥"),
                                    emoji("👍"),
                                    TdApi.AvailableReaction(TdApi.ReactionTypeCustomEmoji(5), false),
                                ),
                            popular = arrayOf(emoji("🔥"), emoji("🎉"), emoji("🤩", needsPremium = true)),
                        ),
                    ),
                )

                val result = repository.availableReactions(CHAT, MESSAGE)

                assertEquals(
                    Outcome.Success(
                        AvailableReactions(
                            top = persistentListOf(ReactionType.Emoji("👍")),
                            recent = persistentListOf(ReactionType.Emoji("🔥"), ReactionType.CustomEmoji(5)),
                            popular = persistentListOf(ReactionType.Emoji("🎉")),
                        ),
                    ),
                    result,
                )
            }

        @Test
        fun `reactions off give no reactions`() =
            runTest {
                val off =
                    TdApi.AvailableReactions(
                        emptyArray(),
                        emptyArray(),
                        emptyArray(),
                        false,
                        false,
                        TdApi.ReactionUnavailabilityReasonGuest(),
                    )
                client.on(TdApi.GetMessageAvailableReactions::class, TdResult.Success(off))

                assertEquals(
                    Outcome.Success(AvailableReactions(persistentListOf())),
                    repository.availableReactions(CHAT, MESSAGE),
                )
            }

        @Test
        fun `custom emoji stickers come by their custom emoji id`() =
            runTest {
                val still =
                    TdApiFixtures.sticker(emoji = "😎", file = TdApiFixtures.file(id = 60)).apply {
                        fullType = TdApi.StickerFullTypeCustomEmoji(5, false)
                    }
                val animated =
                    TdApiFixtures.sticker(format = TdApi.StickerFormatTgs(), file = TdApiFixtures.file(id = 61)).apply {
                        fullType = TdApi.StickerFullTypeCustomEmoji(6, false)
                    }
                // Not a custom emoji: left out.
                val regular = TdApiFixtures.sticker(file = TdApiFixtures.file(id = 62))
                client.on(
                    TdApi.GetCustomEmojiStickers::class,
                    TdResult.Success(TdApi.Stickers(arrayOf(still, animated, regular))),
                )

                val result = repository.customEmojiStickers(listOf(5, 6, 5)) as Outcome.Success

                assertEquals(
                    listOf(5L, 6L),
                    client
                        .sentOf<TdApi.GetCustomEmojiStickers>()
                        .single()
                        .customEmojiIds
                        .toList(),
                )
                assertEquals(setOf(5L, 6L), result.value.keys)
                assertEquals(
                    60,
                    result.value
                        .getValue(5)
                        .file.id,
                )
                assertEquals(StickerFormat.Tgs, result.value.getValue(6).format)
            }

        @Test
        fun `no custom emoji asks TDLib nothing`() =
            runTest {
                assertEquals(
                    Outcome.Success(persistentMapOf<Long, Sticker>()),
                    repository.customEmojiStickers(emptyList()),
                )
                assertEquals(emptyList<TdApi.Function<*>>(), client.sent)
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

    @Nested
    inner class Forwarding {
        private fun answerForwards(vararg forwarded: TdApi.Message?) {
            client.on(TdApi.ForwardMessages::class, TdResult.Success(messages(*forwarded)))
            client.on(TdApi.SendMessage::class, TdResult.Success(message(id = 1, chatId = TARGET)))
        }

        @Test
        fun `forwards the messages in increasing order to the chat itself, sent now`() =
            runTest {
                answerForwards(message(id = 1, chatId = TARGET), message(id = 2, chatId = TARGET))

                val result = repository.forward(CHAT, listOf(MESSAGE + 1, MESSAGE), TARGET, null, ForwardOptions())

                assertEquals(Outcome.Success(Unit), result)
                val request = client.sentOf<TdApi.ForwardMessages>().single()
                assertEquals(
                    listOf<Any?>(TARGET, null, CHAT, listOf(MESSAGE, MESSAGE + 1), false, false),
                    listOf(
                        request.chatId,
                        request.topicId,
                        request.fromChatId,
                        request.messageIds.toList(),
                        request.sendCopy,
                        request.removeCaption,
                    ),
                )
                assertEquals(
                    listOf(false, null),
                    listOf(request.options.disableNotification, request.options.schedulingState),
                )
                assertTrue(client.sentOf<TdApi.SendMessage>().isEmpty())
            }

        @Test
        fun `hiding the sender's name sends copies, hiding captions leaves them out`() =
            runTest {
                answerForwards(message(id = 1, chatId = TARGET))

                repository.forward(CHAT, listOf(MESSAGE), TARGET, null, ForwardOptions(hideSenderName = true))
                repository.forward(CHAT, listOf(MESSAGE), TARGET, null, ForwardOptions(true, hideCaptions = true))
                // TDLib keeps the captions of forwards: without copies, hiding them asks for nothing.
                repository.forward(CHAT, listOf(MESSAGE), TARGET, null, ForwardOptions(hideCaptions = true))

                assertEquals(
                    listOf(true to false, true to true, false to false),
                    client.sentOf<TdApi.ForwardMessages>().map { it.sendCopy to it.removeCaption },
                )
            }

        @Test
        fun `the comment goes first, as a text that keeps the target's draft`() =
            runTest {
                answerForwards(message(id = 1, chatId = TARGET))
                val comment = FormattedText("Look", persistentListOf(TextEntity(0, 4, TextEntityType.Bold)))

                repository.forward(CHAT, listOf(MESSAGE), TARGET, comment, ForwardOptions())

                assertEquals(listOf("SendMessage", "ForwardMessages"), client.sent.map { it.javaClass.simpleName })
                val send = client.sentOf<TdApi.SendMessage>().single()
                val content = send.inputMessageContent as TdApi.InputMessageText
                assertEquals(
                    listOf<Any>(TARGET, "Look", 1, false),
                    listOf(send.chatId, content.text.text, content.text.entities.size, content.clearDraft),
                )
            }

        @Test
        fun `a comment TDLib refuses forwards nothing`() =
            runTest {
                client.on(TdApi.SendMessage::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 12")))

                val comment = FormattedText("Hi", persistentListOf())
                val result = repository.forward(CHAT, listOf(MESSAGE), TARGET, comment, ForwardOptions())

                assertEquals(Outcome.Failure(DomainError.FloodWait(12)), result)
                assertTrue(client.sentOf<TdApi.ForwardMessages>().isEmpty())
            }

        @Test
        fun `a message TDLib does not forward fails as not allowed`() =
            runTest {
                answerForwards(message(id = 1, chatId = TARGET), null)

                val result = repository.forward(CHAT, listOf(MESSAGE, MESSAGE + 1), TARGET, null, ForwardOptions())

                assertEquals(Outcome.Failure(DomainError.SendingNotAllowed), result)
            }

        @Test
        fun `a refused forward reports TDLib's error`() =
            runTest {
                client.on(TdApi.ForwardMessages::class, TdResult.Failure(TdError(420, "FLOOD_WAIT_30")))

                val result = repository.forward(CHAT, listOf(MESSAGE), TARGET, null, ForwardOptions())

                assertEquals(Outcome.Failure(DomainError.FloodWait(30)), result)
            }
    }

    @Nested
    inner class Reporting {
        @Test
        fun `the first report sends an empty option and the messages, a chosen option its id and the comment`() =
            runTest {
                client.on(TdApi.ReportChat::class, TdResult.Success(TdApi.ReportChatResultOk()))

                repository.report(CHAT, listOf(MESSAGE, MESSAGE + 1), ReportOptionId.Initial)
                repository.report(CHAT, listOf(MESSAGE), ReportOptionId(byteArrayOf(7, 8)), "It is spam")

                val requests = client.sentOf<TdApi.ReportChat>()
                assertEquals(listOf(CHAT, CHAT), requests.map { it.chatId })
                assertEquals(
                    listOf(listOf(MESSAGE, MESSAGE + 1), listOf(MESSAGE)),
                    requests.map { it.messageIds.toList() },
                )
                assertArrayEquals(ByteArray(0), requests[0].optionId)
                assertArrayEquals(byteArrayOf(7, 8), requests[1].optionId)
                assertEquals(listOf("", "It is spam"), requests.map { it.text })
            }

        @Test
        fun `each report result maps`() =
            runTest {
                val options =
                    arrayOf(TdApi.ReportOption(byteArrayOf(1), "Spam"), TdApi.ReportOption(byteArrayOf(2), "Violence"))
                val answers =
                    listOf(
                        TdApi.ReportChatResultOk() to ChatReportResult.Reported,
                        TdApi.ReportChatResultOptionRequired("Why?", options) to
                            ChatReportResult.OptionRequired(
                                "Why?",
                                persistentListOf(
                                    ReportOption(ReportOptionId(byteArrayOf(1)), "Spam"),
                                    ReportOption(ReportOptionId(byteArrayOf(2)), "Violence"),
                                ),
                            ),
                        TdApi.ReportChatResultTextRequired(byteArrayOf(3), true) to
                            ChatReportResult.TextRequired(ReportOptionId(byteArrayOf(3)), isOptional = true),
                        TdApi.ReportChatResultMessagesRequired() to ChatReportResult.MessagesRequired,
                    )

                for ((answer, expected) in answers) {
                    client.on(TdApi.ReportChat::class, TdResult.Success(answer))

                    assertEquals(
                        Outcome.Success(expected),
                        repository.report(CHAT, listOf(MESSAGE), ReportOptionId.Initial),
                    )
                }
            }

        @Test
        fun `a refused report fails with its error`() =
            runTest {
                client.on(TdApi.ReportChat::class, TdResult.Failure(TdError(420, "FLOOD_WAIT_30")))

                val result = repository.report(CHAT, listOf(MESSAGE), ReportOptionId.Initial)

                assertEquals(Outcome.Failure(DomainError.FloodWait(30)), result)
            }
    }

    private companion object {
        const val CHAT = 100L
        const val MESSAGE = 10L
        const val TARGET = 200L
    }
}
