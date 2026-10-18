package dev.kyralabs.pingogram.feature.chats.ui

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Chat
import dev.kyralabs.pingogram.core.domain.model.ChatType
import dev.kyralabs.pingogram.core.domain.model.FoundMessage
import dev.kyralabs.pingogram.core.domain.model.FoundMessages
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.ME
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.NOW
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.chat
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.message
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.supergroup
import dev.kyralabs.pingogram.feature.chats.ui.ChatListFixtures.user
import dev.kyralabs.pingogram.feature.chats.ui.FakeSearchRepository.Companion.found
import dev.kyralabs.pingogram.feature.chats.ui.SearchViewModel.Companion.DEBOUNCE_MILLIS
import dev.kyralabs.pingogram.feature.chats.ui.SearchViewModel.Companion.KEY_QUERY
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Locale
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * [SearchViewModel] on a [FakeSearchRepository] (`REQUIREMENTS.md` FR-L6): the debounced, cancelling query, the
 * suggestions, the sections and their states, message paging, the rows, opening results, and process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val search = FakeSearchRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val groups = FakeGroupRepository()
    private val clock = FakeWallClock(NOW)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A view model whose state is collected, as by the screen. */
    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): SearchViewModel {
        val viewModel =
            SearchViewModel(
                savedStateHandle = handle,
                searchRepository = search,
                userRepository = users,
                groupRepository = groups,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    /** Types [query] and waits until it is searched. */
    private fun TestScope.searching(
        query: String,
        viewModel: SearchViewModel = viewModel(),
    ): SearchViewModel {
        viewModel.onEvent(SearchEvent.QueryChanged(query))
        advanceTimeBy(DEBOUNCE_MILLIS + 1)
        runCurrent()
        return viewModel
    }

    private fun TestScope.send(
        viewModel: SearchViewModel,
        vararg events: SearchEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** The effects [viewModel] sends from now on, collected as the screen does. */
    private fun TestScope.effectsOf(viewModel: SearchViewModel): List<SearchEffect> {
        val effects = mutableListOf<SearchEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private val SearchViewModel.results get() = uiState.value.content as SearchContent.Results

    private val SearchViewModel.suggestions get() = uiState.value.content as SearchContent.Suggestions

    private fun SearchSection<SearchChatItem>.ids() = (this as SearchSection.Items).items.map { it.id }

    private fun MessageSection.ids() = (first as SearchSection.Items).items.map { it.chatId to it.messageId }

    private fun page(
        vararg messages: FoundMessage,
        next: String? = null,
    ) = Outcome.Success(FoundMessages(messages.toList().toImmutableList(), next, totalCount = null))

    private fun foundMessage(
        chatId: Long,
        id: Long,
        text: String = "Hello Ada",
    ) = FoundMessage(message(chatId, id = id, content = textContent(text)), chat(chatId))

    // The query

    @Test
    fun `a query is searched once typing pauses, and only the last one`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(SearchEvent.QueryChanged("a"))
            advanceTimeBy(DEBOUNCE_MILLIS / 3)
            viewModel.onEvent(SearchEvent.QueryChanged("ad"))
            advanceTimeBy(DEBOUNCE_MILLIS / 3)
            viewModel.onEvent(SearchEvent.QueryChanged("ada "))
            advanceTimeBy(DEBOUNCE_MILLIS - 1)
            runCurrent()

            assertTrue(search.callsOf("contacts").isEmpty())
            advanceTimeBy(2)
            runCurrent()
            assertEquals(listOf("contacts:ada"), search.callsOf("contacts"))
            assertEquals("ada", viewModel.results.query)
        }

    @Test
    fun `a new query cancels the searches of the previous one`() =
        runTest {
            search.onSearchContacts = { awaitCancellation() }
            search.onSearchMessages = { _, _ -> awaitCancellation() }
            val viewModel = searching("ada")

            searching("adam", viewModel)

            assertEquals(listOf("contacts:ada", "messages:ada:null"), search.cancelled)
            assertEquals("adam", viewModel.results.query)
        }

    @Test
    fun `a blank query searches nothing, and shows the suggestions`() =
        runTest {
            val viewModel = searching("   ")

            assertTrue(search.callsOf("contacts").isEmpty())
            assertFalse(viewModel.suggestions.isLoading)
        }

    @Test
    fun `clearing the query shows the suggestions at once`() =
        runTest {
            val viewModel = searching("ada")

            send(viewModel, SearchEvent.ClearQuery)

            assertTrue(viewModel.uiState.value.content is SearchContent.Suggestions)
        }

    @Test
    fun `the query survives process death and is searched again`() =
        runTest {
            val viewModel = viewModel(SavedStateHandle(mapOf(KEY_QUERY to "ada")))
            advanceTimeBy(DEBOUNCE_MILLIS + 1)
            runCurrent()

            assertEquals("ada", viewModel.results.query)
            assertEquals(listOf("contacts:ada"), search.callsOf("contacts"))
        }

    // Suggestions

    @Test
    fun `an empty query shows the top people and the recent chats`() =
        runTest {
            search.topPeople = listOf(chat(4))
            search.recents += listOf(chat(2), chat(3))

            val suggestions = viewModel().suggestions

            assertEquals(listOf(4L), suggestions.topPeople.map { it.id })
            assertEquals(listOf(2L, 3L), suggestions.recents.map { it.id })
            assertFalse(suggestions.isLoading)
            assertFalse(suggestions.isEmpty)
        }

    @Test
    fun `without top people or recent chats there is nothing to suggest`() =
        runTest {
            assertTrue(viewModel().suggestions.isEmpty)
        }

    @Test
    fun `removing a recent chat reads them again without it`() =
        runTest {
            search.recents += listOf(chat(2), chat(3))
            val viewModel = viewModel()

            send(viewModel, SearchEvent.RemoveRecent(2))

            assertEquals("remove:2", search.calls.first { it.startsWith("remove") })
            assertEquals(listOf(3L), viewModel.suggestions.recents.map { it.id })
        }

    @Test
    fun `clearing the recent chats empties them`() =
        runTest {
            search.recents += listOf(chat(2), chat(3))
            val viewModel = viewModel()

            send(viewModel, SearchEvent.ClearRecents)

            assertTrue(viewModel.suggestions.recents.isEmpty())
        }

    @Test
    fun `a recent chat that cannot be removed stays, and a snackbar says why`() =
        runTest {
            search.recents += chat(2)
            search.onChangeRecents = { Outcome.Failure(DomainError.FloodWait(5)) }
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, SearchEvent.RemoveRecent(2))

            assertEquals(listOf(SearchEffect.ShowError(DomainError.FloodWait(5))), effects)
            assertEquals(listOf(2L), viewModel.suggestions.recents.map { it.id })
        }

    // Results

    @Test
    fun `the sections show what each search found`() =
        runTest {
            search.onSearchChats = { flowOf(found(chat(2), chat(3))) }
            search.onSearchContacts = { found(chat(4)) }
            search.onSearchPublicChats = { found(chat(5)) }
            search.onSearchMessages = { _, _ -> page(foundMessage(2, 10), foundMessage(3, 20)) }

            val results = searching("ada").results

            assertEquals(listOf(2L, 3L), results.chats.ids())
            assertEquals(listOf(4L), results.contacts.ids())
            assertEquals(listOf(5L), results.global.ids())
            assertEquals(listOf(2L to 10L, 3L to 20L), results.messages.ids())
            assertEquals(6, results.resultCount)
            assertFalse(results.isEmpty)
        }

    @Test
    fun `contacts among the chats are left out, and public chats among either`() =
        runTest {
            search.onSearchChats = { flowOf(found(chat(2))) }
            search.onSearchContacts = { found(chat(2), chat(4)) }
            search.onSearchPublicChats = { found(chat(2), chat(4), chat(5)) }

            val results = searching("ada").results

            assertEquals(listOf(4L), results.contacts.ids())
            assertEquals(listOf(5L), results.global.ids())
        }

    @Test
    fun `chats on the device show while the server still searches`() =
        runTest {
            val server = CompletableDeferred<Outcome<ImmutableList<Chat>>>()
            search.onSearchChats = {
                flow {
                    emit(found(chat(2)))
                    emit(server.await())
                }
            }
            val viewModel = searching("ada")
            assertEquals(listOf(2L), viewModel.results.chats.ids())

            server.complete(found(chat(2), chat(7)))
            runCurrent()

            assertEquals(listOf(2L, 7L), viewModel.results.chats.ids())
        }

    @Test
    fun `each section loads on its own`() =
        runTest {
            search.onSearchChats = { flowOf(found(chat(2))) }
            search.onSearchContacts = { awaitCancellation() }

            val results = searching("ada").results

            assertEquals(listOf(2L), results.chats.ids())
            assertEquals(SearchSection.Loading, results.contacts)
            assertNull(results.resultCount)
        }

    @Test
    fun `every section empty is no results`() =
        runTest {
            val results = searching("qwxz").results

            assertTrue(results.isEmpty)
            assertEquals(0, results.resultCount)
        }

    @Test
    fun `a failed section shows its error, and retry searches it again, and only it`() =
        runTest {
            var attempts = 0
            search.onSearchPublicChats = {
                attempts++
                if (attempts == 1) Outcome.Failure(DomainError.Network) else found(chat(5))
            }
            val viewModel = searching("ada")
            assertEquals(SearchSection.Failed(DomainError.Network), viewModel.results.global)

            send(viewModel, SearchEvent.Retry)

            assertEquals(listOf(5L), viewModel.results.global.ids())
            assertEquals(2, search.callsOf("public").size)
            assertEquals(1, search.callsOf("contacts").size)
            assertEquals(1, search.callsOf("chats").size)
        }

    @Test
    fun `a flood wait shows on the section it came from`() =
        runTest {
            search.onSearchMessages = { _, _ -> Outcome.Failure(DomainError.FloodWait(30)) }

            val messages = searching("ada").results.messages

            assertEquals(SearchSection.Failed(DomainError.FloodWait(30)), messages.first)
            assertNull(messages.moreError)
        }

    // Message paging

    @Test
    fun `messages page as the list nears the end, one page at a time`() =
        runTest {
            val second = CompletableDeferred<Outcome<FoundMessages>>()
            search.onSearchMessages = { _, offset ->
                if (offset == null) page(foundMessage(2, 10), next = "o1") else second.await()
            }
            val viewModel = searching("ada")
            assertTrue(viewModel.results.messages.canLoadMore)

            send(viewModel, SearchEvent.LoadMoreMessages, SearchEvent.LoadMoreMessages)

            assertTrue(viewModel.results.messages.isLoadingMore)
            assertEquals(listOf("messages:ada:null", "messages:ada:o1"), search.callsOf("messages"))
            second.complete(page(foundMessage(3, 20)))
            runCurrent()
            val messages = viewModel.results.messages
            assertEquals(listOf(2L to 10L, 3L to 20L), messages.ids())
            assertFalse(messages.canLoadMore)
            assertFalse(messages.isLoadingMore)
        }

    @Test
    fun `without a next page nothing more loads`() =
        runTest {
            search.onSearchMessages = { _, _ -> page(foundMessage(2, 10)) }
            val viewModel = searching("ada")

            send(viewModel, SearchEvent.LoadMoreMessages)

            assertEquals(1, search.callsOf("messages").size)
        }

    @Test
    fun `a failed page is not loaded again by itself, only by retry`() =
        runTest {
            var pages = 0
            search.onSearchMessages = { _, offset ->
                when {
                    offset == null -> page(foundMessage(2, 10), next = "o1")
                    pages++ == 0 -> Outcome.Failure(DomainError.Network)
                    else -> page(foundMessage(3, 20))
                }
            }
            val viewModel = searching("ada")
            send(viewModel, SearchEvent.LoadMoreMessages)
            assertEquals(DomainError.Network, viewModel.results.messages.moreError)

            send(viewModel, SearchEvent.LoadMoreMessages)
            assertEquals(2, search.callsOf("messages").size)

            send(viewModel, SearchEvent.Retry)
            assertEquals(3, search.callsOf("messages").size)
            assertEquals(listOf(2L to 10L, 3L to 20L), viewModel.results.messages.ids())
            assertNull(viewModel.results.messages.moreError)
        }

    @Test
    fun `a message found again on a later page is not repeated`() =
        runTest {
            search.onSearchMessages = { _, offset ->
                if (offset == null) {
                    page(foundMessage(2, 10), next = "o1")
                } else {
                    page(foundMessage(2, 10), foundMessage(2, 9))
                }
            }
            val viewModel = searching("ada")

            send(viewModel, SearchEvent.LoadMoreMessages)

            assertEquals(listOf(2L to 10L, 2L to 9L), viewModel.results.messages.ids())
        }

    // Rows

    @Test
    fun `chat rows show the name, badge, online dot and username, as the chat list maps them`() =
        runTest {
            users.setUsers(
                user(2, firstName = "Ada", isVerified = true, status = UserStatus.Online(NOW.epochSecond + 60))
                    .copy(usernames = persistentListOf("ada")),
            )
            search.onSearchChats = { flowOf(found(chat(2, title = "Ada Lovelace"))) }

            val row = (searching("ada").results.chats as SearchSection.Items).items.single()

            assertEquals(UiText.Plain("Ada Lovelace"), row.title)
            assertEquals(TitleBadge.Verified, row.badge)
            assertTrue(row.isOnline)
            assertEquals(UiText.resource(R.string.search_username, "ada"), row.subtitle)
        }

    @Test
    fun `a group or channel without a username shows its members or subscribers`() =
        runTest {
            groups.supergroupMap.value =
                mapOf(
                    6L to supergroup(6).copy(memberCount = 1204),
                    7L to supergroup(7, isChannel = true).copy(memberCount = 12_301),
                ).toImmutableMap()
            search.onSearchPublicChats = {
                found(
                    chat(-6, type = ChatType.Supergroup(6, isChannel = false)),
                    chat(-7, type = ChatType.Supergroup(7, isChannel = true)),
                )
            }

            val rows = (searching("club").results.global as SearchSection.Items).items

            assertEquals(UiText.plural(R.plurals.search_members, 1204, "1,204"), rows[0].subtitle)
            assertEquals(UiText.plural(R.plurals.search_subscribers, 12_301, "12,301"), rows[1].subtitle)
        }

    @Test
    fun `Saved Messages shows under its own name, without a subtitle`() =
        runTest {
            search.recents += chat(ME, title = "Me")

            val row = viewModel().suggestions.recents.single()

            assertEquals(UiText.resource(UiR.string.chat_saved_messages), row.title)
            assertNull(row.subtitle)
        }

    @Test
    fun `message rows show the chat, the sender in a group, the text and the date`() =
        runTest {
            users.setUsers(user(8, firstName = "Grace"))
            val group = chat(-3, type = ChatType.BasicGroup(3), title = "Book Club")
            val inGroup =
                message(-3, id = 31, sender = MessageSender.User(8), content = textContent("We read Ada next"))
            search.onSearchMessages = { _, _ -> page(FoundMessage(inGroup, group), foundMessage(2, 10)) }

            val rows = (searching("ada").results.messages.first as SearchSection.Items).items

            assertEquals(UiText.Plain("Book Club"), rows[0].chatTitle)
            assertEquals(UiText.Plain("Grace"), rows[0].sender)
            assertEquals(UiText.Plain("We read Ada next"), rows[0].preview)
            assertEquals("9:41 AM", rows[0].date)
            // A private chat names no sender.
            assertNull(rows[1].sender)
        }

    @Test
    fun `a long message shows from shortly before its first match`() =
        runTest {
            val text = "We spent the whole afternoon in the library talking about everything but the notes of Ada"
            search.onSearchMessages = { _, _ -> page(foundMessage(2, 10, text)) }

            val row = (searching("ada").results.messages.first as SearchSection.Items).items.single()

            assertEquals(UiText.Plain("…but the notes of Ada"), row.preview)
        }

    @Test
    fun `the query's words are highlighted, whatever their case`() =
        runTest {
            val highlight = searching("ada LOV").results.highlight

            assertEquals(listOf(0..2, 4..6), highlight.ranges("Ada Lovelace"))
        }

    // Opening results

    @Test
    fun `opening a chat opens it and adds it to the recent chats`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, SearchEvent.OpenChat(2))

            assertEquals(listOf(SearchEffect.OpenChat(2)), effects)
            assertTrue("add:2" in search.calls)
            // The recent chats are read again, the opened one now among them.
            assertEquals(2, search.callsOf("recents").size)
        }

    @Test
    fun `opening a message opens its conversation at it, and adds no recent chat`() =
        runTest {
            val viewModel = searching("ada")
            val effects = effectsOf(viewModel)

            send(viewModel, SearchEvent.OpenMessage(chatId = 2, messageId = 10))

            assertEquals(listOf(SearchEffect.OpenMessage(2, 10)), effects)
            assertTrue(search.callsOf("add").isEmpty())
        }

    private fun textContent(text: String) = MessageContent.Text(ChatListFixtures.text(text), linkPreview = null)
}
