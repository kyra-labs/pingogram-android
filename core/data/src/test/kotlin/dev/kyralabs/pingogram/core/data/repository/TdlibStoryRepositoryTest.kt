package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ActiveStories
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.StoryContent
import dev.kyralabs.pingogram.core.domain.model.StoryInfo
import dev.kyralabs.pingogram.core.domain.model.StoryList
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.StoryFixtures.availableReactions
import dev.kyralabs.pingogram.core.testing.StoryFixtures.chatActiveStories
import dev.kyralabs.pingogram.core.testing.StoryFixtures.story
import dev.kyralabs.pingogram.core.testing.StoryFixtures.updateChatActiveStories
import dev.kyralabs.pingogram.core.testing.StoryFixtures.updateStory
import dev.kyralabs.pingogram.core.testing.StoryFixtures.updateStoryDeleted
import dev.kyralabs.pingogram.core.testing.StoryFixtures.videoContent
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Stories: the cache of `updateChatActiveStories` and its order, loading the lists, a chat's active stories and a
 * story with their updates, marking a story viewed, reactions, replies, and errors.
 */
class TdlibStoryRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibStoryRepository(client, backgroundScope)

    @Test
    fun `lists announced chats by order then chat id, both descending, and only those of the list`() =
        runTest {
            val repository = repository()

            client.emit(updateChatActiveStories(chatActiveStories(chatId = 1, order = 5)))
            client.emit(updateChatActiveStories(chatActiveStories(chatId = 2, order = 9)))
            client.emit(updateChatActiveStories(chatActiveStories(chatId = 3, order = 5)))
            client.emit(updateChatActiveStories(chatActiveStories(chatId = 4, list = TdApi.StoryListArchive())))
            client.emit(updateChatActiveStories(chatActiveStories(chatId = 5, list = null, order = 0)))
            client.emit(updateChatActiveStories(chatActiveStories(chatId = 6, storyIds = emptyList())))

            assertEquals(listOf(2L, 3L, 1L), repository.activeStories(StoryList.Main).first().map { it.chatId })
            assertEquals(listOf(4L), repository.activeStories(StoryList.Archive).first().map { it.chatId })
        }

    @Test
    fun `maps a chat's stories oldest first with its read state`() =
        runTest {
            val repository = repository()

            client.emit(
                updateChatActiveStories(chatActiveStories(chatId = 42, maxReadStoryId = 3, storyIds = listOf(5, 3))),
            )

            val stories = repository.activeStories(StoryList.Main).first().single()
            assertEquals(
                ActiveStories(
                    chatId = 42,
                    list = StoryList.Main,
                    order = 1,
                    maxReadStoryId = 3,
                    stories = persistentListOf(StoryInfo(3, 1_700_000_000), StoryInfo(5, 1_700_000_000)),
                ),
                stories,
            )
            assertTrue(stories.hasUnread)
            assertEquals(1, stories.firstUnreadIndex)
        }

    @Test
    fun `a newer announcement replaces the chat's stories, so seen ones turn read`() =
        runTest {
            val repository = repository()
            client.emit(updateChatActiveStories(chatActiveStories(maxReadStoryId = 0, storyIds = listOf(1, 2))))

            repository.activeStories(StoryList.Main).test {
                assertTrue(awaitItem().single().hasUnread)
                client.emit(updateChatActiveStories(chatActiveStories(maxReadStoryId = 2, storyIds = listOf(1, 2))))
                assertFalse(awaitItem().single().hasUnread)
                client.emit(updateChatActiveStories(chatActiveStories(storyIds = emptyList())))
                assertTrue(awaitItem().isEmpty())
            }
        }

    @Test
    fun `logging out forgets every chat's stories`() =
        runTest {
            val repository = repository()
            client.emit(updateChatActiveStories())

            client.emit(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))

            assertTrue(repository.activeStories(StoryList.Main).first().isEmpty())
        }

    @Test
    fun `loads a list's first chats once per session, and again after a failure`() =
        runTest {
            val repository = repository()
            var answer: TdResult<TdApi.Ok> = TdResult.Failure(TdError(500, "Request aborted"))
            client.on(TdApi.LoadActiveStories::class) { answer }

            assertInstanceOf(Outcome.Failure::class.java, repository.loadActiveStories(StoryList.Main))
            answer = TdResult.Success(TdApi.Ok())
            assertEquals(Outcome.Success(Unit), repository.loadActiveStories(StoryList.Main))
            assertEquals(Outcome.Success(Unit), repository.loadActiveStories(StoryList.Main))

            val sent = client.sentOf<TdApi.LoadActiveStories>()
            assertEquals(2, sent.size)
            assertTrue(sent.all { it.storyList is TdApi.StoryListMain })
        }

    @Test
    fun `loading more reports when every chat is loaded`() =
        runTest {
            val repository = repository()
            client.on(TdApi.LoadActiveStories::class, TdResult.Success(TdApi.Ok()))
            assertEquals(Outcome.Success(true), repository.loadMoreActiveStories(StoryList.Archive))
            assertTrue(client.sentOf<TdApi.LoadActiveStories>().single().storyList is TdApi.StoryListArchive)

            client.on(TdApi.LoadActiveStories::class, TdResult.Failure(TdError(404, "Not Found")))
            assertEquals(Outcome.Success(false), repository.loadMoreActiveStories(StoryList.Archive))
        }

    @Test
    fun `loading fails with a flood wait`() =
        runTest {
            client.on(
                TdApi.LoadActiveStories::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 7")),
            )

            assertEquals(Outcome.Failure(DomainError.FloodWait(7)), repository().loadMoreActiveStories(StoryList.Main))
        }

    @Test
    fun `a chat's active stories come from the cache without asking`() =
        runTest {
            val repository = repository()
            client.emit(updateChatActiveStories(chatActiveStories(chatId = 42, storyIds = listOf(1, 2))))

            val stories = repository.activeStoriesOf(42).first()

            assertEquals(listOf(1, 2), (stories as Outcome.Success).value.stories.map { it.id })
            assertTrue(client.sentOf<TdApi.GetChatActiveStories>().isEmpty())
        }

    @Test
    fun `a chat not announced is asked for, then followed`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetChatActiveStories::class, TdResult.Success(chatActiveStories(chatId = 7, list = null)))

            repository.activeStoriesOf(7).test {
                assertEquals(listOf(1), (awaitItem() as Outcome.Success).value.stories.map { it.id })
                client.emit(
                    updateChatActiveStories(chatActiveStories(chatId = 7, list = null, storyIds = listOf(1, 4))),
                )
                assertEquals(listOf(1, 4), (awaitItem() as Outcome.Success).value.stories.map { it.id })
            }
        }

    @Test
    fun `a chat's stories that cannot be read are an error`() =
        runTest {
            client.on(TdApi.GetChatActiveStories::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(Outcome.Failure(DomainError.Unauthorized), repository().activeStoriesOf(7).first())
        }

    @Test
    fun `maps a story, then follows its updates and deletion`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetStory::class, TdResult.Success(story(id = 3, caption = "Hi")))

            repository.story(42, 3).test {
                val first = (awaitItem() as Outcome.Success).value
                assertEquals(3, first?.id)
                assertEquals(FormattedText("Hi", persistentListOf()), first?.caption)
                assertInstanceOf(StoryContent.Photo::class.java, first?.content)
                assertTrue(first?.canBeReplied == true)
                assertNull(first?.chosenReaction)

                client.emit(updateStory(story(id = 3, chosenReaction = TdApi.ReactionTypeEmoji("❤"))))
                assertEquals(ReactionType.Emoji("❤"), (awaitItem() as Outcome.Success).value?.chosenReaction)
                // Another story's updates are not this one's.
                client.emit(updateStoryDeleted(posterChatId = 42, storyId = 4))
                client.emit(updateStoryDeleted(posterChatId = 42, storyId = 3))
                assertEquals(Outcome.Success(null), awaitItem())
            }
            val sent = client.sentOf<TdApi.GetStory>().single()
            assertEquals(42L to 3, sent.storyPosterChatId to sent.storyId)
            assertFalse(sent.onlyLocal)
        }

    @Test
    fun `maps a video story's length and an unsupported one`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetStory::class) { request ->
                val content =
                    if (request.storyId ==
                        1
                    ) {
                        videoContent(duration = 12.5)
                    } else {
                        TdApi.StoryContentUnsupported()
                    }
                TdResult.Success(story(id = request.storyId, content = content))
            }

            val video = (repository.story(42, 1).first() as Outcome.Success).value?.content
            assertEquals(12_500L, (video as StoryContent.Video).durationMillis)
            assertEquals(31, video.file.id)
            assertEquals(
                StoryContent.Unsupported,
                (repository.story(42, 2).first() as Outcome.Success).value?.content,
            )
        }

    @Test
    fun `a story that cannot be fetched is an error`() =
        runTest {
            client.on(TdApi.GetStory::class, TdResult.Failure(TdError(404, "Not Found")))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().story(42, 1).first())
        }

    @Test
    fun `opens and closes a story, which marks it viewed`() =
        runTest {
            val repository = repository()
            client.on(TdApi.OpenStory::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.CloseStory::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.openStory(42, 3))
            assertEquals(Outcome.Success(Unit), repository.closeStory(42, 3))

            val opened = client.sentOf<TdApi.OpenStory>().single()
            val closed = client.sentOf<TdApi.CloseStory>().single()
            assertEquals(42L to 3, opened.storyPosterChatId to opened.storyId)
            assertEquals(42L to 3, closed.storyPosterChatId to closed.storyId)
            assertTrue(client.sent.none { it is TdApi.ActivateStoryStealthMode })
        }

    @Test
    fun `opening a story without a connection is a network error`() =
        runTest {
            client.on(TdApi.OpenStory::class) { awaitCancellation() }
            val repository = repository()

            var result: Outcome<Unit>? = null
            backgroundScope.launch { result = repository.openStory(42, 3) }
            runCurrent()
            testScheduler.advanceTimeBy(REQUEST_TIMEOUT.inWholeMilliseconds + 1)
            runCurrent()

            assertEquals(Outcome.Failure(DomainError.Network), result)
        }

    @Test
    fun `offers the emoji reactions that need no Premium`() =
        runTest {
            client.on(
                TdApi.GetStoryAvailableReactions::class,
                TdResult.Success(availableReactions(emoji = listOf("⭐", "❤", "👍"), premiumOnly = 1)),
            )

            assertEquals(
                Outcome.Success(persistentListOf(ReactionType.Emoji("❤"), ReactionType.Emoji("👍"))),
                repository().availableReactions(),
            )
        }

    @Test
    fun `sets and removes a reaction`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SetStoryReaction::class, TdResult.Success(TdApi.Ok()))

            repository.setReaction(42, 3, ReactionType.Emoji("❤"))
            repository.setReaction(42, 3, null)

            val (set, removed) = client.sentOf<TdApi.SetStoryReaction>()
            assertEquals("❤", (set.reactionType as TdApi.ReactionTypeEmoji).emoji)
            assertTrue(set.updateRecentReactions)
            assertNull(removed.reactionType)
        }

    @Test
    fun `a refused reaction is a domain error`() =
        runTest {
            client.on(TdApi.SetStoryReaction::class, TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")))

            assertEquals(
                Outcome.Failure(DomainError.FloodWait(3)),
                repository().setReaction(42, 3, ReactionType.Emoji("❤")),
            )
        }

    @Test
    fun `replies to a story in the poster's private chat`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Success(TdApiFixtures.message()))

            assertEquals(
                Outcome.Success(Unit),
                repository().reply(42, 3, FormattedText("Nice!", persistentListOf())),
            )

            val sent = client.sentOf<TdApi.SendMessage>().single()
            assertEquals(42L, sent.chatId)
            val replyTo = sent.replyTo as TdApi.InputMessageReplyToStory
            assertEquals(42L to 3, replyTo.storyPosterChatId to replyTo.storyId)
            assertEquals("Nice!", (sent.inputMessageContent as TdApi.InputMessageText).text.text)
        }

    @Test
    fun `a refused reply is a domain error`() =
        runTest {
            client.on(TdApi.SendMessage::class, TdResult.Failure(TdError(401, "Unauthorized")))

            assertEquals(
                Outcome.Failure(DomainError.Unauthorized),
                repository().reply(42, 3, FormattedText("Nice!", persistentListOf())),
            )
        }
}
