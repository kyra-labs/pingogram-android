package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatReactions
import dev.kyralabs.pingogram.core.domain.model.ChatSettings
import dev.kyralabs.pingogram.core.domain.model.GroupKind
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibChatSettingsRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibChatSettingsRepository(client, backgroundScope)

    private fun answerChannel(
        reactions: TdApi.ChatAvailableReactions = TdApi.ChatAvailableReactionsAll(11),
        linkedChatId: Long = 0,
    ) {
        val chat =
            TdApiFixtures.chat(id = CHAT, type = TdApi.ChatTypeSupergroup(SUPERGROUP, true)).also {
                it.availableReactions = reactions
            }
        client.on(TdApi.GetChat::class, TdResult.Success(chat))
        val supergroup =
            TdApiFixtures.supergroup(id = SUPERGROUP, isChannel = true, status = TdApiFixtures.creatorStatus()).also {
                it.signMessages = true
            }
        client.on(TdApi.GetSupergroup::class, TdResult.Success(supergroup))
        client.on(
            TdApi.GetSupergroupFullInfo::class,
            TdResult.Success(TdApi.SupergroupFullInfo().also { it.linkedChatId = linkedChatId }),
        )
    }

    @Test
    fun `reads a channel's settings`() =
        runTest {
            answerChannel(
                reactions = TdApi.ChatAvailableReactionsSome(arrayOf(TdApi.ReactionTypeEmoji("👍")), 3),
                linkedChatId = 77,
            )

            assertEquals(
                Outcome.Success(
                    ChatSettings(
                        kind = GroupKind.Channel,
                        myRole = MemberRole.Owner(isMember = true),
                        reactions = ChatReactions.Some(persistentListOf(ReactionType.Emoji("👍")), 3),
                        hasProtectedContent = false,
                        signMessages = true,
                        linkedChatId = 77,
                    ),
                ),
                repository().settings(CHAT).first(),
            )
        }

    @Test
    fun `follows TDLib's changes while collected`() =
        runTest {
            answerChannel()
            val seen = mutableListOf<Outcome<ChatSettings>>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { repository().settings(CHAT).toList(seen) }
            runCurrent()

            client.emit(TdApi.UpdateChatHasProtectedContent(CHAT, true))
            client.emit(TdApi.UpdateChatAvailableReactions(CHAT, TdApi.ChatAvailableReactionsSome(emptyArray(), 0)))
            runCurrent()

            val last = (seen.last() as Outcome.Success).value
            assertTrue(last.hasProtectedContent)
            assertEquals(ChatReactions.Some(persistentListOf(), 0), last.reactions)
        }

    @Test
    fun `a chat that is no group is not found`() =
        runTest {
            client.on(TdApi.GetChat::class, TdResult.Success(TdApiFixtures.chat(id = CHAT)))

            assertEquals(Outcome.Failure(DomainError.NotFound), repository().settings(CHAT).first())
        }

    @Test
    fun `keeps the emoji Telegram offers as reactions`() =
        runTest {
            val repository = repository()

            client.emit(TdApi.UpdateActiveEmojiReactions(arrayOf("👍", "❤")))
            runCurrent()

            assertEquals(listOf("👍", "❤"), repository.activeReactions.first())
        }

    @Test
    fun `sets reactions, keeping the paid reaction the chat has`() =
        runTest {
            client.on(TdApi.SetChatAvailableReactions::class, TdResult.Success(TdApi.Ok()))

            repository().setReactions(
                CHAT,
                ChatReactions.Some(persistentListOf(ReactionType.Emoji("👍"), ReactionType.Paid), 5),
            )

            val sent = client.sentOf<TdApi.SetChatAvailableReactions>().single().availableReactions
            val some = sent as TdApi.ChatAvailableReactionsSome
            assertEquals(5, some.maxReactionCount)
            assertEquals("👍", (some.reactions[0] as TdApi.ReactionTypeEmoji).emoji)
            assertTrue(some.reactions[1] is TdApi.ReactionTypePaid)
        }

    @Test
    fun `signatures and topics go to the chat's supergroup`() =
        runTest {
            answerChannel()
            client.on(TdApi.ToggleSupergroupSignMessages::class, TdResult.Success(TdApi.Ok()))
            client.on(TdApi.ToggleSupergroupIsForum::class, TdResult.Success(TdApi.Ok()))
            val repository = repository()

            assertEquals(Outcome.Success(Unit), repository.setSignMessages(CHAT, sign = false, showSender = true))
            assertEquals(Outcome.Success(Unit), repository.setForum(CHAT, isForum = true, hasTabs = true))

            val sign = client.sentOf<TdApi.ToggleSupergroupSignMessages>().single()
            // Without signatures there is no author to show.
            assertEquals(
                Triple(SUPERGROUP, false, false),
                Triple(sign.supergroupId, sign.signMessages, sign.showMessageSender),
            )
            val forum = client.sentOf<TdApi.ToggleSupergroupIsForum>().single()
            assertEquals(Triple(SUPERGROUP, true, true), Triple(forum.supergroupId, forum.isForum, forum.hasForumTabs))
        }

    @Test
    fun `lists discussion groups, links one and upgrades a basic group`() =
        runTest {
            client.on(TdApi.GetSuitableDiscussionChats::class, TdResult.Success(TdApi.Chats(2, longArrayOf(5, 6))))
            client.on(TdApi.SetChatDiscussionGroup::class, TdResult.Success(TdApi.Ok()))
            client.on(
                TdApi.UpgradeBasicGroupChatToSupergroupChat::class,
                TdResult.Success(TdApiFixtures.chat(id = 900, type = TdApi.ChatTypeSupergroup(9, false))),
            )
            val repository = repository()

            assertEquals(Outcome.Success(listOf(5L, 6L)), repository.discussionGroupCandidates())
            assertEquals(Outcome.Success(Unit), repository.setDiscussionGroup(CHAT, 6))
            assertEquals(Outcome.Success(900L), repository.upgradeToSupergroup(CHAT))
            assertEquals(6, client.sentOf<TdApi.SetChatDiscussionGroup>().single().discussionChatId)
        }

    private companion object {
        const val CHAT = -100_123L
        const val SUPERGROUP = 123L
    }
}
