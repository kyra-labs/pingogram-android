package dev.kyralabs.pingogram.feature.chat.ui.pinned

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.MessageTopic
import dev.kyralabs.pingogram.core.domain.model.TextEntity
import dev.kyralabs.pingogram.core.domain.model.TextEntityType
import dev.kyralabs.pingogram.core.navigation.PinnedMessagesRoute
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ADA
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.CHANNEL_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.ME
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.NOW
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.channelChat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.chat
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.user
import dev.kyralabs.pingogram.feature.chat.ui.FakeChatRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeMessageActionRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakePinnedMessageRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeTimePatterns
import dev.kyralabs.pingogram.feature.chat.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.chat.ui.FakeWallClock
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.Locale

/**
 * The pinned messages page of [PinnedMessagesViewModel] (the owner's design, `docs/DESIGN_SYSTEM.md` §6 "Pinned
 * messages
 * bar"): the cards under their months, the filters with their counts, the search, the states, each action with its
 * errors, the jump handed to the conversation, and what survives process death.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PinnedMessagesViewModelTest {
    private val chats = FakeChatRepository()
    private val users = FakeUserRepository().apply { myUserId.value = ME }
    private val pinned = FakePinnedMessageRepository()
    private val actions = FakeMessageActionRepository()
    private val jumps = PinnedJumps()
    private val clock = FakeWallClock(NOW)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        chats.setChats(chat(PRIVATE_CHAT, title = "Pingogram Beta"))
        users.setUsers(user(ADA, firstName = "Ada", lastName = "Lovelace"), user(ME, firstName = "Me"))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        savedState: SavedStateHandle = SavedStateHandle(),
        topic: MessageTopic? = null,
    ): PinnedMessagesViewModel {
        val viewModel =
            PinnedMessagesViewModel(
                chatId = PRIVATE_CHAT,
                topic = topic,
                savedState = savedState,
                repository = pinned,
                chatRepository = chats,
                userRepository = users,
                actions = actions,
                jumps = jumps,
                clock = clock,
                timePatterns = FakeTimePatterns(),
                dispatcher = StandardTestDispatcher(testScheduler),
                locale = { Locale.US },
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        runCurrent()
        return viewModel
    }

    private fun TestScope.effectsOf(viewModel: PinnedMessagesViewModel): List<PinnedPageEffect> {
        val effects = mutableListOf<PinnedPageEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.collect(effects::add) }
        return effects
    }

    private fun TestScope.send(
        viewModel: PinnedMessagesViewModel,
        event: PinnedPageEvent,
    ) {
        viewModel.onEvent(event)
        runCurrent()
    }

    private val PinnedMessagesViewModel.months: List<PinnedMonth>
        get() = (uiState.value.content as PinnedPageContent.Content).months

    private val PinnedMessagesViewModel.ids: List<Long>
        get() = months.flatMap { month -> month.cards.map { it.messageId } }

    /** Three pins: a text this month, a link this month, a text in the month before. */
    private fun pinThree() {
        pinned.setPinned(
            PRIVATE_CHAT,
            text(3, "Release notes for 2.2.0", NOW),
            link(2, "Testing guide https://github.com", NOW.minus(Duration.ofDays(2))),
            text(1, "Welcome to the beta", NOW.minus(Duration.ofDays(40))),
        )
    }

    @Test
    fun `a pin another chat sent, as a linked channel's post, is named by that chat's title`() =
        runTest {
            chats.setChats(chat(PRIVATE_CHAT, title = "Pingogram Beta"), channelChat)
            pinned.setPinned(PRIVATE_CHAT, message(1, sender = MessageSender.Chat(CHANNEL_CHAT)))

            val viewModel = viewModel()

            assertEquals(
                UiText.Plain("Channel"),
                viewModel.months
                    .single()
                    .cards
                    .single()
                    .name,
            )
        }

    @Test
    fun `shows the pins newest first under their months, numbered from the oldest, with the chat's title`() =
        runTest {
            pinThree()

            val viewModel = viewModel()

            val state = viewModel.uiState.value
            assertEquals("Pingogram Beta", state.chatTitle)
            assertEquals(3, state.total)
            assertEquals(listOf("October", "September"), viewModel.months.map { it.label })
            assertEquals(listOf(3L, 2L, 1L), viewModel.ids)
            assertEquals(listOf(3, 2, 1), viewModel.months.flatMap { month -> month.cards.map { it.number } })
            assertEquals(
                UiText.Plain("Ada Lovelace"),
                viewModel.months
                    .first()
                    .cards
                    .first()
                    .name,
            )
        }

    @Test
    fun `counts each filter and shows only the kind chosen, which survives process death`() =
        runTest {
            pinThree()
            val savedState = SavedStateHandle()
            val viewModel = viewModel(savedState)

            assertEquals(
                listOf(3, 2, 0, 0, 1),
                viewModel.uiState.value.filters
                    .map { it.count },
            )
            send(viewModel, PinnedPageEvent.SelectFilter(PinnedFilter.Links))
            assertEquals(listOf(2L), viewModel.ids)

            val restored = viewModel(savedState)
            assertEquals(PinnedFilter.Links, restored.uiState.value.selected)
            assertEquals(listOf(2L), restored.ids)
        }

    @Test
    fun `searches the pins on the device, and closing the search shows them all again`() =
        runTest {
            pinThree()
            val viewModel = viewModel()

            send(viewModel, PinnedPageEvent.OpenSearch)
            send(viewModel, PinnedPageEvent.Search("beta welcome"))
            assertEquals(listOf(1L), viewModel.ids)

            send(viewModel, PinnedPageEvent.Search("nothing like this"))
            assertEquals(PinnedPageContent.Empty(filtered = true), viewModel.uiState.value.content)

            send(viewModel, PinnedPageEvent.CloseSearch)
            assertEquals(listOf(3L, 2L, 1L), viewModel.ids)
            assertFalse(viewModel.uiState.value.searching)
        }

    @Test
    fun `without pins shows the empty state, and a failed load its error, which retry loads again`() =
        runTest {
            pinned.loadError.value = DomainError.Network
            val viewModel = viewModel()
            assertEquals(PinnedPageContent.Error(DomainError.Network), viewModel.uiState.value.content)

            pinned.loadError.value = null
            send(viewModel, PinnedPageEvent.Retry)

            assertEquals(PinnedPageContent.Empty(filtered = false), viewModel.uiState.value.content)
        }

    @Test
    fun `jump hands the message to the conversation of the chat and topic, and leaves`() =
        runTest {
            pinThree()
            val topic = MessageTopic.Forum(7)
            val viewModel = viewModel(topic = topic)
            val effects = effectsOf(viewModel)

            send(viewModel, PinnedPageEvent.Jump(2))

            assertEquals(listOf(PinnedPageEffect.Leave), effects)
            assertEquals(2L, jumps.take(PRIVATE_CHAT, topic))
        }

    @Test
    fun `copy sends the message's text to the clipboard`() =
        runTest {
            pinThree()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, PinnedPageEvent.Copy(3))

            assertEquals(listOf(PinnedPageEffect.CopyText("Release notes for 2.2.0")), effects)
        }

    @Test
    fun `unpins a message where the user may, and says why it failed`() =
        runTest {
            pinThree()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            assertTrue(viewModel.uiState.value.canUnpin)

            send(viewModel, PinnedPageEvent.Unpin(2))
            assertEquals(listOf("unpin:$PRIVATE_CHAT:2"), actions.calls)

            actions.onAction = { Outcome.Failure(DomainError.Network) }
            send(viewModel, PinnedPageEvent.Unpin(1))
            assertEquals(listOf(PinnedPageEffect.ShowError(DomainError.Network)), effects)
        }

    @Test
    fun `unpin all asks first, then unpins them all, and says it did`() =
        runTest {
            pinThree()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, PinnedPageEvent.UnpinAll)
            assertTrue(viewModel.uiState.value.confirmUnpinAll)
            assertEquals(emptyList<Long>(), pinned.unpinnedAll)

            send(viewModel, PinnedPageEvent.ConfirmUnpinAll)

            assertFalse(viewModel.uiState.value.confirmUnpinAll)
            assertEquals(listOf(PRIVATE_CHAT), pinned.unpinnedAll)
            assertEquals(listOf(PinnedPageEffect.UnpinnedAll), effects)
        }

    @Test
    fun `the route names the conversation's topic, and back`() {
        listOf(
            null,
            MessageTopic.Forum(7),
            MessageTopic.Thread(9),
            MessageTopic.SavedMessages(4),
            MessageTopic.DirectMessages(5),
        ).forEach { topic -> assertEquals(topic, topic.pinnedMessagesRoute(PRIVATE_CHAT).topic) }
        assertEquals(
            PinnedMessagesRoute(PRIVATE_CHAT, topicId = 7),
            MessageTopic.Forum(7).pinnedMessagesRoute(PRIVATE_CHAT),
        )
    }

    private fun text(
        id: Long,
        text: String,
        date: java.time.Instant,
    ): Message =
        message(
            id,
            date = date,
            content = MessageContent.Text(FormattedText(text, persistentListOf()), linkPreview = null),
        )

    private fun link(
        id: Long,
        text: String,
        date: java.time.Instant,
    ): Message {
        val url = text.substringAfter(' ').substringAfter(' ')
        val entity = TextEntity(text.indexOf(url), url.length, TextEntityType.Url)
        return message(
            id,
            date = date,
            content = MessageContent.Text(FormattedText(text, persistentListOf(entity)), linkPreview = null),
        )
    }
}
