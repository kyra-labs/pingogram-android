package dev.kyralabs.pingogram.feature.calls.ui.comments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.GroupCallComment
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.feature.calls.CALL_ID
import dev.kyralabs.pingogram.feature.calls.CHAT_ID
import dev.kyralabs.pingogram.feature.calls.CallLine
import dev.kyralabs.pingogram.feature.calls.CommentDeletion
import dev.kyralabs.pingogram.feature.calls.FakeCallClock
import dev.kyralabs.pingogram.feature.calls.FakeCallEngine
import dev.kyralabs.pingogram.feature.calls.FakeCallForeground
import dev.kyralabs.pingogram.feature.calls.FakeCallSystem
import dev.kyralabs.pingogram.feature.calls.FakeConferenceCallRepository
import dev.kyralabs.pingogram.feature.calls.FakeGroupCallRepository
import dev.kyralabs.pingogram.feature.calls.FakeNetworkRepository
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import dev.kyralabs.pingogram.feature.calls.SELF_ID
import dev.kyralabs.pingogram.feature.calls.groupCall
import dev.kyralabs.pingogram.feature.calls.ui.FakeUserRepository
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallChat
import dev.kyralabs.pingogram.feature.calls.ui.GroupCallChats
import dev.kyralabs.pingogram.feature.calls.ui.user
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import org.junit.jupiter.api.Test

