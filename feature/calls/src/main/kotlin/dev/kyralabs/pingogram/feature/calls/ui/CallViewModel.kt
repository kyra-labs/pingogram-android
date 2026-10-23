package dev.kyralabs.pingogram.feature.calls.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.calls.ActiveCall
import dev.kyralabs.pingogram.feature.calls.CallClock
import dev.kyralabs.pingogram.feature.calls.CallManager
import dev.kyralabs.pingogram.feature.calls.CallPhase
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The call screen (`REQUIREMENTS.md` FR-V1): the user's call from [CallManager], the other person from
 * [UserRepository], and the call's timer. Opened to call someone ([CallActivity.ACTION_START] with a user), to answer
 * ([CallActivity.ACTION_ANSWER]) or to show the call ([CallActivity.ACTION_SHOW]); both of the first need the
 * microphone, which the screen asks for first when it is missing ([MicrophoneAccess]).
 *
 * Video (`docs/adr/0019-video-and-group-calls-scope.md`): the camera is asked for when the user turns it on
 * ([CameraAccess], [CallEffect]), and a video call is answered with the camera on where it is allowed already.
 *
 * The action is taken once per screen: [SavedStateHandle] remembers it was, so a rotation or a recreated process does
 * not call again.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // The screen's events, its two permission steps and the video sinks.
