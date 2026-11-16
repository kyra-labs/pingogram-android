package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.SavedMessagesTag
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Saved Messages' tags: `getSavedMessagesTags` and its update, `searchSavedMessages` by tag, labels. */
class TdlibSavedMessagesTagRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibSavedMessagesTagRepository(client)

    private fun tags(vararg tags: TdApi.SavedMessagesTag) = TdApi.SavedMessagesTags(arrayOf(*tags))

    private fun tag(
        emoji: String,
        label: String,
        count: Int,
    ) = TdApi.SavedMessagesTag(TdApi.ReactionTypeEmoji(emoji), label, count)

    @Test
    fun `reads the tags of all of Saved Messages, then again when they change`() =
        runTest {
            client.on(TdApi.GetSavedMessagesTags::class, TdResult.Success(tags(tag("❤", "Ideas", 3))))

            repository.tags().test {
                assertEquals(listOf(SavedMessagesTag(ReactionType.Emoji("❤"), "Ideas", 3)), awaitItem())
                assertEquals(0L, client.sentOf<TdApi.GetSavedMessagesTags>().single().savedMessagesTopicId)

                client.on(TdApi.GetSavedMessagesTags::class, TdResult.Success(tags(tag("👍", "", 7))))
                client.emit(TdApi.UpdateSavedMessagesTags(0, tags(tag("👍", "", 7))))
                assertEquals(listOf(SavedMessagesTag(ReactionType.Emoji("👍"), "", 7)), awaitItem())

                // A topic's own tags change nothing here.
                client.emit(TdApi.UpdateSavedMessagesTags(5, tags()))
                expectNoEvents()
            }
        }

    @Test
    fun `tags that cannot be read are none`() =
        runTest {
            client.on(TdApi.GetSavedMessagesTags::class, TdResult.Failure(TdError(400, "PREMIUM_ACCOUNT_REQUIRED")))

            repository.tags().test { assertEquals(emptyList<SavedMessagesTag>(), awaitItem()) }
        }

    @Test
    fun `a tag's messages are a search of Saved Messages by it, without words`() =
        runTest {
            client.on(
                TdApi.SearchSavedMessages::class,
                TdResult.Success(
                    TdApi.FoundChatMessages(
                        3,
                        arrayOf(TdApiFixtures.message(id = 9), TdApiFixtures.message(id = 8)),
                        8,
                    ),
                ),
            )

            val found = (repository.taggedMessages(ReactionType.Emoji("❤"), 0, 20) as Outcome.Success).value

            assertEquals(listOf(9L, 8L), found.messages.map { it.id })
            assertEquals(3, found.totalCount)
            assertEquals(8L, found.nextFromMessageId)
            val request = client.sentOf<TdApi.SearchSavedMessages>().single()
            assertEquals(0L, request.savedMessagesTopicId)
            assertEquals("❤", (request.tag as TdApi.ReactionTypeEmoji).emoji)
            assertEquals("", request.query)
            assertEquals(20, request.limit)
        }

    @Test
    fun `a label is set trimmed, and Telegram's refusal is its error`() =
        runTest {
            client.on(TdApi.SetSavedMessagesTagLabel::class, TdResult.Success(TdApi.Ok()))
            assertEquals(Outcome.Success(Unit), repository.setLabel(ReactionType.Emoji("❤"), " Ideas "))
            assertEquals("Ideas", client.sentOf<TdApi.SetSavedMessagesTagLabel>().single().label)

            client.on(TdApi.SetSavedMessagesTagLabel::class, TdResult.Failure(TdError(400, "PREMIUM_ACCOUNT_REQUIRED")))
            assertEquals(
                Outcome.Failure(DomainError.PremiumRequired),
                repository.setLabel(ReactionType.Emoji("❤"), "Ideas"),
            )
        }
}