/** A voice chat's comments: shown or not, named, sent, reacted, deleted, turned off, and announced politely. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupCallCommentsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeGroupCallRepository()
    private val users = FakeUserRepository()
    private val created = mutableListOf<GroupCallCommentsViewModel>()
    private val chats = GroupCallChats { id -> flowOf(GroupCallChat(id, "Book club", null)) }

    init {
        Dispatchers.setMain(dispatcher)
        users.add(user(SELF_ID, "Ada", "Lovelace"))
        users.add(user(7, "Grace", "Hopper"))
        repository.calls.value = mapOf(CALL_ID to groupCall(canComment = true))
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    /** A manager in the voice chat of [CHAT_ID], joined. */
    private suspend fun TestScope.joined(): GroupCallManager {
        val manager =
            GroupCallManager(
                repository,
                FakeConferenceCallRepository(),
                FakeCallEngine(),
                FakeCallSystem(),
                FakeCallForeground(),
                FakeCallClock(),
                { "" },
                CallLine(),
                { FakeNetworkRepository() },
                backgroundScope,
            )
        manager.join(CHAT_ID)
        runCurrent()
        repository.participant(participant(SELF_ID, isCurrentUser = true))
        return manager
    }

    private suspend fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): GroupCallCommentsViewModel {
        val manager = joined()
        return GroupCallCommentsViewModel(handle, manager, repository, chats, users).also { viewModel ->
            created += viewModel
            backgroundScope.launch { viewModel.uiState.collect {} }
            backgroundScope.launch { viewModel.announcement.collect {} }
            runCurrent()
        }
    }

    private fun GroupCallCommentsViewModel.available() = uiState.value as GroupCallCommentsUiState.Available

    @Test
    fun `comments show by name, newest last, the user's own marked, and the count of all received`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            repository.comment(comment(1, 7, "Hello"))
            repository.comment(comment(2, SELF_ID, "Hi Grace"))
            runCurrent()

            val state = viewModel.available()
            assertTrue(state.isShown)
            assertTrue(state.canComment)
            assertEquals(listOf("Grace Hopper", "Ada Lovelace"), state.comments.map { it.name })
            assertEquals(listOf(false, true), state.comments.map { it.isOwn })
            assertEquals(2, state.received)
        }

    @Test
    fun `a call without comments that the user may not turn on offers none`() =
        runTest(dispatcher) {
            repository.calls.value = mapOf(CALL_ID to groupCall(canComment = false))
            val viewModel = viewModel()

            assertEquals(GroupCallCommentsUiState.Unavailable, viewModel.uiState.value)
        }

    @Test
    fun `an administrator may turn comments on while they are off`() =
        runTest(dispatcher) {
            repository.calls.value = mapOf(CALL_ID to groupCall(canComment = false, canToggleComments = true))
            val viewModel = viewModel()

            val state = viewModel.available()
            assertFalse(state.areAllowed)
            assertTrue(state.canToggle)
            viewModel.onEvent(CommentEvent.SetAllowed(true))
            runCurrent()
            assertEquals(listOf(true), repository.commentToggles)
        }

    @Test
    fun `the toggle hides the pane and stays hidden after process death`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val viewModel = viewModel(handle)

            viewModel.onEvent(CommentEvent.ToggleShown)
            runCurrent()
            assertFalse(viewModel.available().isShown)

            val restored = GroupCallCommentsViewModel(handle, joined(), repository, chats, users)
            created += restored
            backgroundScope.launch { restored.uiState.collect {} }
            runCurrent()
            assertFalse(restored.available().isShown)
        }

    @Test
    fun `sending trims the text and sends nothing blank, nor where comments cost Stars`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onEvent(CommentEvent.Send("  Hi all  "))
            viewModel.onEvent(CommentEvent.Send("   "))
            runCurrent()
            assertEquals(listOf("Hi all"), repository.sentComments)

            repository.calls.value = mapOf(CALL_ID to groupCall(canComment = true).copy(commentStarCount = 10))
            runCurrent()
            assertTrue(viewModel.available().isPaid)
            viewModel.onEvent(CommentEvent.Send("Paid?"))
            runCurrent()
            assertEquals(listOf("Hi all"), repository.sentComments)
        }

    @Test
    fun `a refused comment is a snackbar's error`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            repository.commentResult = Outcome.Failure(DomainError.FloodWait(5))

            viewModel.onEvent(CommentEvent.Send("Hi"))
            runCurrent()

            assertEquals(CommentEffect.Failed(DomainError.FloodWait(5)), viewModel.effects.first())
        }

    @Test
    fun `quick reactions are sent as comments and float up, but not those there before the screen opened`() =
        runTest(dispatcher) {
            repository.comment(comment(1, 7, "❤"))
            val viewModel = viewModel()
            viewModel.onEvent(CommentEvent.React("🔥"))
            viewModel.onEvent(CommentEvent.React("not a reaction"))
            runCurrent()
            assertEquals(listOf("🔥"), repository.sentComments)

            repository.comment(comment(2, 7, "👏"))
            repository.comment(comment(3, 7, "Nice talk"))
            runCurrent()
            assertEquals(listOf("👏"), viewModel.available().reactions.map { it.emoji })
        }

    @Test
    fun `an administrator deletes, reports and deletes all from a sender`() =
        runTest(dispatcher) {
            repository.calls.value = mapOf(CALL_ID to groupCall(canComment = true, canToggleComments = true))
            val viewModel = viewModel()
            repository.comment(comment(1, 7, "Spam"))
            runCurrent()
            val row = viewModel.available().comments.single()
            assertTrue(row.canDelete)

            viewModel.onEvent(CommentEvent.Delete(1, reportSpam = true))
            viewModel.onEvent(CommentEvent.DeleteAllFrom(row.senderKey))
            runCurrent()

            assertEquals(
                listOf(
                    CommentDeletion(listOf(1), null, true),
                    CommentDeletion(emptyList(), MessageSender.User(7), false),
                ),
                repository.commentDeletions,
            )
        }

    @Test
    fun `others may not delete all from a sender`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            repository.comment(comment(1, 7, "Hello"))
            runCurrent()

            viewModel.onEvent(CommentEvent.DeleteAllFrom("user:7"))
            runCurrent()

            assertTrue(repository.commentDeletions.isEmpty())
            assertFalse(
                viewModel
                    .available()
                    .comments
                    .single()
                    .canDelete,
            )
        }

    @Test
    fun `new comments are announced at most every few seconds, with how many came, never the user's own`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            assertNull(viewModel.announcement.value)

            repository.comment(comment(1, 7, "Hello"))
            runCurrent()
            assertEquals(CommentAnnouncement(1, "Grace Hopper", "Hello"), viewModel.announcement.value)

            repository.comment(comment(2, 7, "Anyone?"))
            repository.comment(comment(3, 7, "Hm"))
            runCurrent()
            // Still the first: the next waits for the interval.
            assertEquals(1, viewModel.announcement.value?.count)
            advanceTimeBy(GroupCallCommentsViewModel.ANNOUNCE_INTERVAL_MS + 1)
            runCurrent()
            assertEquals(CommentAnnouncement(2, "Grace Hopper", "Hm"), viewModel.announcement.value)

            advanceTimeBy(GroupCallCommentsViewModel.ANNOUNCE_INTERVAL_MS + 1)
            repository.comment(comment(4, SELF_ID, "Mine"))
            runCurrent()
            assertEquals(CommentAnnouncement(2, "Grace Hopper", "Hm"), viewModel.announcement.value)
        }

    private fun comment(
        id: Int,
        userId: Long,
        text: String,
    ) = GroupCallComment(
        id = id,
        sender = MessageSender.User(userId),
        date = 0,
        text = FormattedText(text, persistentListOf()),
    )

    private fun participant(
        userId: Long,
        isCurrentUser: Boolean = false,
    ) = GroupCallParticipant(
        participant = MessageSender.User(userId),
        audioSourceId = userId.toInt(),
        bio = "",
        isCurrentUser = isCurrentUser,
        isSpeaking = false,
        isHandRaised = false,
        isMutedForAllUsers = false,
        isMutedForCurrentUser = false,
        canUnmuteSelf = true,
        canBeMutedForAllUsers = false,
        canBeUnmutedForAllUsers = false,
        order = "1",
    )
}
