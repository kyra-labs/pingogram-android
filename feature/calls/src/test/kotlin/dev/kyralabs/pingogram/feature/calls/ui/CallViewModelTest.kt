package dev.kyralabs.pingogram.feature.calls.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserStatus
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.calls.CallEndReason
import dev.kyralabs.pingogram.feature.calls.CallIds
import dev.kyralabs.pingogram.feature.calls.CallLine
import dev.kyralabs.pingogram.feature.calls.CallManager
import dev.kyralabs.pingogram.feature.calls.CallUsers
import dev.kyralabs.pingogram.feature.calls.CameraFacing
import dev.kyralabs.pingogram.feature.calls.ENGINE_PROTOCOL
import dev.kyralabs.pingogram.feature.calls.FakeCallClock
import dev.kyralabs.pingogram.feature.calls.FakeCallEngine
import dev.kyralabs.pingogram.feature.calls.FakeCallForeground
import dev.kyralabs.pingogram.feature.calls.FakeCallRepository
import dev.kyralabs.pingogram.feature.calls.FakeCallSystem
import dev.kyralabs.pingogram.feature.calls.FakeCallTones
import dev.kyralabs.pingogram.feature.calls.FakeNetworkRepository
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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
import kotlin.time.Duration.Companion.seconds

/** The call screen: calling or answering after the microphone permission, the call's stages, its timer. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeCallRepository()
    private val engine = FakeCallEngine()
    private val clock = FakeCallClock()
    private val users = FakeUserRepository()
    private var microphone = true
    private val camera = FakeCameraAccess()
    private val created = mutableListOf<CallViewModel>()

    init {
        Dispatchers.setMain(dispatcher)
        users.add(user(42, "Ada", "Lovelace"))
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    private fun TestScope.manager() =
        CallManager(
            repository,
            engine,
            FakeCallSystem(),
            FakeCallForeground(),
            FakeCallTones(),
            clock,
            { _, _ -> "Ada" },
            { FakeNetworkRepository() },
            CallLine(),
            backgroundScope,
        ).also { it.start() }

    private fun TestScope.viewModel(
        manager: CallManager,
        action: String,
        userId: Long? = null,
        handle: SavedStateHandle =
            SavedStateHandle(
                buildMap {
                    put(CallActivity.EXTRA_ACTION, action)
                    if (userId != null) put(CallActivity.EXTRA_USER_ID, userId)
                },
            ),
        callUsers: CallUsers = CallUsers { users },
    ) = CallViewModel(handle, manager, callUsers, { microphone }, camera, clock).also { viewModel ->
        created += viewModel
        backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
    }

    @Test
    fun `calls the user at once where the microphone is allowed`() =
        runTest(dispatcher) {
            val manager = manager()

            val viewModel = viewModel(manager, CallActivity.ACTION_START, userId = 42)
            runCurrent()

            assertEquals(listOf(42L to ENGINE_PROTOCOL), repository.started)
            val state = viewModel.uiState.value as CallUiState.InCall
            assertEquals(CallPerson(42, "Ada Lovelace", null), state.person)
            assertEquals(CallStage.Ringing, state.stage)
            assertTrue(state.isOutgoing)
        }

    @Test
    fun `says why the microphone is needed, then calls once Android allows it`() =
        runTest(dispatcher) {
            microphone = false
            val manager = manager()
            val viewModel = viewModel(manager, CallActivity.ACTION_START, userId = 42)

            assertEquals(
                CallUiState.NeedsMicrophone(CallPerson(42, "Ada Lovelace", null), isAnswering = false, canAsk = true),
                viewModel.uiState.value,
            )
            assertTrue(repository.started.isEmpty())

            microphone = true
            viewModel.onEvent(CallEvent.MicrophoneResult(granted = true, canAskAgain = true))
            runCurrent()

            assertEquals(1, repository.started.size)
            assertTrue(viewModel.uiState.value is CallUiState.InCall)
        }

    @Test
    fun `offers the settings once Android no longer asks, and closes on not now`() =
        runTest(dispatcher) {
            microphone = false
            val viewModel = viewModel(manager(), CallActivity.ACTION_START, userId = 42)

            viewModel.onEvent(CallEvent.MicrophoneResult(granted = false, canAskAgain = false))
            runCurrent()
            assertEquals(false, (viewModel.uiState.value as CallUiState.NeedsMicrophone).canAsk)

            viewModel.onEvent(CallEvent.DismissMicrophone)
            runCurrent()
            assertEquals(CallUiState.Closed, viewModel.uiState.value)
            assertTrue(repository.started.isEmpty())
        }

    @Test
    fun `answers the call that rings`() =
        runTest(dispatcher) {
            val manager = manager()
            repository.update(Call(3, 42, isOutgoing = false, isVideo = false, state = CallState.Pending(true, true)))
            runCurrent()

            val viewModel = viewModel(manager, CallActivity.ACTION_ANSWER)
            runCurrent()

            assertEquals(listOf(3 to ENGINE_PROTOCOL), repository.accepted)
            assertEquals(CallStage.Connecting, (viewModel.uiState.value as CallUiState.InCall).stage)
        }

    @Test
    fun `a call to another account shows the caller as that account knows them`() =
        runTest(dispatcher) {
            val otherAccount = FakeUserRepository().apply { add(user(42, "Grace", "Hopper")) }
            val manager = manager()
            val callId = CallIds.of(slot = 1, tdlibId = 3)
            repository.update(
                Call(callId, 42, isOutgoing = false, isVideo = false, state = CallState.Pending(true, true)),
            )
            runCurrent()

            val viewModel =
                viewModel(manager, CallActivity.ACTION_SHOW, callUsers = { slot ->
                    if (slot ==
                        1
                    ) {
                        otherAccount
                    } else {
                        users
                    }
                })
            runCurrent()

            assertEquals("Grace Hopper", (viewModel.uiState.value as CallUiState.InCall).person.name)
            assertEquals(1, viewModel.callSlot)
        }

    @Test
    fun `an incoming video call says so, and is answered with the camera where it is allowed`() =
        runTest(dispatcher) {
            camera.granted = true
            val manager = manager()
            repository.update(Call(3, 42, isOutgoing = false, isVideo = true, state = CallState.Pending(true, true)))
            runCurrent()

            val viewModel = viewModel(manager, CallActivity.ACTION_SHOW)
            runCurrent()
            assertTrue(viewModel.inCall().isVideoCall)

            viewModel.onEvent(CallEvent.Answer)
            runCurrent()

            assertEquals(CameraFacing.Front, viewModel.inCall().video.camera)
        }

    @Test
    fun `a video call is answered without the camera where it is not allowed yet`() =
        runTest(dispatcher) {
            val manager = manager()
            repository.update(Call(3, 42, isOutgoing = false, isVideo = true, state = CallState.Pending(true, true)))
            runCurrent()
            val viewModel = viewModel(manager, CallActivity.ACTION_SHOW)

            viewModel.onEvent(CallEvent.Answer)
            runCurrent()

            assertEquals(listOf(3 to ENGINE_PROTOCOL), repository.accepted)
            assertNull(viewModel.inCall().video.camera)
        }

    @Test
    fun `share screen asks Android first, shares once agreed, and stops`() =
        runTest(dispatcher) {
            val viewModel = viewModel(manager(), CallActivity.ACTION_START, userId = 42)
            val effects = mutableListOf<CallEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects += it } }
            runCurrent()

            viewModel.onEvent(CallEvent.ToggleScreenSharing)
            runCurrent()
            assertEquals(listOf<CallEffect>(CallEffect.RequestScreenCapture), effects)

            viewModel.onEvent(CallEvent.ScreenSharingResult(granted = false))
            runCurrent()
            assertFalse(viewModel.inCall().video.isSharingScreen)

            viewModel.onEvent(CallEvent.ScreenSharingResult(granted = true))
            runCurrent()
            assertTrue(viewModel.inCall().video.isSharingScreen)

            viewModel.onEvent(CallEvent.ToggleScreenSharing)
            runCurrent()
            assertFalse(viewModel.inCall().video.isSharingScreen)
            assertEquals(1, effects.size)
        }

    @Test
    fun `turns the camera on and off where it is allowed`() =
        runTest(dispatcher) {
            camera.granted = true
            val viewModel = viewModel(manager(), CallActivity.ACTION_START, userId = 42)
            assertTrue(viewModel.inCall().video.hasCamera)

            viewModel.onEvent(CallEvent.ToggleCamera)
            runCurrent()
            assertEquals(CameraFacing.Front, viewModel.inCall().video.camera)

            viewModel.onEvent(CallEvent.SwitchCamera)
            runCurrent()
            assertEquals(CameraFacing.Back, viewModel.inCall().video.camera)

            viewModel.onEvent(CallEvent.ToggleCamera)
            runCurrent()
            assertNull(viewModel.inCall().video.camera)
        }

    @Test
    fun `asks for the camera, and turns it on once Android allows it`() =
        runTest(dispatcher) {
            val viewModel = viewModel(manager(), CallActivity.ACTION_START, userId = 42)
            val effects = mutableListOf<CallEffect>()
            backgroundScope.launch { viewModel.effects.collect { effects += it } }

            viewModel.onEvent(CallEvent.ToggleCamera)
            runCurrent()
            assertEquals(listOf<CallEffect>(CallEffect.RequestCamera), effects)
            assertNull(viewModel.inCall().video.camera)

            camera.granted = true
            viewModel.onEvent(CallEvent.CameraResult(granted = true, canAskAgain = true))
            runCurrent()

            assertEquals(CameraFacing.Front, viewModel.inCall().video.camera)
        }

    @Test
    fun `opens the settings once Android no longer asks for the camera, also after the screen is created again`() =
        runTest(dispatcher) {
            val handle =
                SavedStateHandle(
                    mapOf(CallActivity.EXTRA_ACTION to CallActivity.ACTION_START, CallActivity.EXTRA_USER_ID to 42L),
                )
            val manager = manager()
            val viewModel = viewModel(manager, CallActivity.ACTION_START, handle = handle)
            viewModel.onEvent(CallEvent.CameraResult(granted = false, canAskAgain = false))

            val again = viewModel(manager, CallActivity.ACTION_START, handle = handle)
            val effects = mutableListOf<CallEffect>()
            backgroundScope.launch { again.effects.collect { effects += it } }
            again.onEvent(CallEvent.ToggleCamera)
            runCurrent()

            assertEquals(listOf<CallEffect>(CallEffect.OpenSettings), effects)
            assertEquals(1, repository.started.size)
        }

    @Test
    fun `a video call is made as one with the camera on where it is allowed`() =
        runTest(dispatcher) {
            camera.granted = true
            val handle = videoCallHandle()

            val viewModel = viewModel(manager(), CallActivity.ACTION_START, handle = handle)
            runCurrent()

            assertEquals(listOf(true), repository.startedVideo)
            assertEquals(CameraFacing.Front, viewModel.inCall().video.camera)
        }

    @Test
    fun `a video call without the camera's permission calls, asks for the camera, and turns it on once allowed`() =
        runTest(dispatcher) {
            val manager = manager()
            val effects = mutableListOf<CallEffect>()

            val viewModel = viewModel(manager, CallActivity.ACTION_START, handle = videoCallHandle())
            backgroundScope.launch { viewModel.effects.collect { effects += it } }
            runCurrent()
            assertEquals(listOf(false), repository.startedVideo)
            assertEquals(listOf<CallEffect>(CallEffect.RequestCamera), effects)

            camera.granted = true
            viewModel.onEvent(CallEvent.CameraResult(granted = true, canAskAgain = true))
            runCurrent()

            assertEquals(CameraFacing.Front, viewModel.inCall().video.camera)
        }

    private fun videoCallHandle() =
        SavedStateHandle(
            mapOf(
                CallActivity.EXTRA_ACTION to CallActivity.ACTION_START,
                CallActivity.EXTRA_USER_ID to 42L,
                CallActivity.EXTRA_VIDEO to true,
            ),
        )

    @Test
    fun `a device without a camera offers none`() =
        runTest(dispatcher) {
            camera.hasCamera = false
            val viewModel = viewModel(manager(), CallActivity.ACTION_START, userId = 42)

            assertFalse(viewModel.inCall().video.hasCamera)
        }

    @Test
    fun `hands the screen's video sinks to the call, and takes them back when it goes`() =
        runTest(dispatcher) {
            val manager = manager()
            val viewModel = viewModel(manager, CallActivity.ACTION_START, userId = 42)
            val ready = CallState.Ready(connection())
            repository.update(Call(FakeCallRepository.STARTED_CALL_ID, 42, isOutgoing = true, isVideo = false, ready))
            runCurrent()
            val remote = VideoFrameSink { _, _, _, _ -> }

            viewModel.setVideoSink(CallVideoSlot.Remote, remote)
            runCurrent()
            assertEquals(remote, engine.sessions.single().remoteSink)

            viewModel.setVideoSink(CallVideoSlot.Remote, null)
            runCurrent()
            assertNull(engine.sessions.single().remoteSink)
        }

    private fun CallViewModel.inCall() = uiState.value as CallUiState.InCall

    @Test
    fun `declining from the permission step declines the call`() =
        runTest(dispatcher) {
            microphone = false
            val manager = manager()
            repository.update(Call(3, 42, isOutgoing = false, isVideo = false, state = CallState.Pending(true, true)))
            runCurrent()
            val viewModel = viewModel(manager, CallActivity.ACTION_ANSWER)
            assertEquals(true, (viewModel.uiState.value as CallUiState.NeedsMicrophone).isAnswering)

            viewModel.onEvent(CallEvent.DismissMicrophone)
            runCurrent()

            assertEquals(listOf(3), repository.discarded.map { it.callId })
        }

    @Test
    fun `shows the call's length once connected`() =
        runTest(dispatcher) {
            val manager = manager()
            val viewModel = viewModel(manager, CallActivity.ACTION_START, userId = 42)
            repository.update(Call(7, 42, isOutgoing = true, isVideo = false, state = CallState.Ready(connection())))
            runCurrent()
            engine.sessions
                .single()
                .state.value = CallMediaState.Connected
            runCurrent()
            assertEquals("0:00", (viewModel.uiState.value as CallUiState.InCall).duration)

            clock.now += 61_000
            advanceTimeBy(1.seconds)
            runCurrent()

            val state = viewModel.uiState.value as CallUiState.InCall
            assertEquals("1:01", state.duration)
            assertEquals(CallStage.Connected, state.stage)
        }

    /** A call of [seconds] that the other side hung up, which Telegram asks to have rated. */
    private fun TestScope.ratedCall(
        manager: CallManager,
        seconds: Long = 30,
    ): CallViewModel {
        val viewModel = viewModel(manager, CallActivity.ACTION_START, userId = 42)
        repository.update(Call(7, 42, isOutgoing = true, isVideo = true, state = CallState.Ready(connection())))
        runCurrent()
        engine.sessions
            .single()
            .state.value = CallMediaState.Connected
        runCurrent()
        clock.now += seconds * 1_000
        repository.update(
            Call(
                7,
                42,
                isOutgoing = true,
                isVideo = true,
                state = CallState.Discarded(CallDiscardReason.HungUp, needsRating = true),
            ),
        )
        runCurrent()
        return viewModel
    }

    @Test
    fun `asks to rate the ended call, and stays until it is rated`() =
        runTest(dispatcher) {
            val viewModel = ratedCall(manager())

            val ended = viewModel.uiState.value as CallUiState.InCall
            assertEquals(CallStage.Ended(CallEndReason.HungUp), ended.stage)
            assertEquals(CallRatingPrompt(isVideo = true), ended.rating)

            advanceTimeBy(3.seconds)
            assertEquals(ended, viewModel.uiState.value)

            viewModel.onEvent(CallEvent.Rate(1, setOf(CallProblem.PixelatedVideo)))
            runCurrent()

            assertEquals(listOf(FakeCallRepository.Rating(7, 1, setOf(CallProblem.PixelatedVideo))), repository.rated)
            assertEquals(CallUiState.Closed, viewModel.uiState.value)
        }

    @Test
    fun `not rating closes the screen without rating`() =
        runTest(dispatcher) {
            val viewModel = ratedCall(manager())
            advanceTimeBy(3.seconds)

            viewModel.onEvent(CallEvent.DismissRating)
            runCurrent()

            assertTrue(repository.rated.isEmpty())
            assertEquals(CallUiState.Closed, viewModel.uiState.value)
        }

    @Test
    fun `a call of a few seconds is not rated and closes as before`() =
        runTest(dispatcher) {
            val viewModel = ratedCall(manager(), seconds = 3)

            assertNull((viewModel.uiState.value as CallUiState.InCall).rating)
            advanceTimeBy(3.seconds)
            assertEquals(CallUiState.Closed, viewModel.uiState.value)
        }

    @Test
    fun `a call that could not be made shows why, then closes`() =
        runTest(dispatcher) {
            repository.startResult = Outcome.Failure(DomainError.CallPrivacyRestricted)
            val viewModel = viewModel(manager(), CallActivity.ACTION_START, userId = 42)
            runCurrent()

            assertEquals(
                CallStage.Ended(CallEndReason.Failed(DomainError.CallPrivacyRestricted)),
                (viewModel.uiState.value as CallUiState.InCall).stage,
            )

            advanceTimeBy(3.seconds)
            assertEquals(CallUiState.Closed, viewModel.uiState.value)
        }

    @Test
    fun `does not call again when the screen is created again`() =
        runTest(dispatcher) {
            val manager = manager()
            val handle =
                SavedStateHandle(
                    mapOf(
                        CallActivity.EXTRA_ACTION to CallActivity.ACTION_START,
                        CallActivity.EXTRA_USER_ID to 42L,
                    ),
                )
            viewModel(manager, CallActivity.ACTION_START, handle = handle)
            runCurrent()
            manager.hangUp()
            advanceTimeBy(3.seconds)

            viewModel(manager, CallActivity.ACTION_START, handle = handle)
            runCurrent()

            assertEquals(1, repository.started.size)
        }

    @Test
    fun `shows nothing to show the call when there is none`() =
        runTest(dispatcher) {
            assertEquals(CallUiState.Closed, viewModel(manager(), CallActivity.ACTION_SHOW).uiState.value)
        }

    @Test
    fun `formats durations as the system's call screens do`() {
        assertEquals(listOf("0:07", "12:34", "1:02:03"), listOf(7L, 754L, 3723L).map(::formatDuration))
    }

    private fun connection() =
        CallConnection(
            ENGINE_PROTOCOL,
            persistentListOf(),
            ByteArray(256),
            persistentListOf("😀", "🐶", "🍎", "🚗"),
            true,
            "",
        )
}

