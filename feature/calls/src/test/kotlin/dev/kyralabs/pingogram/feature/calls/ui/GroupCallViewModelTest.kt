package dev.kyralabs.pingogram.feature.calls.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipantVideo
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.feature.calls.CALL_ID
import dev.kyralabs.pingogram.feature.calls.CHAT_ID
import dev.kyralabs.pingogram.feature.calls.CallLine
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.FakeCallClock
import dev.kyralabs.pingogram.feature.calls.FakeCallEngine
import dev.kyralabs.pingogram.feature.calls.FakeCallForeground
import dev.kyralabs.pingogram.feature.calls.FakeCallSystem
import dev.kyralabs.pingogram.feature.calls.FakeGroupCallRepository
import dev.kyralabs.pingogram.feature.calls.FakeNetworkRepository
import dev.kyralabs.pingogram.feature.calls.GroupCallEndReason
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import dev.kyralabs.pingogram.feature.calls.MuteRequest
import dev.kyralabs.pingogram.feature.calls.SELF_ID
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
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

/** The voice chat's screen: joining after the microphone permission, refused joins, participants, actions. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroupCallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeGroupCallRepository()
    private val engine = FakeCallEngine()
    private val clock = FakeCallClock()
    private val users = FakeUserRepository()
    private val line = CallLine()
    private var microphone = true
    private val camera = FakeCameraAccess()
    private val created = mutableListOf<GroupCallViewModel>()
    private val chats = GroupCallChats { id -> flowOf(GroupCallChat(id, "Book club", null)) }

    init {
        Dispatchers.setMain(dispatcher)
        users.add(user(SELF_ID, "Ada", "Lovelace"))
        users.add(user(7, "Grace", "Hopper"))
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun TestScope.manager() =
        GroupCallManager(
            repository,
            engine,
            FakeCallSystem(),
            FakeCallForeground(),
            clock,
            { "" },
            line,
            { FakeNetworkRepository() },
            backgroundScope,
        )

    private fun TestScope.viewModel(
        manager: GroupCallManager,
        action: String,
        handle: SavedStateHandle =
            SavedStateHandle(
                mapOf(GroupCallActivity.EXTRA_ACTION to action, GroupCallActivity.EXTRA_CHAT_ID to CHAT_ID),
            ),
    ) = GroupCallViewModel(handle, manager, repository, chats, users, { microphone }, camera, clock).also { viewModel ->
        created += viewModel
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `joins at once where the microphone is allowed, and shows the participants by name`() =
        runTest(dispatcher) {
            val viewModel = viewModel(manager(), GroupCallActivity.ACTION_JOIN)
            repository.participant(participant(SELF_ID, "2", isCurrentUser = true))
            repository.participant(participant(7, "9", isSpeaking = true))
            runCurrent()

            val state = viewModel.uiState.value as GroupCallUiState.InCall
            assertEquals("Book club", state.chat.title)
            assertEquals(GroupCallStage.Joining, state.stage)
            assertEquals(3, state.participantCount)
            assertEquals(listOf("Grace Hopper", "Ada Lovelace"), state.participants.map { it.name })
            assertEquals(ParticipantState.Speaking, state.participants.first().state)
            assertEquals(ParticipantState.Muted, state.participants.last().state)
            assertTrue(state.participants.last().isCurrentUser)
            assertEquals(CALL_ID, repository.joins.single().id)

            engine.groupSessions
                .single()
                .state.value = CallMediaState.Connected
            clock.now += 65_000
            runCurrent()
            val connected = viewModel.uiState.value as GroupCallUiState.InCall
            assertEquals(GroupCallStage.Connected, connected.stage)
        }

    @Test
    fun `asks for the microphone first, then joins once allowed`() =
        runTest(dispatcher) {
            microphone = false
            val viewModel = viewModel(manager(), GroupCallActivity.ACTION_JOIN)
            assertEquals(
                GroupCallUiState.NeedsMicrophone(GroupCallChat(CHAT_ID, "Book club", null), canAsk = true),
                viewModel.uiState.value,
            )
            assertTrue(repository.joins.isEmpty())

            viewModel.onEvent(GroupCallEvent.MicrophoneResult(granted = false, canAskAgain = false))
            runCurrent()
            assertEquals(false, (viewModel.uiState.value as GroupCallUiState.NeedsMicrophone).canAsk)

            viewModel.onEvent(GroupCallEvent.MicrophoneResult(granted = true, canAskAgain = true))
            runCurrent()
            assertTrue(viewModel.uiState.value is GroupCallUiState.InCall)
            assertEquals(1, repository.joins.size)
        }

    @Test
    fun `not now joins nothing and closes the screen`() =
        runTest(dispatcher) {
            microphone = false
            val viewModel = viewModel(manager(), GroupCallActivity.ACTION_JOIN)

            viewModel.onEvent(GroupCallEvent.DismissMicrophone)
            runCurrent()

            assertEquals(GroupCallUiState.Closed, viewModel.uiState.value)
            assertTrue(repository.joins.isEmpty())
        }

    @Test
    fun `during another call it says so, then closes`() =
        runTest(dispatcher) {
            line.take(Any())
            val viewModel = viewModel(manager(), GroupCallActivity.ACTION_JOIN)

            assertEquals(
                GroupCallUiState.Unavailable(GroupCallChat(CHAT_ID, "Book club", null), isBusy = true),
                viewModel.uiState.value,
            )
            advanceTimeBy(2_500)
            assertEquals(GroupCallUiState.Closed, viewModel.uiState.value)
        }

    @Test
    fun `mute, hand and leave go to the voice chat`() =
        runTest(dispatcher) {
            val manager = manager()
            val viewModel = viewModel(manager, GroupCallActivity.ACTION_JOIN)
            repository.participant(participant(SELF_ID, "2", isCurrentUser = true))
            runCurrent()

            viewModel.onEvent(GroupCallEvent.ToggleMute)
            runCurrent()
            assertEquals(listOf(MuteRequest(MessageSender.User(SELF_ID), false)), repository.mutes)

            viewModel.onEvent(GroupCallEvent.ToggleHand)
            runCurrent()
            assertEquals(listOf(MessageSender.User(SELF_ID) to true), repository.hands)

            viewModel.onEvent(GroupCallEvent.Leave)
            runCurrent()
            assertEquals(
                GroupCallStage.Ended(GroupCallEndReason.Left),
                (viewModel.uiState.value as GroupCallUiState.InCall).stage,
            )
            advanceTimeBy(2_500)
            assertEquals(GroupCallUiState.Closed, viewModel.uiState.value)
        }

    @Test
    fun `the camera asks for its permission, then turns on and off`() =
        runTest(dispatcher) {
            val manager = manager()
            val viewModel = viewModel(manager, GroupCallActivity.ACTION_JOIN)
            val effects = mutableListOf<CallEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects += it } }
            runCurrent()
            assertTrue((viewModel.uiState.value as GroupCallUiState.InCall).video.canSend)

            viewModel.onEvent(GroupCallEvent.ToggleCamera)
            runCurrent()
            assertEquals(listOf<CallEffect>(CallEffect.RequestCamera), effects)

            camera.granted = true
            viewModel.onEvent(GroupCallEvent.CameraResult(granted = true, canAskAgain = true))
            runCurrent()
            assertEquals(CameraFacing.Front, (viewModel.uiState.value as GroupCallUiState.InCall).video.camera)
            assertEquals(listOf(true), repository.videoToggles)

            viewModel.onEvent(GroupCallEvent.ToggleCamera)
            runCurrent()
            assertNull((viewModel.uiState.value as GroupCallUiState.InCall).video.camera)
            assertEquals(listOf(true, false), repository.videoToggles)
        }

    @Test
    fun `shows the others' videos by name, and a tap pins and unpins one`() =
        runTest(dispatcher) {
            val manager = manager()
            val viewModel = viewModel(manager, GroupCallActivity.ACTION_JOIN)
            engine.groupSessions
                .single()
                .state.value = CallMediaState.Connected
            val video = GroupCallParticipantVideo("grace", persistentListOf(), isPaused = false)
            repository.participant(participant(7, "9", isSpeaking = true, video = video))
            runCurrent()

            val remote = (viewModel.uiState.value as GroupCallUiState.InCall).video.remote
            assertEquals(
                listOf(ParticipantVideoUi("user:7", "grace", "Grace Hopper", false, false, isSpeaking = true)),
                remote,
            )

            viewModel.onEvent(GroupCallEvent.TogglePin("user:7"))
            runCurrent()
            assertTrue(
                (viewModel.uiState.value as GroupCallUiState.InCall)
                    .video.remote
                    .single()
                    .isPinned,
            )

            viewModel.onEvent(GroupCallEvent.TogglePin("user:7"))
            runCurrent()
            assertFalse(
                (viewModel.uiState.value as GroupCallUiState.InCall)
                    .video.remote
                    .single()
                    .isPinned,
            )
        }

    @Test
    fun `no camera is offered without one on the device`() =
        runTest(dispatcher) {
            camera.hasCamera = false
            val viewModel = viewModel(manager(), GroupCallActivity.ACTION_JOIN)

            assertFalse((viewModel.uiState.value as GroupCallUiState.InCall).video.canSend)
        }

    @Test
    fun `a screen created again does not join a second time`() =
        runTest(dispatcher) {
            val manager = manager()
            val handle =
                SavedStateHandle(
                    mapOf(
                        GroupCallActivity.EXTRA_ACTION to GroupCallActivity.ACTION_JOIN,
                        GroupCallActivity.EXTRA_CHAT_ID to CHAT_ID,
                    ),
                )
            viewModel(manager, GroupCallActivity.ACTION_JOIN, handle)
            viewModel(manager, GroupCallActivity.ACTION_JOIN, handle)

            assertEquals(1, repository.joins.size)
        }

    @Test
    fun `showing without a voice chat closes the screen`() =
        runTest(dispatcher) {
            val viewModel =
                viewModel(
                    manager(),
                    GroupCallActivity.ACTION_SHOW,
                    SavedStateHandle(
                        mapOf(GroupCallActivity.EXTRA_ACTION to GroupCallActivity.ACTION_SHOW),
                    ),
                )

            assertEquals(GroupCallUiState.Closed, viewModel.uiState.value)
        }

    private fun participant(
        userId: Long,
        order: String,
        isCurrentUser: Boolean = false,
        isSpeaking: Boolean = false,
        video: GroupCallParticipantVideo? = null,
    ) = GroupCallParticipant(
        participant = MessageSender.User(userId),
        audioSourceId = userId.toInt(),
        bio = "",
        isCurrentUser = isCurrentUser,
        isSpeaking = isSpeaking,
        isHandRaised = false,
        // TDLib's self-mute: muted for everyone, and allowed to unmute.
        isMutedForAllUsers = isCurrentUser,
        isMutedForCurrentUser = false,
        canUnmuteSelf = isCurrentUser,
        canBeMutedForAllUsers = false,
        canBeUnmutedForAllUsers = false,
        order = order,
        video = video,
    )
}
