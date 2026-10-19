package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibGroupRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.collections.immutable.persistentListOf
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
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.kyralabs.pingogram.core.ui.R as UiR

@OptIn(ExperimentalCoroutinesApi::class)
class BlockedUsersViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val grace =
        TdApiFixtures.user(
            id = 7,
            firstName = "Grace",
            lastName = "Hopper",
            usernames = listOf("grace"),
        )
    private val alan = TdApiFixtures.user(id = 8, firstName = "Alan", lastName = "Turing", phoneNumber = "447700900123")
    private val deleted =
        TdApiFixtures.user(id = 9, firstName = "", phoneNumber = "", type = TdApi.UserTypeDeleted())
    private val channel =
        TdApiFixtures.chat(id = -100_123, type = TdApi.ChatTypeSupergroup(6, true), title = "Spam Channel")
    private val supergroup = TdApiFixtures.supergroup(id = 6, usernames = listOf("spamchannel"), isChannel = true)

    private fun user(id: Long) = TdApi.MessageSenderUser(id)

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them; the caching ones subscribe to
    // TDLib's updates before the test emits any, as they do at app start.
    private suspend fun TestScope.viewModel(announce: Boolean = true): BlockedUsersViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler))
        val groups = TdlibGroupRepository(client, backgroundScope)
        if (announce) {
            listOf(grace, alan, deleted).forEach { client.emit(TdApiFixtures.updateUser(it)) }
            client.emit(TdApiFixtures.updateSupergroup(supergroup))
            client.emit(TdApiFixtures.updateNewChat(channel))
            runCurrent()
        }
        return BlockedUsersViewModel(TdlibBlockListRepository(client), users, chats, groups).also { runCurrent() }
    }

    private fun TestScope.effectsOf(viewModel: BlockedUsersViewModel): List<BlockedUsersEffect> {
        val effects = mutableListOf<BlockedUsersEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: BlockedUsersViewModel,
        vararg events: BlockedUsersEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    /** Answers pages of the block list: [pages] by offset, with [totalCount]. */
    private fun answerBlocked(
        totalCount: Int,
        pages: Map<Int, List<TdApi.MessageSender>>,
    ) = client.on(TdApi.GetBlockedMessageSenders::class) { request ->
        TdResult.Success(TdApi.MessageSenders(totalCount, pages[request.offset].orEmpty().toTypedArray()))
    }

    private fun answerBlocked(vararg senders: TdApi.MessageSender) =
        answerBlocked(
            senders.size,
            mapOf(0 to senders.toList()),
        )

    private fun answerBlockList(result: TdResult<TdApi.Ok> = TdResult.Success(TdApi.Ok())) =
        client.on(TdApi.SetMessageSenderBlockList::class, result)

    private val BlockedUsersViewModel.state get() = uiState.value

    private val BlockedUsersViewModel.content get() = state as BlockedUsersUiState.Content

    private val BlockedUsersViewModel.senders get() = content.items.map { it.sender }

    @Test
    fun `is loading until the first page arrives`() =
        runTest {
            client.on(TdApi.GetBlockedMessageSenders::class) {
                CompletableDeferred<TdResult<TdApi.MessageSenders>>().await()
            }

            assertEquals(BlockedUsersUiState.Loading, viewModel().state)
        }

    @Test
    fun `rows show names with usernames or phone numbers, and chats by their title`() =
        runTest {
            answerBlocked(user(7), user(8), user(9), TdApi.MessageSenderChat(-100_123))

            assertEquals(
                BlockedUsersUiState.Content(
                    items =
                        persistentListOf(
                            BlockedSenderItem(
                                sender = MessageSender.User(7),
                                name = UiText.Plain("Grace Hopper"),
                                detail = UiText.resource(R.string.settings_blocked_username, "grace"),
                                photo = null,
                                avatarId = 7,
                            ),
                            BlockedSenderItem(
                                sender = MessageSender.User(8),
                                name = UiText.Plain("Alan Turing"),
                                detail = UiText.resource(R.string.settings_blocked_phone, "+447700900123"),
                                photo = null,
                                avatarId = 8,
                            ),
                            BlockedSenderItem(
                                sender = MessageSender.User(9),
                                name = UiText.resource(UiR.string.user_deleted_account),
                                detail = null,
                                photo = null,
                                avatarId = 9,
                                placeholderIcon = UiR.drawable.ic_person_filled,
                            ),
                            BlockedSenderItem(
                                sender = MessageSender.Chat(-100_123),
                                name = UiText.Plain("Spam Channel"),
                                detail = UiText.resource(R.string.settings_blocked_username, "spamchannel"),
                                photo = null,
                                avatarId = -100_123,
                            ),
                        ),
                    totalCount = 4,
                    more = MoreBlockedSenders.None,
                ),
                viewModel().state,
            )
        }

    @Test
    fun `users not in memory yet are asked for`() =
        runTest {
            answerBlocked(user(7))
            client.on(TdApi.GetUser::class, TdResult.Success(grace))

            val viewModel = viewModel(announce = false)

            assertEquals(
                UiText.Plain("Grace Hopper"),
                viewModel.content.items
                    .single()
                    .name,
            )
        }

    @Test
    fun `asks for the main block list from the start`() =
        runTest {
            answerBlocked(user(7))

            viewModel()

            val request = client.sentOf<TdApi.GetBlockedMessageSenders>().single()
            assertInstanceOf(TdApi.BlockListMain::class.java, request.blockList)
            assertEquals(0, request.offset)
        }

    @Test
    fun `nobody blocked is empty`() =
        runTest {
            answerBlocked()

            assertEquals(BlockedUsersUiState.Empty, viewModel().state)
        }

    @Test
    fun `a failed first page shows why, and retry loads it again`() =
        runTest {
            client.on(
                TdApi.GetBlockedMessageSenders::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 6")),
            )
            val viewModel = viewModel()

            assertEquals(BlockedUsersUiState.Error(DomainError.FloodWait(6)), viewModel.state)

            answerBlocked(user(7))
            send(viewModel, BlockedUsersEvent.Retry)

            assertEquals(listOf(MessageSender.User(7)), viewModel.senders)
        }

    @Test
    fun `more pages load as asked, from where the list ends, until all are loaded`() =
        runTest {
            answerBlocked(totalCount = 3, pages = mapOf(0 to listOf(user(7), user(8)), 2 to listOf(user(9))))
            val viewModel = viewModel()

            assertEquals(MoreBlockedSenders.Available, viewModel.content.more)

            send(viewModel, BlockedUsersEvent.LoadMore)

            assertEquals(listOf(0, 2), client.sentOf<TdApi.GetBlockedMessageSenders>().map { it.offset })
            assertEquals(listOf(7L, 8L, 9L), viewModel.senders.map { (it as MessageSender.User).userId })
            assertEquals(MoreBlockedSenders.None, viewModel.content.more)

            send(viewModel, BlockedUsersEvent.LoadMore)

            assertEquals(2, client.sentOf<TdApi.GetBlockedMessageSenders>().size)
        }

    @Test
    fun `the next page shows progress while it loads, and one loads at a time`() =
        runTest {
            answerBlocked(totalCount = 3, pages = mapOf(0 to listOf(user(7), user(8))))
            val viewModel = viewModel()
            client.on(TdApi.GetBlockedMessageSenders::class) {
                CompletableDeferred<TdResult<TdApi.MessageSenders>>().await()
            }

            send(viewModel, BlockedUsersEvent.LoadMore, BlockedUsersEvent.LoadMore)

            assertEquals(MoreBlockedSenders.Loading, viewModel.content.more)
            assertEquals(2, client.sentOf<TdApi.GetBlockedMessageSenders>().size)
        }

    @Test
    fun `a failed next page shows at the end, and loading more tries again`() =
        runTest {
            answerBlocked(totalCount = 3, pages = mapOf(0 to listOf(user(7), user(8))))
            val viewModel = viewModel()
            client.on(TdApi.GetBlockedMessageSenders::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            send(viewModel, BlockedUsersEvent.LoadMore)

            assertEquals(MoreBlockedSenders.Failed(DomainError.Network), viewModel.content.more)

            answerBlocked(totalCount = 3, pages = mapOf(2 to listOf(user(9))))
            send(viewModel, BlockedUsersEvent.LoadMore)

            assertEquals(3, viewModel.senders.size)
        }

    @Test
    fun `senders already loaded are not shown twice`() =
        runTest {
            answerBlocked(totalCount = 3, pages = mapOf(0 to listOf(user(7), user(8)), 2 to listOf(user(8), user(9))))
            val viewModel = viewModel()

            send(viewModel, BlockedUsersEvent.LoadMore)

            assertEquals(listOf(7L, 8L, 9L), viewModel.senders.map { (it as MessageSender.User).userId })
        }

    // Unblocking

    @Test
    fun `unblocking takes the sender off the block list, then out of the list, and offers to undo it`() =
        runTest {
            answerBlocked(user(7), user(8))
            answerBlockList()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, BlockedUsersEvent.Unblock(MessageSender.User(7)))

            val request = client.sentOf<TdApi.SetMessageSenderBlockList>().single()
            assertEquals(7L, (request.senderId as TdApi.MessageSenderUser).userId)
            assertNull(request.blockList)
            assertEquals(listOf(MessageSender.User(8)), viewModel.senders)
            assertEquals(1, viewModel.content.totalCount)
            assertEquals(
                listOf(BlockedUsersEffect.Unblocked(MessageSender.User(7), UiText.Plain("Grace Hopper"))),
                effects,
            )
        }

    @Test
    fun `a row shows progress until TDLib unblocks, and unblocks once`() =
        runTest {
            answerBlocked(user(7))
            client.on(TdApi.SetMessageSenderBlockList::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            send(
                viewModel,
                BlockedUsersEvent.Unblock(MessageSender.User(7)),
                BlockedUsersEvent.Unblock(MessageSender.User(7)),
            )

            assertTrue(
                viewModel.content.items
                    .single()
                    .isUnblocking,
            )
            assertEquals(1, client.sentOf<TdApi.SetMessageSenderBlockList>().size)
        }

    @Test
    fun `a failed unblock keeps the row and shows the error`() =
        runTest {
            answerBlocked(user(7))
            answerBlockList(TdResult.Failure(TdError(429, "Too Many Requests: retry after 3")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, BlockedUsersEvent.Unblock(MessageSender.User(7)))

            assertEquals(listOf(BlockedUsersEffect.ShowError(DomainError.FloodWait(3))), effects)
            assertEquals(listOf(MessageSender.User(7)), viewModel.senders)
            assertFalse(
                viewModel.content.items
                    .single()
                    .isUnblocking,
            )
        }

    @Test
    fun `unblocking the last one leaves the list empty`() =
        runTest {
            answerBlocked(user(7))
            answerBlockList()
            val viewModel = viewModel()

            send(viewModel, BlockedUsersEvent.Unblock(MessageSender.User(7)))

            assertEquals(BlockedUsersUiState.Empty, viewModel.state)
        }

    @Test
    fun `undo blocks again and puts the row back where it was`() =
        runTest {
            answerBlocked(user(7), user(8), user(9))
            answerBlockList()
            val viewModel = viewModel()

            send(
                viewModel,
                BlockedUsersEvent.Unblock(MessageSender.User(8)),
                BlockedUsersEvent.Undo(MessageSender.User(8)),
            )

            val request = client.sentOf<TdApi.SetMessageSenderBlockList>().last()
            assertEquals(8L, (request.senderId as TdApi.MessageSenderUser).userId)
            assertInstanceOf(TdApi.BlockListMain::class.java, request.blockList)
            assertEquals(listOf(7L, 8L, 9L), viewModel.senders.map { (it as MessageSender.User).userId })
            assertEquals(3, viewModel.content.totalCount)
        }

    @Test
    fun `a failed undo shows the error`() =
        runTest {
            answerBlocked(user(7), user(8))
            answerBlockList()
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)
            send(viewModel, BlockedUsersEvent.Unblock(MessageSender.User(7)))
            answerBlockList(TdResult.Failure(TdError(500, "Network is unreachable")))

            send(viewModel, BlockedUsersEvent.Undo(MessageSender.User(7)))

            assertEquals(BlockedUsersEffect.ShowError(DomainError.Network), effects.last())
            assertEquals(listOf(MessageSender.User(8)), viewModel.senders)
        }

    @Test
    fun `undo does nothing for a sender not unblocked here`() =
        runTest {
            answerBlocked(user(7))
            answerBlockList()
            val viewModel = viewModel()

            send(viewModel, BlockedUsersEvent.Undo(MessageSender.User(7)))

            assertTrue(client.sentOf<TdApi.SetMessageSenderBlockList>().isEmpty())
        }

    @Test
    fun `after an undo the next page starts earlier`() =
        runTest {
            answerBlocked(totalCount = 3, pages = mapOf(0 to listOf(user(7), user(8))))
            answerBlockList()
            val viewModel = viewModel()
            send(
                viewModel,
                BlockedUsersEvent.Unblock(MessageSender.User(7)),
                BlockedUsersEvent.Undo(MessageSender.User(7)),
            )
            answerBlocked(totalCount = 3, pages = mapOf(1 to listOf(user(8), user(9))))

            send(viewModel, BlockedUsersEvent.LoadMore)

            assertEquals(1, client.sentOf<TdApi.GetBlockedMessageSenders>().last().offset)
            assertEquals(listOf(7L, 8L, 9L), viewModel.senders.map { (it as MessageSender.User).userId })
        }
}
