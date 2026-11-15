package dev.kyralabs.pingogram.feature.chat.ui.transcription

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.model.Transcript
import dev.kyralabs.pingogram.core.domain.model.TranscriptionResult
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatEffect
import dev.kyralabs.pingogram.feature.chat.ui.TranscriptionEffect
import dev.kyralabs.pingogram.feature.chat.ui.TranscriptionEvent
import dev.kyralabs.pingogram.feature.chat.ui.findById
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Voice message transcription in the conversation of chat [chatId], for `ChatViewModel` (`docs/DESIGN_SYSTEM.md` §6
 * "Voice message transcription", the owner's board 28): what the voice and round video bubbles show of their
 * transcripts ([bubbles], copied into their bubbles as translations are), made on the device only.
 *
 * - **Available** ([isAvailable]) on Android 13 and later with an on-device recognizer; elsewhere the button is not
 *   shown and nothing here runs.
 * - **Transcribe** ([TranscriptionEvent.Transcribe]): a transcript kept on the device shows at once; otherwise the
 *   message's file is downloaded (visible priority) and transcribed, one message at a time, the bubble saying so
 *   meanwhile. The first transcript made on the device says "On this phone" (`TranscriptionSettings.noticeShown`).
 *   Android's recognizer needs the microphone permission: without it a snackbar explains and asks
 *   ([TranscriptionEffect.ExplainMicrophone]), and the message is transcribed once it is granted.
 * - **Automatically** (`TranscriptionSettings.automatic`): other people's voice and round video messages on screen
 *   ([onVisible]) are transcribed as they show, once each, never asking for the permission; the user's own keep the
 *   button. "Hide text" hides an automatic transcript until the button shows it again.
 * - **Kept**: transcripts in [repositories]' `TranscriptRepository`, on the device; which bubbles show their text,
 *   hid it or show it whole survive process death in [savedState]; failures only while the view model lives.
 *
 * Runs in [scope], the view model's, on the main thread; [bubbles] may be collected on another dispatcher.
 */
internal class ChatTranscripts(
    private val chatId: Long,
    private val repositories: TranscriptionRepositories,
    private val history: Flow<ChatHistory>,
    private val savedState: SavedStateHandle,
    private val scope: CoroutineScope,
    private val onEffect: (ChatEffect) -> Unit,
) {
    private val transcriber = repositories.transcriber
    private val files = repositories.files

    /** Whether the device can transcribe, which shows the bubbles' button. */
    val isAvailable: Boolean = transcriber.isAvailable

    /** The messages whose text the user chose to see. */
    private val shown: StateFlow<LongArray> = savedState.getStateFlow(KEY_SHOWN, LongArray(0))

    /** The messages whose automatic transcript the user hid. */
    private val hidden: StateFlow<LongArray> = savedState.getStateFlow(KEY_HIDDEN, LongArray(0))

    /** The long transcripts shown whole. */
    private val expanded: StateFlow<LongArray> = savedState.getStateFlow(KEY_EXPANDED, LongArray(0))

    /** The messages being transcribed. */
    private val pending = MutableStateFlow<PersistentSet<Long>>(persistentSetOf())

    /** The messages that could not be transcribed, with why. */
    private val failures = MutableStateFlow<PersistentMap<Long, BubbleTranscript>>(persistentMapOf())

    /** The transcripts kept for this chat, by message. */
    private val saved: StateFlow<ImmutableMap<Long, Transcript>> =
        repositories.transcripts
            .transcripts(chatId)
            .stateIn(scope, SharingStarted.Lazily, persistentMapOf())

    /** The request of each message in [pending]; only on the main thread. */
    private val jobs = mutableMapOf<Long, Job>()

    /** The messages automatic transcription asked for already, once each per view model. */
    private val attempted = mutableSetOf<Long>()

    /** One recognizer at a time: the device's recognizer works on one audio stream. */
    private val recognizer = Mutex()

    /** The message to transcribe once the microphone permission is granted. */
    private var awaitingPermission: Long? = null

    /** What the bubbles show, by message id: only the bubbles with something to show. */
    val bubbles: Flow<ImmutableMap<Long, BubbleTranscript>> =
        combine(
            combine(shown, hidden, expanded, ::Choices),
            pending,
            failures,
            saved,
        ) { choices, pending, failures, saved ->
            if (!isAvailable && saved.isEmpty()) return@combine persistentMapOf()
            buildMap {
                saved.forEach { (id, transcript) ->
                    val visible = id in choices.shown || (transcript.isAutomatic && id !in choices.hidden)
                    if (visible) {
                        put(
                            id,
                            BubbleTranscript.Shown(
                                text = transcript.text,
                                isAutomatic = transcript.isAutomatic,
                                showsNotice = transcript.isFirst,
                                isExpanded = id in choices.expanded,
                            ),
                        )
                    }
                }
                failures.forEach { (id, failure) -> if (id in choices.shown && id !in saved) put(id, failure) }
                pending.forEach { id ->
                    if (id in choices.shown ||
                        id !in choices.hidden
                    ) {
                        put(id, BubbleTranscript.Transcribing)
                    }
                }
            }.toImmutableMap()
        }.onStart { emit(persistentMapOf()) }
            .distinctUntilChanged()

    fun onEvent(event: TranscriptionEvent) {
        when (event) {
            is TranscriptionEvent.Transcribe -> transcribe(event.messageId)
            is TranscriptionEvent.Hide -> hide(event.messageId)
            is TranscriptionEvent.ShowMore -> setIds(KEY_EXPANDED, expanded.value.toSet() + event.messageId)
            TranscriptionEvent.OpenLanguageSettings -> onEffect(TranscriptionEffect.OpenLanguageSettings)
            is TranscriptionEvent.PermissionAnswered -> onPermission(event.granted)
        }
    }

    /**
     * Messages [ids] show on screen: with automatic transcription on, other people's voice and round video messages
     * among them that have no transcript are transcribed, once each.
     */
    fun onVisible(ids: Collection<Long>) {
        if (!isAvailable || ids.isEmpty()) return
        scope.launch {
            if (!repositories.settings.settings
                    .first()
                    .automatic
            ) {
                return@launch
            }
            val messages = history.first().messages
            ids
                .mapNotNull(messages::findById)
                .filter { !it.isOutgoing && it.isTranscribable && it.id !in saved.value && attempted.add(it.id) }
                .forEach { request(it, automatic = true) }
        }
    }

    private fun transcribe(messageId: Long) {
        setIds(KEY_SHOWN, shown.value.toSet() + messageId)
        setIds(KEY_HIDDEN, hidden.value.toSet() - messageId)
        failures.update { it.removing(messageId) }
        if (messageId in saved.value || messageId in jobs) return
        scope.launch {
            val message = history.first().messages.findById(messageId)
            if (message?.isTranscribable == true) request(message, automatic = false)
        }
    }

    private fun hide(messageId: Long) {
        setIds(KEY_SHOWN, shown.value.toSet() - messageId)
        setIds(KEY_HIDDEN, hidden.value.toSet() + messageId)
        setIds(KEY_EXPANDED, expanded.value.toSet() - messageId)
        failures.update { it.removing(messageId) }
        jobs.remove(messageId)?.cancel()
        pending.update { it.removing(messageId) }
    }

    private fun onPermission(granted: Boolean) {
        val messageId = awaitingPermission
        awaitingPermission = null
        if (granted) {
            messageId?.let(::transcribe)
        } else {
            onEffect(TranscriptionEffect.MicrophoneDenied)
        }
    }

    /** Transcribes [message], unless it is under way. */
    private fun request(
        message: Message,
        automatic: Boolean,
    ) {
        val id = message.id
        val (fileId, duration) = message.audio ?: return
        if (id in jobs) return
        pending.update { it.adding(id) }
        // Registered before it starts, so a request that ends at once (an immediate dispatcher) is not asked again.
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val job = coroutineContext.job
                try {
                    val path = files.downloaded(fileId)
                    val result =
                        if (path == null) {
                            TranscriptionResult.Failed
                        } else {
                            recognizer.withLock { transcriber.transcribe(path, duration) }
                        }
                    onResult(id, result, automatic)
                } finally {
                    if (jobs.remove(id, job)) pending.update { it.removing(id) }
                }
            }
        jobs[id] = job
        job.start()
    }

    private suspend fun onResult(
        messageId: Long,
        result: TranscriptionResult,
        automatic: Boolean,
    ) {
        when (result) {
            is TranscriptionResult.Recognized -> {
                val settings = repositories.settings.settings.first()
                val first = !settings.noticeShown
                val transcript = Transcript(result.text, isAutomatic = automatic, isFirst = first)
                repositories.transcripts.save(chatId, messageId, transcript)
                if (first) repositories.settings.markNoticeShown()
            }

            TranscriptionResult.NeedsMicrophonePermission -> {
                // Asked for only on the user's tap, never for an automatic transcript.
                if (!automatic) {
                    setIds(KEY_SHOWN, shown.value.toSet() - messageId)
                    awaitingPermission = messageId
                    onEffect(TranscriptionEffect.ExplainMicrophone)
                }
            }

            TranscriptionResult.LanguageUnavailable -> {
                failures.update { it.putting(messageId, BubbleTranscript.LanguageUnavailable) }
            }

            TranscriptionResult.Failed -> {
                failures.update { it.putting(messageId, BubbleTranscript.Failed) }
            }
        }
    }

    private fun setIds(
        key: String,
        ids: Set<Long>,
    ) {
        savedState[key] = ids.toLongArray()
    }

    private data class Choices(
        val shown: Set<Long>,
        val hidden: Set<Long>,
        val expanded: Set<Long>,
    ) {
        constructor(shown: LongArray, hidden: LongArray, expanded: LongArray) :
            this(shown.toSet(), hidden.toSet(), expanded.toSet())
    }

    private companion object {
        const val KEY_SHOWN = "transcription.shown"
        const val KEY_HIDDEN = "transcription.hidden"
        const val KEY_EXPANDED = "transcription.expanded"
    }
}

/** Whether the message is a voice or round video message, which can be transcribed. */
internal val Message.isTranscribable: Boolean
    get() = content is MessageContent.VoiceNote || content is MessageContent.VideoNote

/** The audio's file and length in seconds; null for other content. */
private val Message.audio: Pair<Int, Int>?
    get() =
        when (val content = content) {
            is MessageContent.VoiceNote -> content.voiceNote.file.id to content.voiceNote.duration
            is MessageContent.VideoNote -> content.videoNote.file.id to content.videoNote.duration
            else -> null
        }

/** File [fileId]'s path once it is on the device, downloading it at visible priority; null if it cannot be. */
private suspend fun FileRepository.downloaded(fileId: Int): String? {
    var requested = false
    var failed = false
    return observe(fileId)
        .transformWhile { state ->
            if (state is FileState.Downloaded) {
                emit(state.path)
                false
            } else {
                if (!requested && !(state is FileState.Downloading && state.isActive)) {
                    requested = true
                    failed = download(fileId, DownloadPriority.Visible) is Outcome.Failure
                }
                !failed
            }
        }.firstOrNull()
}
