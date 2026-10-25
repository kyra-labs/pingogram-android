package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddedReaction
import dev.kyralabs.pingogram.core.domain.model.AddedReactionsPage
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageViewer
import dev.kyralabs.pingogram.core.domain.model.Reaction
import dev.kyralabs.pingogram.core.domain.model.ReactionType
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import dev.kyralabs.pingogram.feature.chat.ui.ReactionLabel
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [MessageInteractionsViewModel]: opening at the viewers or the reactions, the tabs and their counts, each tab loaded
 * only once it is selected, paging the reactions, the rows' names and times, failures (flood wait, network, the
 * viewers no longer kept, unauthorized) with "Try again", opening a profile, closing, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageInteractionsViewModelTest {
    private val repository = FakeMessageInteractionRepository()
    private val users = FakeUserRepository()
    private val chats = FakeChatRepository()
    private val clock = FakeWallClock(NOW)
    private val savedState = SavedStateHandle()

    private val heart = ReactionType.Emoji("❤")
    private val thumbsUp = ReactionType.Emoji("👍")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        users.setUsers(
            user(ADA, firstName = "Ada", lastName = "Lovelace"),
            user(GRACE, firstName = "Grace", lastName = "Hopper"),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose sheet is collected, as by the screen. */
    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): MessageInteractionsViewModel {
        val viewModel =
            MessageInteractionsViewModel(handle, repository, users, chats, clock, FakeTimePatterns()) { Locale.US }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }
        runCurrent()
        return viewModel
    }

    private fun request(
        canGetViewers: Boolean = true,
        reactions: List<Reaction> = listOf(Reaction(heart, 2, isChosen = true), Reaction(thumbsUp, 1, false)),
        opensReactions: Boolean = false,
    ) = MessageInteractionsRequest(
        chatId = GROUP_CHAT,
        messageId = MESSAGE,
        reactionsMessageId = REACTIONS_MESSAGE,
        canGetViewers = canGetViewers,
        reactions = reactions.toImmutableList(),
        opensReactions = opensReactions,
    )

    private fun TestScope.open(
        viewModel: MessageInteractionsViewModel,
        request: MessageInteractionsRequest = request(),
    ) {
        viewModel.open(request)
        runCurrent()
    }

    private fun TestScope.send(
        viewModel: MessageInteractionsViewModel,
        event: InteractionsEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private fun page(
        nextOffset: String,
        vararg reactions: AddedReaction,
    ) = Outcome.Success(AddedReactionsPage(totalCount = 10, reactions = persistentListOf(*reactions), nextOffset))

    private fun added(
        userId: Long,
        type: ReactionType = heart,
        secondsAgo: Long = 60,
    ) = AddedReaction(type, MessageSender.User(userId), isOutgoing = false, date = NOW.epochSecond - secondsAgo)

    private val MessageInteractionsViewModel.rows
        get() = (sheet.value?.content as InteractionsContent.Rows).rows

    @Test
    fun `closed at first, asking nothing`() =
        runTest {
            val viewModel = viewModel()

            assertNull(viewModel.sheet.value)
            assertEquals(emptyList<String>(), repository.requests)
        }

    @Test
    fun `a request with nothing to list opens nothing`() =
        runTest {
            val viewModel = viewModel()

            open(viewModel, request(canGetViewers = false, reactions = emptyList()))

            assertNull(viewModel.sheet.value)
        }

    @Nested
    inner class Viewers {
        @Test
        fun `Seen by opens at the viewers and loads only them, latest first, with when`() =
            runTest {
                repository.onViewers = {
                    Outcome.Success(
                        persistentListOf(
                            MessageViewer(ADA, NOW.epochSecond - 30),
                            MessageViewer(GRACE, NOW.minus(Duration.ofDays(1)).epochSecond),
                        ),
                    )
                }
                val viewModel = viewModel()

                open(viewModel)

                assertEquals(InteractionsTab.Seen, viewModel.sheet.value?.selected)
                assertEquals(listOf("viewers:$GROUP_CHAT:$MESSAGE"), repository.requests)
                assertEquals(
                    listOf(UiText.Plain("Ada Lovelace"), UiText.Plain("Grace Hopper")),
                    viewModel.rows.map { it.name },
                )
                assertEquals(
                    listOf(
                        UiText.resource(R.string.interactions_seen_today, "9:41 AM"),
                        UiText.resource(R.string.interactions_seen_yesterday, "9:41 AM"),
                    ),
                    viewModel.rows.map { it.time },
                )
                assertEquals(listOf(null, null), viewModel.rows.map { it.reaction })
            }

        @Test
        fun `an older view says its day`() =
            runTest {
                repository.onViewers = {
                    Outcome.Success(persistentListOf(MessageViewer(ADA, NOW.minus(Duration.ofDays(3)).epochSecond)))
                }
                val viewModel = viewModel()

                open(viewModel)

                assertEquals(
                    UiText.resource(R.string.interactions_seen_on, "Oct 15", "9:41 AM"),
                    viewModel.rows.single().time,
                )
            }

        @Test
        fun `nobody yet is the empty state`() =
            runTest {
                val viewModel = viewModel()

                open(viewModel)

                assertEquals(InteractionsContent.Empty, viewModel.sheet.value?.content)
            }

        @Test
        fun `the viewers' tab counts them once loaded`() =
            runTest {
                val loaded = CompletableDeferred<Unit>()
                repository.onViewers = {
                    loaded.await()
                    Outcome.Success(persistentListOf(MessageViewer(ADA, NOW.epochSecond)))
                }
                val viewModel = viewModel()
                open(viewModel)
                assertEquals(InteractionsContent.Loading, viewModel.sheet.value?.content)
                assertNull(
                    viewModel.sheet.value
                        ?.tabs
                        ?.first()
                        ?.count,
                )

                loaded.complete(Unit)
                runCurrent()

                assertEquals(
                    1,
                    viewModel.sheet.value
                        ?.tabs
                        ?.first()
                        ?.count,
                )
            }

        @Test
        fun `a member TDLib does not know is looked up, one nobody knows is a deleted account`() =
            runTest {
                users.setUsers()
                val looked = mutableListOf<Long>()
                val ada = user(ADA, firstName = "Ada")
                repository.onViewers = {
                    Outcome.Success(
                        persistentListOf(MessageViewer(ADA, NOW.epochSecond), MessageViewer(9, NOW.epochSecond)),
                    )
                }
                val lookups =
                    object : UserRepository by users {
                        override suspend fun getUser(id: Long): Outcome<User> {
                            looked += id
                            return if (id == ADA) Outcome.Success(ada) else Outcome.Failure(DomainError.NotFound)
                        }
                    }
                val viewModel =
                    MessageInteractionsViewModel(savedState, repository, lookups, chats, clock, FakeTimePatterns()) {
                        Locale.US
                    }
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.sheet.collect {} }

                open(viewModel)

                assertEquals(listOf(ADA, 9L), looked)
                assertEquals(
                    listOf(UiText.Plain("Ada"), UiText.resource(UiR.string.user_deleted_account)),
                    viewModel.rows.map { it.name },
                )
                assertEquals(UiR.drawable.ic_person_filled, viewModel.rows[1].placeholderIcon)
            }

        @Test
        fun `viewers Telegram no longer keeps say so, and Try again asks again`() =
            runTest {
                repository.onViewers = { Outcome.Failure(DomainError.MessageViewersUnavailable) }
                val viewModel = viewModel()
                open(viewModel)
                assertEquals(
                    InteractionsContent.Error(DomainError.MessageViewersUnavailable),
                    viewModel.sheet.value?.content,
                )

                repository.onViewers = { Outcome.Success(persistentListOf(MessageViewer(ADA, NOW.epochSecond))) }
                send(viewModel, InteractionsEvent.Retry)

                assertEquals(1, viewModel.rows.size)
                assertEquals(2, repository.requests.size)
            }

        @Test
        fun `a flood wait and an unauthorized session fail the tab`() =
            runTest {
                repository.onViewers = { Outcome.Failure(DomainError.FloodWait(12)) }
                val viewModel = viewModel()
                open(viewModel)
                assertEquals(InteractionsContent.Error(DomainError.FloodWait(12)), viewModel.sheet.value?.content)

                repository.onViewers = { Outcome.Failure(DomainError.Unauthorized) }
                send(viewModel, InteractionsEvent.Retry)

                assertEquals(InteractionsContent.Error(DomainError.Unauthorized), viewModel.sheet.value?.content)
            }
    }

    @Nested
    inner class Reactions {
        @Test
        fun `Reactions opens at every reaction, loading none of the viewers`() =
            runTest {
                repository.onReactions = { _, _ -> page("", added(ADA), added(GRACE, thumbsUp)) }
                val viewModel = viewModel()

                open(viewModel, request(opensReactions = true))

                assertEquals(InteractionsTab.AllReactions, viewModel.sheet.value?.selected)
                assertEquals(listOf("reactions:$GROUP_CHAT:$REACTIONS_MESSAGE:all:"), repository.requests)
                assertEquals(
                    listOf(ReactionLabel.Emoji("❤"), ReactionLabel.Emoji("👍")),
                    viewModel.rows.map { it.reaction },
                )
                assertEquals(
                    UiText.resource(R.string.interactions_reacted_today, "9:40 AM"),
                    viewModel.rows.first().time,
                )
            }

        @Test
        fun `the tabs are the viewers, every reaction and each kind with their counts, without the paid one`() =
            runTest {
                val viewModel = viewModel()

                open(
                    viewModel,
                    request(
                        reactions =
                            listOf(
                                Reaction(heart, 2, isChosen = true),
                                Reaction(ReactionType.Paid, 5, isChosen = false),
                                Reaction(ReactionType.CustomEmoji(7), 1, isChosen = false),
                            ),
                    ),
                )

                assertEquals(
                    listOf(
                        InteractionsTabItem(InteractionsTab.Seen, 0),
                        InteractionsTabItem(InteractionsTab.AllReactions, 3),
                        InteractionsTabItem(InteractionsTab.OneReaction(ReactionLabel.Emoji("❤")), 2),
                        InteractionsTabItem(InteractionsTab.OneReaction(ReactionLabel.CustomEmoji(7)), 1),
                    ),
                    viewModel.sheet.value?.tabs,
                )
            }

        @Test
        fun `without the viewers there is no Seen tab`() =
            runTest {
                val viewModel = viewModel()

                open(viewModel, request(canGetViewers = false))

                assertEquals(
                    InteractionsTab.AllReactions,
                    viewModel.sheet.value
                        ?.tabs
                        ?.first()
                        ?.tab,
                )
                assertEquals(InteractionsTab.AllReactions, viewModel.sheet.value?.selected)
            }

        @Test
        fun `a kind's tab loads that kind once, and shows no reaction on its rows`() =
            runTest {
                repository.onReactions = { type, _ -> page("", added(ADA, type ?: heart)) }
                val viewModel = viewModel()
                open(viewModel)

                send(viewModel, InteractionsEvent.SelectTab(InteractionsTab.OneReaction(ReactionLabel.Emoji("👍"))))
                send(viewModel, InteractionsEvent.SelectTab(InteractionsTab.Seen))
                send(viewModel, InteractionsEvent.SelectTab(InteractionsTab.OneReaction(ReactionLabel.Emoji("👍"))))

                assertEquals(
                    listOf(
                        "viewers:$GROUP_CHAT:$MESSAGE",
                        "reactions:$GROUP_CHAT:$REACTIONS_MESSAGE:Emoji(emoji=👍):",
                    ),
                    repository.requests,
                )
                assertEquals(listOf(null), viewModel.rows.map { it.reaction })
            }

        @Test
        fun `a tab the request has not is not selected`() =
            runTest {
                val viewModel = viewModel()
                open(viewModel)

                send(viewModel, InteractionsEvent.SelectTab(InteractionsTab.OneReaction(ReactionLabel.Emoji("🎉"))))

                assertEquals(InteractionsTab.Seen, viewModel.sheet.value?.selected)
            }

        @Test
        fun `the next page loads from where the last ended, until there is none`() =
            runTest {
                repository.onReactions = { _, offset ->
                    if (offset.isEmpty()) page("p2", added(ADA)) else page("", added(GRACE, thumbsUp), added(ADA))
                }
                val viewModel = viewModel()
                open(viewModel, request(opensReactions = true))
                assertEquals(
                    MoreInteractions.Available,
                    (viewModel.sheet.value?.content as InteractionsContent.Rows).more,
                )

                send(viewModel, InteractionsEvent.LoadMore)

                assertEquals(
                    listOf(
                        "reactions:$GROUP_CHAT:$REACTIONS_MESSAGE:all:",
                        "reactions:$GROUP_CHAT:$REACTIONS_MESSAGE:all:p2",
                    ),
                    repository.requests,
                )
                // Ada's heart is in both pages once.
                assertEquals(listOf("u$ADA:emoji:❤", "u$GRACE:emoji:👍"), viewModel.rows.map { it.key })
                assertEquals(MoreInteractions.None, (viewModel.sheet.value?.content as InteractionsContent.Rows).more)

                send(viewModel, InteractionsEvent.LoadMore)

                assertEquals(2, repository.requests.size)
            }

        @Test
        fun `a page on its way is asked for once`() =
            runTest {
                val second = CompletableDeferred<Unit>()
                repository.onReactions = { _, offset ->
                    if (offset.isNotEmpty()) second.await()
                    page("p2", added(ADA))
                }
                val viewModel = viewModel()
                open(viewModel, request(opensReactions = true))

                send(viewModel, InteractionsEvent.LoadMore)
                assertEquals(
                    MoreInteractions.Loading,
                    (viewModel.sheet.value?.content as InteractionsContent.Rows).more,
                )
                send(viewModel, InteractionsEvent.LoadMore)

                assertEquals(2, repository.requests.size)
                second.complete(Unit)
            }

        @Test
        fun `a failed next page shows below the rows, and Try again asks again`() =
            runTest {
                repository.onReactions = { _, offset ->
                    if (offset.isEmpty()) page("p2", added(ADA)) else Outcome.Failure(DomainError.Network)
                }
                val viewModel = viewModel()
                open(viewModel, request(opensReactions = true))

                send(viewModel, InteractionsEvent.LoadMore)
                assertEquals(
                    MoreInteractions.Failed(DomainError.Network),
                    (viewModel.sheet.value?.content as InteractionsContent.Rows).more,
                )

                repository.onReactions = { _, _ -> page("", added(GRACE)) }
                send(viewModel, InteractionsEvent.Retry)

                assertEquals(listOf("u$ADA:emoji:❤", "u$GRACE:emoji:❤"), viewModel.rows.map { it.key })
                assertEquals(3, repository.requests.size)
            }

        @Test
        fun `a reaction sent as a channel is named by the channel`() =
            runTest {
                val channel = chat(id = CHANNEL, type = ChatType.Supergroup(5, isChannel = true), title = "News")
                chats.setChats(channel)
                repository.onReactions = { _, _ ->
                    page("", AddedReaction(heart, MessageSender.Chat(CHANNEL), isOutgoing = false, date = 0))
                }
                val viewModel = viewModel()

                open(viewModel, request(opensReactions = true))

                val row = viewModel.rows.single()
                assertEquals(UiText.Plain("News"), row.name)
                assertEquals(CHANNEL, row.avatarId)
                // Telegram gave no date.
                assertNull(row.time)
                assertEquals(listOf(CHANNEL), chats.getChatRequests)
            }
    }

    @Nested
    inner class Leaving {
        @Test
        fun `a row opens the user's profile and closes the sheet`() =
            runTest {
                val viewModel = viewModel()
                val effects = mutableListOf<InteractionsEffect>()
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
                open(viewModel)

                send(viewModel, InteractionsEvent.Open(MessageSender.User(ADA)))
                open(viewModel)
                send(viewModel, InteractionsEvent.Open(MessageSender.Chat(CHANNEL)))

                assertNull(viewModel.sheet.value)
                assertEquals(
                    listOf(InteractionsEffect.OpenProfile(ADA), InteractionsEffect.OpenChat(CHANNEL)),
                    effects,
                )
            }

        @Test
        fun `dismissing closes the sheet and drops what was on its way`() =
            runTest {
                val answer = CompletableDeferred<Unit>()
                repository.onViewers = {
                    answer.await()
                    Outcome.Success(persistentListOf(MessageViewer(ADA, NOW.epochSecond)))
                }
                val viewModel = viewModel()
                open(viewModel)

                send(viewModel, InteractionsEvent.Dismiss)
                answer.complete(Unit)
                runCurrent()

                assertNull(viewModel.sheet.value)
                assertEquals(null, savedState.get<Long>("interactions.chat"))
            }

        @Test
        fun `after process death the sheet opens again at its tab, which loads again`() =
            runTest {
                repository.onReactions = { _, _ -> page("", added(ADA)) }
                val first = viewModel()
                open(first)
                send(first, InteractionsEvent.SelectTab(InteractionsTab.OneReaction(ReactionLabel.Emoji("❤"))))
                repository.requests.clear()

                val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))
                runCurrent()

                assertEquals(InteractionsTab.OneReaction(ReactionLabel.Emoji("❤")), restored.sheet.value?.selected)
                assertEquals(
                    4,
                    restored.sheet.value
                        ?.tabs
                        ?.size,
                )
                assertEquals(listOf("reactions:$GROUP_CHAT:$REACTIONS_MESSAGE:Emoji(emoji=❤):"), repository.requests)
                assertEquals(1, restored.rows.size)
            }
    }

    private companion object {
        const val MESSAGE = 42L
        const val REACTIONS_MESSAGE = 43L
        const val CHANNEL = -1_000_000_000_500L
    }
}
