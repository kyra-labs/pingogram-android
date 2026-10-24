package dev.kyralabs.pingogram.feature.calls.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.GroupCall
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.repository.GroupCallRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.feature.calls.ActiveGroupCall
import dev.kyralabs.pingogram.feature.calls.CallClock
import dev.kyralabs.pingogram.feature.calls.GroupCallManager
import dev.kyralabs.pingogram.feature.calls.GroupCallPhase
import dev.kyralabs.pingogram.feature.calls.JoinResult
import dev.kyralabs.pingogram.feature.calls.engine.VideoFrameSink
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The voice chat's screen (`docs/adr/0023-group-voice-chats-and-live-streams.md`): the voice chat from
 * [GroupCallManager], its chat from [GroupCallChats], its participants from [GroupCallRepository] with their names from
 * [UserRepository], and its timer. Opened to join a chat's voice chat ([GroupCallActivity.ACTION_JOIN]), to start one
 * ([GroupCallActivity.ACTION_START]) or to show the one the user is in ([GroupCallActivity.ACTION_SHOW]); joining and
 * starting need the microphone, which the screen asks for first when it is missing ([MicrophoneAccess]).
 *
 * The action is taken once per screen: [SavedStateHandle] remembers it was, so a rotation or a recreated process does
 * not join again.
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("TooManyFunctions") // The screen's events, its permission step, the joining and the flows it combines.
class GroupCallViewModel
    @Inject
    // Injected: the voice chat, its participants, chats, users, microphone, camera, clock.
    @Suppress("LongParameterList")
    internal constructor(
        private val savedStateHandle: SavedStateHandle,
        private val manager: GroupCallManager,
        private val repository: GroupCallRepository,
        private val chats: GroupCallChats,
        private val userRepository: UserRepository,
        private val microphone: MicrophoneAccess,
        private val camera: CameraAccess,
        private val clock: CallClock,
    ) : ViewModel() {
        private val action: String = savedStateHandle[GroupCallActivity.EXTRA_ACTION] ?: GroupCallActivity.ACTION_SHOW
        private val startChatId: Long? =
            savedStateHandle.get<Long>(GroupCallActivity.EXTRA_CHAT_ID)?.takeIf { it != 0L }

        /** The permission step: whether Android would still ask; null while none shows. */
        private val permissionStep = MutableStateFlow<Boolean?>(null)

        /** Why joining did not happen, shown for a moment: busy (true) or no voice chat (false). */
        private val unavailable = MutableStateFlow<Boolean?>(null)

        /** True once a voice chat was shown here: its end closes the screen, while before it "Loading" waits for it. */
        private var sawCall = false

        private val hasCamera = camera.hasCamera()

        private val effectChannel = Channel<CallEffect>(Channel.BUFFERED)

        /** The camera's permission request and the app's settings, each once. */
        val effects: Flow<CallEffect> = effectChannel.receiveAsFlow()

        val uiState: StateFlow<GroupCallUiState> =
            combine(manager.call, permissionStep, unavailable, chat(), details()) { call, step, busy, chat, details ->
                val shownChat = chat ?: GroupCallChat(call?.chatId ?: startChatId ?: 0, "", null)
                when {
                    step != null -> {
                        GroupCallUiState.NeedsMicrophone(shownChat, canAsk = step)
                    }

                    busy != null -> {
                        GroupCallUiState.Unavailable(shownChat, isBusy = busy)
                    }

                    call != null && (startChatId == null || call.chatId == startChatId || sawCall) -> {
                        sawCall = true
                        call.toUiState(shownChat, details)
                    }

                    sawCall || savedStateHandle.get<Boolean>(KEY_DONE) == true ||
                        action == GroupCallActivity.ACTION_SHOW -> {
                        GroupCallUiState.Closed
                    }

                    else -> {
                        GroupCallUiState.Loading
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GroupCallUiState.Loading)

        init {
            // After the first frame (docs/PERFORMANCE.md §4): joining loads the engine.
            viewModelScope.launch { takeAction() }
        }

        /** Where a live stream's video goes while the screen draws it, or null once it no longer does. */
        fun setVideoSink(sink: VideoFrameSink?) = manager.setVideoSink(sink)

        /** Where the user's own camera's frames go while the screen draws the self-view; null once it does not. */
        fun setSelfVideoSink(sink: VideoFrameSink?) = manager.setSelfVideoSink(sink)

        /** Where the frames of the video at [endpointId] go while its tile shows; null once it does not. */
        fun setParticipantVideoSink(
            endpointId: String,
            sink: VideoFrameSink?,
        ) = manager.setParticipantVideoSink(endpointId, sink)

        override fun onCleared() {
            manager.setVideoSink(null)
            manager.setSelfVideoSink(null)
        }

        fun onEvent(event: GroupCallEvent) {
            when (event) {
                GroupCallEvent.ToggleMute -> {
                    manager.call.value?.let { call -> viewModelScope.launch { manager.setMuted(!call.isMuted) } }
                }

                GroupCallEvent.ToggleHand -> {
                    manager.call.value?.let { call ->
                        viewModelScope.launch { manager.setHandRaised(!call.isHandRaised) }
                    }
                }

                is GroupCallEvent.SelectRoute -> {
                    manager.selectRoute(event.route)
                }

                GroupCallEvent.Leave -> {
                    viewModelScope.launch { manager.leave() }
                }

                GroupCallEvent.EndForEveryone -> {
                    viewModelScope.launch { manager.endForEveryone() }
                }

                is GroupCallEvent.MicrophoneResult -> {
                    onMicrophoneResult(event)
                }

                GroupCallEvent.DismissMicrophone -> {
                    permissionStep.value = null
                    savedStateHandle[KEY_DONE] = true
                }

                GroupCallEvent.ToggleCamera -> {
                    toggleCamera()
                }

                GroupCallEvent.SwitchCamera -> {
                    viewModelScope.launch { manager.switchCamera() }
                }

                is GroupCallEvent.TogglePin -> {
                    togglePin(event.key)
                }

                is GroupCallEvent.CameraResult -> {
                    if (event.granted) {
                        viewModelScope.launch { manager.setCameraOn(true) }
                    } else if (!event.canAskAgain) {
                        savedStateHandle[KEY_CAMERA_REFUSED] = true
                    }
                }
            }
        }

        private fun toggleCamera() {
            val call = manager.call.value ?: return
            when {
                call.video.camera != null -> viewModelScope.launch { manager.setCameraOn(false) }

                camera.isGranted() -> viewModelScope.launch { manager.setCameraOn(true) }

                savedStateHandle.get<Boolean>(
                    KEY_CAMERA_REFUSED,
                ) == true -> effectChannel.trySend(CallEffect.OpenSettings)

                else -> effectChannel.trySend(CallEffect.RequestCamera)
            }
        }

        private fun togglePin(key: String) {
            val video = manager.call.value?.video ?: return
            val participant = video.remote.firstOrNull { it.participant.key() == key }?.participant ?: return
            viewModelScope.launch { manager.setPinned(participant.takeIf { it != video.pinned }) }
        }

        private suspend fun takeAction() {
            if (savedStateHandle.get<Boolean>(KEY_DONE) == true || startChatId == null) return
            if (action != GroupCallActivity.ACTION_JOIN && action != GroupCallActivity.ACTION_START) return
            if (microphone.isGranted()) {
                join()
            } else {
                permissionStep.value = savedStateHandle.get<Boolean>(KEY_REFUSED) != true
            }
        }

        private fun onMicrophoneResult(result: GroupCallEvent.MicrophoneResult) {
            if (permissionStep.value == null) return
            if (result.granted) {
                permissionStep.value = null
                viewModelScope.launch { join() }
            } else if (!result.canAskAgain) {
                savedStateHandle[KEY_REFUSED] = true
                permissionStep.value = false
            }
        }

        private suspend fun join() {
            val chatId = startChatId ?: return
            savedStateHandle[KEY_DONE] = true
            val result =
                if (action == GroupCallActivity.ACTION_START) {
                    // A refusal (rights, a flood wait) reads as no voice chat: there is none to join.
                    (manager.startVoiceChat(chatId) as? Outcome.Success)?.value ?: JoinResult.NoVoiceChat
                } else {
                    manager.join(chatId)
                }
            if (result != JoinResult.Joining) {
                unavailable.value = result == JoinResult.Busy
                delay(UNAVAILABLE_SHOWN_MS)
                unavailable.value = null
            }
        }

        /** The voice chat's chat, as TDLib knows it. */
        private fun chat(): Flow<GroupCallChat?> =
            manager.call
                .map { it?.chatId ?: startChatId }
                .distinctUntilChanged()
                .flatMapLatest { chatId ->
                    if (chatId == null) {
                        flowOf(null)
                    } else {
                        chats.chat(chatId).map { it ?: GroupCallChat(chatId, "", null) }
                    }
                }.distinctUntilChanged()

        /** The voice chat's participants, count and timer, while there is one. */
        private fun details(): Flow<Details> =
            manager.call
                .map { call -> call?.let { it.groupCallId to it.connectedAtMillis.takeIf { _ -> !it.isOver() } } }
                .distinctUntilChanged()
                .flatMapLatest { ids ->
                    if (ids == null) {
                        flowOf(Details())
                    } else {
                        combine(
                            repository.groupCall(ids.first),
                            participantRows(ids.first),
                            duration(ids.second),
                        ) { info, rows, duration -> Details(info?.participantCount, rows, duration) }
                    }
                }

        private fun participantRows(groupCallId: Int): Flow<ImmutableList<GroupCallParticipantUi>> =
            combine(repository.participants(groupCallId), repository.groupCall(groupCallId), userRepository.users) {
                participants,
                info,
                users,
                ->
                Triple(participants, info, users)
            }.mapLatest { (participants, info, users) -> participants.map { it.toRow(info, users) }.toImmutableList() }

        private suspend fun GroupCallParticipant.toRow(
            info: GroupCall?,
            users: ImmutableMap<Long, User>,
        ): GroupCallParticipantUi {
            val speaking =
                isSpeaking || info?.recentSpeakers?.any { it.participant == participant && it.isSpeaking } == true
            val state =
                when {
                    isHandRaised && isMuted -> ParticipantState.HandRaised
                    isMuted || isMutedForAllUsers -> ParticipantState.Muted
                    speaking -> ParticipantState.Speaking
                    else -> ParticipantState.Listening
                }
            return when (val sender = participant) {
                is MessageSender.User -> {
                    val user = users[sender.userId]
                    GroupCallParticipantUi(
                        sender.key(),
                        sender.userId,
                        user?.let(::displayName).orEmpty(),
                        user?.profilePhoto,
                        isCurrentUser,
                        state,
                    )
                }

                is MessageSender.Chat -> {
                    val chat = chats.chat(sender.chatId).first()
                    GroupCallParticipantUi(
                        sender.key(),
                        sender.chatId,
                        chat?.title.orEmpty(),
                        chat?.photo,
                        isCurrentUser,
                        state,
                    )
                }
            }
        }

        /** The voice chat's length, once a second while connected. */
        private fun duration(connectedAt: Long?): Flow<String?> =
            if (connectedAt == null) {
                flowOf(null)
            } else {
                flow {
                    while (true) {
                        val elapsed = clock.elapsedRealtimeMillis() - connectedAt
                        emit(formatDuration(elapsed / MILLIS_PER_SECOND))
                        delay(MILLIS_PER_SECOND - elapsed % MILLIS_PER_SECOND)
                    }
                }.distinctUntilChanged()
            }

        private data class Details(
            val participantCount: Int? = null,
            val participants: ImmutableList<GroupCallParticipantUi> = persistentListOf(),
            val duration: String? = null,
        )

        private fun ActiveGroupCall.toUiState(
            chat: GroupCallChat,
            details: Details,
        ) = GroupCallUiState.InCall(
            chat = chat,
            stage =
                when (val phase = phase) {
                    GroupCallPhase.Joining -> GroupCallStage.Joining
                    GroupCallPhase.Connected -> GroupCallStage.Connected
                    GroupCallPhase.Reconnecting -> GroupCallStage.Reconnecting
                    is GroupCallPhase.Ended -> GroupCallStage.Ended(phase.reason)
                },
            duration = details.duration,
            participantCount = details.participantCount,
            participants = details.participants,
            isStream = isStream,
            isMuted = isMuted,
            canUnmute = canUnmute,
            isHandRaised = isHandRaised,
            canEnd = canEnd,
            route = audio.route,
            routes = audio.available,
            video =
                GroupCallVideoUi(
                    canSend = video.canSend && hasCamera,
                    camera = video.camera,
                    canSwitchCamera = video.canSwitchCamera,
                    remote = remoteVideos(details.participants),
                ),
        )

        /** The others' videos, named from their rows; a participant TDLib has not announced yet waits. */
        private fun ActiveGroupCall.remoteVideos(rows: ImmutableList<GroupCallParticipantUi>) =
            video.remote
                .mapNotNull { remote ->
                    val key = remote.participant.key()
                    rows.firstOrNull { it.key == key }?.let { row ->
                        ParticipantVideoUi(
                            key = key,
                            endpointId = remote.endpointId,
                            name = row.name,
                            isPaused = remote.isPaused,
                            isPinned = remote.participant == video.pinned,
                            isSpeaking = row.state == ParticipantState.Speaking,
                        )
                    }
                }.toImmutableList()

        private companion object {
            const val KEY_DONE = "group_call_action_done"
            const val KEY_REFUSED = "microphone_refused"
            const val KEY_CAMERA_REFUSED = "camera_refused"
            const val STOP_TIMEOUT_MS = 5_000L
            const val MILLIS_PER_SECOND = 1_000L
            const val UNAVAILABLE_SHOWN_MS = 2_000L
        }
    }

private fun ActiveGroupCall.isOver() = phase is GroupCallPhase.Ended

/** A participant's row key, also their video tile's: "user:42" or "chat:-100". */
private fun MessageSender.key(): String =
    when (this) {
        is MessageSender.User -> "user:$userId"
        is MessageSender.Chat -> "chat:$chatId"
    }

/** First and last name, as TDLib has them; the screen resolves an empty one to "Someone". */
private fun displayName(user: User): String =
    listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ")