class CallViewModel
    @Inject
    internal constructor(
        private val savedStateHandle: SavedStateHandle,
        private val callManager: CallManager,
        private val userRepository: UserRepository,
        private val microphone: MicrophoneAccess,
        private val camera: CameraAccess,
        private val clock: CallClock,
    ) : ViewModel() {
        private val action: String = savedStateHandle[CallActivity.EXTRA_ACTION] ?: CallActivity.ACTION_SHOW
        private val startUserId: Long? = savedStateHandle.get<Long>(CallActivity.EXTRA_USER_ID)?.takeIf { it != 0L }

        /** The permission step: the user to call or answer, and whether Android would still ask. */
        private val permissionStep = MutableStateFlow<PermissionStep?>(null)

        /** True once a call was shown here: its end closes the screen, while before it "Loading" waits for it. */
        private var sawCall = false

        private val effectChannel = Channel<CallEffect>(Channel.BUFFERED)

        /** The camera's permission request and the app's settings, each once. */
        val effects: Flow<CallEffect> = effectChannel.receiveAsFlow()

        private val hasCamera = camera.hasCamera()

        val uiState: StateFlow<CallUiState> =
            combine(callManager.call, permissionStep, person(), duration()) { call, step, person, duration ->
                when {
                    step != null -> {
                        CallUiState.NeedsMicrophone(
                            person ?: CallPerson(step.userId, "", null),
                            step.isAnswering,
                            step.canAsk,
                        )
                    }

                    call != null -> {
                        sawCall = true
                        call.toUiState(person ?: CallPerson(call.userId, "", null), duration, hasCamera)
                    }

                    sawCall || savedStateHandle.get<Boolean>(
                        KEY_DONE,
                    ) == true || action == CallActivity.ACTION_SHOW -> {
                        CallUiState.Closed
                    }

                    else -> {
                        CallUiState.Loading
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CallUiState.Loading)

        init {
            // After the first frame (docs/PERFORMANCE.md §4): calling loads the engine.
            viewModelScope.launch { takeAction() }
        }

        @Suppress("ReturnCount") // An event without a call or a permission step does nothing.
        fun onEvent(event: CallEvent) {
            when (event) {
                CallEvent.Answer -> {
                    if (microphone.isGranted()) {
                        viewModelScope.launch { answer() }
                    } else {
                        callManager.call.value?.let { askForMicrophone(it.userId, isAnswering = true) }
                    }
                }

                CallEvent.HangUp -> {
                    viewModelScope.launch { callManager.hangUp() }
                }

                CallEvent.ToggleMute -> {
                    val call = callManager.call.value ?: return
                    viewModelScope.launch { callManager.setMuted(!call.isMuted) }
                }

                CallEvent.ToggleHold -> {
                    val call = callManager.call.value ?: return
                    viewModelScope.launch { callManager.setOnHold(!call.isOnHold) }
                }

                is CallEvent.SelectRoute -> {
                    callManager.selectRoute(event.route)
                }

                is CallEvent.MicrophoneResult -> {
                    onMicrophoneResult(event)
                }

                CallEvent.ToggleCamera -> {
                    toggleCamera()
                }

                CallEvent.SwitchCamera -> {
                    viewModelScope.launch { callManager.switchCamera() }
                }

                is CallEvent.CameraResult -> {
                    if (event.granted) {
                        viewModelScope.launch { callManager.setCameraOn(true) }
                    } else if (!event.canAskAgain) {
                        savedStateHandle[KEY_CAMERA_REFUSED] = true
                    }
                }

                CallEvent.DismissMicrophone -> {
                    val step = permissionStep.value ?: return
                    permissionStep.value = null
                    savedStateHandle[KEY_DONE] = true
                    if (step.isAnswering) viewModelScope.launch { callManager.hangUp() }
                }
            }
        }

        private var localSink: VideoFrameSink? = null
        private var remoteSink: VideoFrameSink? = null

        /**
         * Where [slot]'s frames go while the screen draws them ([CallManager.setVideoSinks]), or null once it no
         * longer does.
         */
        fun setVideoSink(
            slot: CallVideoSlot,
            sink: VideoFrameSink?,
        ) {
            if (slot == CallVideoSlot.Self) localSink = sink else remoteSink = sink
            callManager.setVideoSinks(localSink, remoteSink)
        }

        override fun onCleared() {
            callManager.setVideoSinks(null, null)
        }

        private fun toggleCamera() {
            val call = callManager.call.value ?: return
            when {
                call.video.camera != null -> viewModelScope.launch { callManager.setCameraOn(false) }

                camera.isGranted() -> viewModelScope.launch { callManager.setCameraOn(true) }

                savedStateHandle.get<Boolean>(
                    KEY_CAMERA_REFUSED,
                ) == true -> effectChannel.trySend(CallEffect.OpenSettings)

                else -> effectChannel.trySend(CallEffect.RequestCamera)
            }
        }

        @Suppress("ReturnCount") // Taken already, or nothing to call or answer.
        private suspend fun takeAction() {
            if (savedStateHandle.get<Boolean>(KEY_DONE) == true) return
            when (action) {
                CallActivity.ACTION_START -> {
                    val userId = startUserId ?: return
                    if (microphone.isGranted()) call(userId) else askForMicrophone(userId, isAnswering = false)
                }

                CallActivity.ACTION_ANSWER -> {
                    val call = callManager.call.value ?: return
                    if (microphone.isGranted()) answer() else askForMicrophone(call.userId, isAnswering = true)
                }
            }
        }

        private fun askForMicrophone(
            userId: Long,
            isAnswering: Boolean,
        ) {
            permissionStep.value =
                PermissionStep(userId, isAnswering, canAsk = savedStateHandle.get<Boolean>(KEY_REFUSED) != true)
        }

        private fun onMicrophoneResult(result: CallEvent.MicrophoneResult) {
            val step = permissionStep.value ?: return
            if (result.granted) {
                permissionStep.value = null
                viewModelScope.launch { if (step.isAnswering) answer() else call(step.userId) }
            } else if (!result.canAskAgain) {
                savedStateHandle[KEY_REFUSED] = true
                permissionStep.value = step.copy(canAsk = false)
            }
        }

        private suspend fun call(userId: Long) {
            savedStateHandle[KEY_DONE] = true
            callManager.startCall(userId)
        }

        /** Answers, a video call with the camera on where it is allowed already (otherwise the user turns it on). */
        private suspend fun answer() {
            savedStateHandle[KEY_DONE] = true
            val video = callManager.call.value?.videoOffered == true && hasCamera && camera.isGranted()
            callManager.answer(withVideo = video)
        }

        /** The other person of the call (or of the permission step), as TDLib knows them. */
        private fun person(): Flow<CallPerson?> =
            combine(callManager.call, permissionStep) { call, step -> call?.userId ?: step?.userId ?: startUserId }
                .distinctUntilChanged()
                .flatMapLatest { userId ->
                    if (userId ==
                        null
                    ) {
                        flowOf(null)
                    } else {
                        userRepository.user(userId).map { it.toPerson(userId) }
                    }
                }

        /** The call's length, once a second while connected. */
        private fun duration(): Flow<String?> =
            callManager.call
                .map { call -> call?.connectedAtMillis?.takeIf { call.phase !is CallPhase.Ended } }
                .distinctUntilChanged()
                .flatMapLatest { connectedAt ->
                    if (connectedAt == null) {
                        flowOf<String?>(null)
                    } else {
                        flow {
                            while (true) {
                                val elapsed = clock.elapsedRealtimeMillis() - connectedAt
                                emit(formatDuration(elapsed / MILLIS_PER_SECOND))
                                delay(MILLIS_PER_SECOND - elapsed % MILLIS_PER_SECOND)
                            }
                        }
                    }
                }.distinctUntilChanged()

        private data class PermissionStep(
            val userId: Long,
            val isAnswering: Boolean,
            val canAsk: Boolean,
        )

        private companion object {
            const val KEY_DONE = "call_action_done"
            const val KEY_REFUSED = "microphone_refused"
            const val KEY_CAMERA_REFUSED = "camera_refused"
            const val STOP_TIMEOUT_MS = 5_000L
            const val MILLIS_PER_SECOND = 1_000L
        }
    }

private fun User?.toPerson(userId: Long): CallPerson =
    CallPerson(userId, this?.let(::displayName).orEmpty(), this?.profilePhoto)

/** First and last name, as TDLib has them; the screen resolves an empty one to "Someone". */
private fun displayName(user: User): String =
    listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")

private fun ActiveCall.toUiState(
    person: CallPerson,
    duration: String?,
    hasCamera: Boolean,
): CallUiState.InCall =
    CallUiState.InCall(
        person = person,
        isOutgoing = isOutgoing,
        stage =
            when (val phase = phase) {
                CallPhase.Requesting -> {
                    CallStage.Requesting
                }

                is CallPhase.Ringing -> {
                    if (isOutgoing &&
                        phase.reachedDevice
                    ) {
                        CallStage.RingingThere
                    } else {
                        CallStage.Ringing
                    }
                }

                CallPhase.Connecting -> {
                    CallStage.Connecting
                }

                CallPhase.Connected -> {
                    CallStage.Connected
                }

                CallPhase.Reconnecting -> {
                    CallStage.Reconnecting
                }

                is CallPhase.Ended -> {
                    CallStage.Ended(phase.reason)
                }
            },
        duration = duration,
        emojis = emojis,
        isMuted = isMuted,
        isOnHold = isOnHold,
        route = audio.route,
        routes = audio.available,
        isVideoCall = videoOffered,
        video =
            CallVideoUi(
                camera = video.camera,
                canSwitchCamera = video.canSwitchCamera,
                remote = video.remote,
                hasCamera = hasCamera,
            ),
    )

/** "0:07", "12:34", "1:02:03". */
internal fun formatDuration(seconds: Long): String {
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val rest = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