internal class FakeUserRepository : UserRepository {
    override val myUserId = MutableStateFlow<Long?>(1)
    private val userMap = MutableStateFlow<ImmutableMap<Long, User>>(persistentMapOf())

    fun add(user: User) {
        userMap.value = persistentMapOf<Long, User>().putAll(userMap.value).put(user.id, user)
    }

    override val users: Flow<ImmutableMap<Long, User>> = userMap

    override fun user(id: Long): Flow<User?> = userMap.map { it[id] }

    override suspend fun getUser(id: Long): Outcome<User> =
        userMap.value[id]?.let { Outcome.Success(it) } ?: Outcome.Failure(DomainError.NotFound)
}

internal fun user(
    id: Long,
    firstName: String,
    lastName: String,
) = User(
    id = id,
    firstName = firstName,
    lastName = lastName,
    usernames = persistentListOf(),
    phoneNumber = "",
    status = UserStatus.Empty,
    profilePhoto = null,
    isVerified = false,
    isScam = false,
    isFake = false,
    isPremium = false,
    isContact = true,
    isMutualContact = true,
    isSupport = false,
    type = UserType.Regular,
    haveAccess = true,
)

internal class FakeCameraAccess : CameraAccess {
    var hasCamera = true
    var granted = false

    override fun hasCamera() = hasCamera

    override fun isGranted() = granted
}
