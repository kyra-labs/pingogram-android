package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatSearchCursor
import dev.kyralabs.pingogram.core.domain.model.ChatSearchPage
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.GroupMember
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.ui.text.MatchHighlight
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GRACE
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.GROUP_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.history
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.search.ChatSearch
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeChatSearchRepository.Companion.found
import dev.kyralabs.pingogram.feature.chat.ui.search.FakeGroupInfoRepository.Companion.page
import dev.kyralabs.pingogram.feature.chat.ui.search.SearchResults
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * The search in the conversation of [ChatViewModel] (`REQUIREMENTS.md` §5.9): opening and closing it, searching what
 * is typed after a pause, the newest search winning, moving through the results and their pages, the match in the
 * result's bubble, the sender filter, the jump to a day, failures, and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSearchTest : ChatViewModelHarness() {
    private val messagesOfAda = (1L..9L).map { message(it) }

    /**
     * The view model of a chat of [type] ([id]) with messages 1–9 (of [topic], if not null), its search's state and
     * sheet collected, as the screen does.
     */
    private fun TestScope.searching(
        type: ChatType = ChatType.Private(ADA),
        handle: SavedStateHandle = savedState,
        topic: MessageTopic? = null,
        id: Long = if (type is ChatType.Private) ADA else GROUP_CHAT,
    ): ChatViewModel {
        chats.setChats(chat(id = id, type = type))
        messages.setHistory(id, history(*messagesOfAda.map { it.copy(chatId = id) }.toTypedArray()))
        val viewModel = viewModel(id, handle, topic = topic)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.searchState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.searchSenderSheet.collect {} }
        runCurrent()
        return viewModel
    }

    /** Replaces the search field's text, as typing does, and lets the view model see it. */
    private fun TestScope.type(
        viewModel: ChatViewModel,
        text: String,
    ) {
        viewModel.searchQuery.edit { replace(0, length, text) }
        Snapshot.sendApplyNotifications()
        runCurrent()
    }

    /** Waits as long as a query waits before it is searched. */
    private fun TestScope.pause() {
        advanceTimeBy(ChatSearch.DEBOUNCE_MILLIS + 1)
        runCurrent()
    }

    private fun TestScope.typeAndWait(
        viewModel: ChatViewModel,
        text: String,
    ) {
        type(viewModel, text)
        pause()
    }

    private val ChatViewModel.search get() = searchState.value

    private val ChatViewModel.results get() = search?.results

    private fun found(
        position: Int,
        total: Int,
        hasOlder: Boolean,
        hasNewer: Boolean,
    ) = SearchResults.Found(position, total, isTotalApproximate = false, hasOlder = hasOlder, hasNewer = hasNewer)

    @Test
    fun `search is closed at first, and opens without anything to search`() =
        runViewModelTest {
            val viewModel = searching()
            assertNull(viewModel.search)

            send(viewModel, ChatSearchEvent.Open)

            assertEquals(SearchResults.Idle, viewModel.results)
            assertFalse(viewModel.search!!.canPickSender)
            assertTrue(chatSearch.searches.isEmpty())
        }

    @Test
    fun `searches the query once it has not changed for a moment, and shows the newest result in the list`() =
        runViewModelTest {
            chatSearch.answer = { _, _ -> found(9, 6, 2) }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)

            type(viewModel, "ca")
            advanceTimeBy(ChatSearch.DEBOUNCE_MILLIS / 2)
            type(viewModel, "cats ")
            advanceTimeBy(ChatSearch.DEBOUNCE_MILLIS - 1)
            runCurrent()
            assertTrue(chatSearch.searches.isEmpty())
            pause()

            val search = chatSearch.searches.single()
            assertEquals(ADA, search.chatId)
            assertEquals("cats", search.query.text)
            assertNull(search.cursor)
            assertEquals(found(1, 3, hasOlder = true, hasNewer = false), viewModel.results)
            assertEquals(AnchorTarget.Message(9), viewModel.anchor.target)
        }

    @Test
    fun `a new query cancels the search still running, so the newest wins`() =
        runViewModelTest {
            val first = CompletableDeferred<Outcome<ChatSearchPage>>()
            chatSearch.answer = { query, _ -> if (query.text == "cat") first.await() else found(6) }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)

            typeAndWait(viewModel, "cat")
            assertEquals(SearchResults.Searching, viewModel.results)
            typeAndWait(viewModel, "dog")
            first.complete(found(9))
            runCurrent()

            assertEquals(listOf("cat", "dog"), chatSearch.searches.map { it.query.text })
            assertEquals(found(1, 1, hasOlder = false, hasNewer = false), viewModel.results)
            assertEquals(AnchorTarget.Message(6), viewModel.anchor.target)
        }

    @Test
    fun `up and down move through the results, showing each in the list`() =
        runViewModelTest {
            chatSearch.answer = { _, _ -> found(9, 6, 2) }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)
            typeAndWait(viewModel, "cats")

            send(viewModel, ChatSearchEvent.Older)
            assertEquals(found(2, 3, hasOlder = true, hasNewer = true), viewModel.results)
            assertEquals(AnchorTarget.Message(6), viewModel.anchor.target)
            send(viewModel, ChatSearchEvent.Older, ChatSearchEvent.Older)
            assertEquals(found(3, 3, hasOlder = false, hasNewer = true), viewModel.results)
            assertEquals(AnchorTarget.Message(2), viewModel.anchor.target)

            send(viewModel, ChatSearchEvent.Newer)
            assertEquals(found(2, 3, hasOlder = true, hasNewer = true), viewModel.results)
            assertEquals(AnchorTarget.Message(6), viewModel.anchor.target)
        }

    @Test
    fun `the next page loads near the last loaded result, and going past it shows its first`() =
        runViewModelTest {
            chatSearch.answer = { _, cursor ->
                if (cursor == null) found(9, 8, total = 4, next = 8) else found(5, 3, total = 4)
            }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)
            typeAndWait(viewModel, "cats")

            send(viewModel, ChatSearchEvent.Older)
            assertEquals(ChatSearchCursor.FromMessage(8), chatSearch.searches.last().cursor)
            assertEquals(found(2, 4, hasOlder = true, hasNewer = true), viewModel.results)
            send(viewModel, ChatSearchEvent.Older)
            assertEquals(found(3, 4, hasOlder = true, hasNewer = true), viewModel.results)
            assertEquals(AnchorTarget.Message(5), viewModel.anchor.target)
            assertEquals(2, chatSearch.searches.size)
        }

    @Test
    fun `going past the last loaded result before its page came shows its first once it comes`() =
        runViewModelTest {
            val nextPage = CompletableDeferred<Outcome<ChatSearchPage>>()
            chatSearch.answer =
                { _, cursor -> if (cursor == null) found(9, next = 9, total = null) else nextPage.await() }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)
            typeAndWait(viewModel, "cats")
            assertEquals(
                SearchResults.Found(1, 1, isTotalApproximate = true, hasOlder = true, hasNewer = false),
                viewModel.results,
            )

            send(viewModel, ChatSearchEvent.Older)
            assertTrue((viewModel.results as SearchResults.Found).isLoadingOlder)
            nextPage.complete(found(4, total = null))
            runCurrent()

            assertEquals(
                SearchResults.Found(2, 2, isTotalApproximate = false, hasOlder = false, hasNewer = true),
                viewModel.results,
            )
            assertEquals(AnchorTarget.Message(4), viewModel.anchor.target)
        }

    @Test
    fun `a query that finds nothing says so, and moves nothing`() =
        runViewModelTest {
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)
            val anchor = viewModel.anchor

            typeAndWait(viewModel, "qwxz")
            send(viewModel, ChatSearchEvent.Older)

            assertEquals(SearchResults.NoResults, viewModel.results)
            assertEquals(anchor, viewModel.anchor)
        }

    @Test
    fun `a failed search says so with a snackbar, and Try again searches again`() =
        runViewModelTest {
            for (error in listOf(DomainError.FloodWait(12), DomainError.Network, DomainError.Unauthorized)) {
                chatSearch.searches.clear()
                chatSearch.answer = { _, _ -> Outcome.Failure(error) }
                val viewModel = searching(handle = SavedStateHandle())
                send(viewModel, ChatSearchEvent.Open)

                viewModel.effects.test {
                    typeAndWait(viewModel, "cats")
                    assertEquals(ChatEffect.ShowError(error), awaitItem())
                    assertEquals(SearchResults.Failed(error), viewModel.results)

                    chatSearch.answer = { _, _ -> found(3) }
                    send(viewModel, ChatSearchEvent.Retry)
                    assertEquals(found(1, 1, hasOlder = false, hasNewer = false), viewModel.results)
                    assertEquals(2, chatSearch.searches.size)
                    cancelAndIgnoreRemainingEvents()
                }
                clear(viewModel)
            }
        }

    @Test
    fun `a next page that fails is a snackbar, and the results stay`() =
        runViewModelTest {
            chatSearch.answer = { _, cursor ->
                if (cursor == null) found(9, total = 2, next = 9) else Outcome.Failure(DomainError.Network)
            }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)
            typeAndWait(viewModel, "cats")

            viewModel.effects.test {
                send(viewModel, ChatSearchEvent.Older)
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
            }
            assertEquals(found(1, 2, hasOlder = true, hasNewer = false), viewModel.results)
        }

    @Test
    fun `the result shown marks the query's words in its bubble, and no other bubble`() =
        runViewModelTest {
            chatSearch.answer = { _, _ -> found(9, 6) }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)
            typeAndWait(viewModel, "Message")

            assertEquals(MatchHighlight.of("Message"), viewModel.bubble(9).searchMatch)
            assertEquals(listOf(9L), viewModel.bubbles.filter { it.searchMatch != null }.map { it.id })
            send(viewModel, ChatSearchEvent.Older)
            assertEquals(listOf(6L), viewModel.bubbles.filter { it.searchMatch != null }.map { it.id })

            send(viewModel, ChatSearchEvent.Close)
            assertTrue(viewModel.bubbles.none { it.searchMatch != null })
        }

    @Test
    fun `closing forgets the query, the sender and the results`() =
        runViewModelTest {
            chatSearch.answer = { _, _ -> found(9) }
            val viewModel = searching(ChatType.BasicGroup(ChatFixtures.BASIC_GROUP_ID))
            send(viewModel, ChatSearchEvent.Open, ChatSearchEvent.PickSender(MessageSender.User(GRACE)))
            typeAndWait(viewModel, "cats")

            send(viewModel, ChatSearchEvent.Close)

            assertNull(viewModel.search)
            assertEquals("", viewModel.searchQuery.text.toString())
            assertEquals(false, savedState[ChatSearch.KEY_OPEN])
            assertNull(savedState[ChatSearch.KEY_SENDER])
            send(viewModel, ChatSearchEvent.Open)
            assertEquals(SearchResults.Idle, viewModel.results)
            assertNull(viewModel.search?.sender)
        }

    @Test
    fun `in a group, a member picked in the sheet limits the search to their messages, also without words`() =
        runViewModelTest {
            users.setUsers(user(ADA, firstName = "Ada"), user(GRACE, firstName = "Grace", lastName = "Hopper"))
            groupMembers.answer = {
                Outcome.Success(
                    page(
                        GroupMember(MessageSender.User(ADA), MemberRole.Owner(isMember = true)),
                        GroupMember(MessageSender.User(GRACE), MemberRole.Member),
                        GroupMember(MessageSender.User(4), MemberRole.Left),
                    ),
                )
            }
            chatSearch.answer = { _, _ -> found(5) }
            val viewModel = searching(ChatType.BasicGroup(ChatFixtures.BASIC_GROUP_ID))
            send(viewModel, ChatSearchEvent.Open)
            assertTrue(viewModel.search!!.canPickSender)

            send(viewModel, ChatSearchEvent.OpenSenders)
            val options = checkNotNull(viewModel.searchSenderSheet.value?.options)
            assertEquals(listOf(ADA, GRACE), options.map { it.id })
            assertEquals(UiText.resource(R.string.search_sender_owner), options.first().subtitle)
            send(viewModel, ChatSearchEvent.PickSender(MessageSender.User(GRACE)))

            assertNull(viewModel.searchSenderSheet.value)
            val search = chatSearch.searches.single()
            assertEquals("", search.query.text)
            assertEquals(MessageSender.User(GRACE), search.query.sender)
            assertEquals(UiText.Plain("Grace Hopper"), viewModel.search?.sender?.name)
            assertEquals(AnchorTarget.Message(5), viewModel.anchor.target)

            send(viewModel, ChatSearchEvent.ClearSender)
            assertNull(viewModel.search?.sender)
            assertEquals(SearchResults.Idle, viewModel.results)
        }

    @Test
    fun `the members' sheet searches what its field holds after a pause, and offers to try again`() =
        runViewModelTest {
            groupMembers.answer = { Outcome.Failure(DomainError.Network) }
            val viewModel = searching(ChatType.BasicGroup(ChatFixtures.BASIC_GROUP_ID))
            send(viewModel, ChatSearchEvent.Open, ChatSearchEvent.OpenSenders)
            assertEquals(DomainError.Network, viewModel.searchSenderSheet.value?.error)

            groupMembers.answer = { Outcome.Success(page()) }
            send(viewModel, ChatSearchEvent.RetrySenders)
            assertEquals(persistentListOf<Any>(), viewModel.searchSenderSheet.value?.options)
            viewModel.searchSenderQuery.edit { replace(0, length, "gra") }
            Snapshot.sendApplyNotifications()
            runCurrent()
            assertEquals(listOf("", ""), groupMembers.queries)
            pause()

            assertEquals(listOf("", "", "gra"), groupMembers.queries)
        }

    @Test
    fun `private chats and channels have no sender to pick, and a secret chat is searched on the device`() =
        runViewModelTest {
            val private = searching()
            send(private, ChatSearchEvent.Open)
            assertFalse(private.search!!.canPickSender)
            clear(private)

            chats.setChats(channelChat)
            messages.setHistory(channelChat.id, history(message(1, chatId = channelChat.id)))
            val channel = viewModel(channelChat.id, SavedStateHandle())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { channel.searchState.collect {} }
            send(channel, ChatSearchEvent.Open)
            assertFalse(channel.search!!.canPickSender)
            clear(channel)

            val secret = searching(ChatType.Secret(7, ADA), SavedStateHandle())
            send(secret, ChatSearchEvent.Open)
            typeAndWait(secret, "cats")
            assertTrue(
                chatSearch.searches
                    .single()
                    .query.isSecretChat,
            )
        }

    @Test
    fun `a topic's conversation searches the topic`() =
        runViewModelTest {
            forumTopics.setTopics(ChatFixtures.FORUM_CHAT, ChatFixtures.forumTopic(4))
            val viewModel =
                searching(
                    ChatType.Supergroup(ChatFixtures.SUPERGROUP_ID, isChannel = false),
                    topic = MessageTopic.Forum(4),
                    id = ChatFixtures.FORUM_CHAT,
                )
            send(viewModel, ChatSearchEvent.Open)
            typeAndWait(viewModel, "cats")

            assertEquals(
                MessageTopic.Forum(4),
                chatSearch.searches
                    .single()
                    .query.topic,
            )
        }

    @Test
    fun `a picked day shows its first message in the list, and a day before the chat says there is none`() =
        runViewModelTest {
            chatSearch.dayAnswer = { _, _ -> Outcome.Success(4) }
            val viewModel = searching()
            send(viewModel, ChatSearchEvent.Open)

            // 2026-10-01.
            send(viewModel, ChatSearchEvent.JumpToDate(20_727))
            assertEquals(
                Instant.parse("2026-10-01T00:00:00Z") to Instant.parse("2026-10-02T00:00:00Z"),
                chatSearch.days.single(),
            )
            assertEquals(AnchorTarget.Message(4), viewModel.anchor.target)

            chatSearch.dayAnswer = { _, _ -> Outcome.Failure(DomainError.NotFound) }
            viewModel.effects.test {
                send(viewModel, ChatSearchEvent.JumpToDate(10_000))
                assertEquals(ChatEffect.NoMessagesForDay, awaitItem())
                chatSearch.dayAnswer = { _, _ -> Outcome.Failure(DomainError.Network) }
                send(viewModel, ChatSearchEvent.JumpToDate(10_000))
                assertEquals(ChatEffect.ShowError(DomainError.Network), awaitItem())
            }
        }

    @Test
    fun `after process death search opens again at the result it showed, with its query and sender, in place`() =
        runViewModelTest {
            chatSearch.answer = { _, cursor -> if (cursor == null) found(9, 7, total = 3, next = 7) else found(3) }
            val handle =
                SavedStateHandle(
                    mapOf(
                        ChatSearch.KEY_OPEN to true,
                        ChatSearch.KEY_QUERY to "cats",
                        ChatSearch.KEY_SENDER to "u$GRACE",
                        ChatSearch.KEY_CURRENT to 3L,
                    ),
                )
            val viewModel = searching(ChatType.BasicGroup(ChatFixtures.BASIC_GROUP_ID), handle)
            val anchor = viewModel.anchor
            pause()

            assertEquals("cats", viewModel.searchQuery.text.toString())
            assertEquals(
                MessageSender.User(GRACE),
                chatSearch.searches
                    .first()
                    .query.sender,
            )
            assertEquals(found(3, 3, hasOlder = false, hasNewer = true), viewModel.results)
            assertEquals(anchor, viewModel.anchor)
            send(viewModel, ChatSearchEvent.Newer)
            assertEquals(AnchorTarget.Message(7), viewModel.anchor.target)
        }

    @Test
    fun `the query, the sender and the result shown are kept in the saved state`() =
        runViewModelTest {
            chatSearch.answer = { _, _ -> found(9, 6) }
            val viewModel = searching(ChatType.BasicGroup(ChatFixtures.BASIC_GROUP_ID))
            send(viewModel, ChatSearchEvent.Open, ChatSearchEvent.PickSender(MessageSender.User(GRACE)))
            typeAndWait(viewModel, "cats")
            send(viewModel, ChatSearchEvent.Older)

            assertEquals(true, savedState[ChatSearch.KEY_OPEN])
            assertEquals("cats", savedState[ChatSearch.KEY_QUERY])
            assertEquals("u$GRACE", savedState[ChatSearch.KEY_SENDER])
            assertEquals(6L, savedState[ChatSearch.KEY_CURRENT])
        }
}
