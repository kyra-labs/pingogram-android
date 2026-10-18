package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.runtime.mutableFloatStateOf
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.onFailure
import dev.kyralabs.pingogram.core.domain.model.ChatHistory
import dev.kyralabs.pingogram.core.domain.model.DownloadPriority
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.domain.model.Message
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.domain.repository.FileRepository
import dev.kyralabs.pingogram.feature.chat.ui.content.Playback
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioKind
import dev.kyralabs.pingogram.feature.chat.ui.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch

/**
 * The voice messages and songs of a conversation as they play, for [ChatViewModel] (`REQUIREMENTS.md` FR-C4): one at
 * a time, on an [AudioPlayer], from files [files] downloads. [state] is the message in the player.
 *
 * - **Play and pause** ([playPause]) toggle the message in the player; another message stops it and plays instead.
 *   A file that is not on the device downloads at visible priority first and plays once it is there; the message
 *   shows as playing meanwhile, and pausing it stops the waiting, not the download.
 * - **Seek** ([seek]) moves the message in the player to a fraction of its length, shown at once; another message
 *   starts playing from there.
 * - **Next**: a voice message that ends plays the next newer voice message of the loaded [history], as Telegram does.
 *   Songs stop at their end.
 * - **Progress**: while playing, the position goes into snapshot state every [TICK_MILLIS], which [Playback.progress]
 *   reads. Only the playing message's waveform or progress bar reads it, in the draw phase, so a tick neither changes
 *   [state] nor recomposes anything. Nothing ticks while paused: this is a UI ticker during playback, not polling.
 * - [pause] when the screen stops, as nothing plays in the background; [release] when the view model is cleared.
 *
 * Main thread only, like the player. A failed download goes to [onError]; a file the player cannot play to
 * [onPlaybackFailed], logged without user data.
 */
@Suppress("LongParameterList") // The player, where files and messages come from, and where failures go.
internal class ChatPlayback(
    private val player: AudioPlayer,
    private val files: FileRepository,
    private val scope: CoroutineScope,
    private val history: suspend () -> ChatHistory,
    private val onError: (DomainError) -> Unit,
    private val onPlaybackFailed: () -> Unit,
    private val log: ChatLog,
) {
    private val mutableState = MutableStateFlow<MessagePlayback?>(null)

    /** The message in the player, playing or paused, or on its way there; null when none is. */
    val state: StateFlow<MessagePlayback?> = mutableState.asStateFlow()

    private var session: Session? = null
    private var ticker: Job? = null

    /** What the player does by itself: pause or play on, reach the end, fail. */
    private val listener =
        object : AudioPlayer.Listener {
            override fun onPlayingChanged(isPlaying: Boolean) {
                val current = session?.takeIf { it.media != null && it.isPlaying != isPlaying } ?: return
                current.isPlaying = isPlaying
                if (isPlaying) startTicker(current) else stopTicker(current)
                publish()
            }

            override fun onEnded() {
                val ended = session?.takeIf { it.media != null } ?: return
                val isVoiceNote = ended.media?.kind == AudioKind.Speech
                stop()
                if (isVoiceNote) {
                    scope.launch {
                        val next = history().nextVoiceNoteAfter(ended.messageId)
                        // Unless the user played something else meanwhile.
                        if (next != null && session == null) start(next.id, startFraction = 0f)
                    }
                }
            }

            override fun onError() {
                log.warn("Playback failed: ${session?.media?.kind}")
                stop()
                onPlaybackFailed()
            }
        }

    init {
        player.listener = listener
    }

    /** Plays or pauses message [messageId]; another message in the player stops. */
    fun playPause(messageId: Long) {
        val current = session
        when {
            current == null || current.messageId != messageId -> start(messageId, startFraction = 0f)
            current.isPlaying -> pause()
            else -> resume(current)
        }
    }

    /**
     * Plays message [messageId] from [fraction] (0–1) of its length: moves there at once if it is in the player, else
     * starts it there.
     */
    fun seek(
        messageId: Long,
        fraction: Float,
    ) {
        val target = fraction.coerceIn(0f, 1f)
        val current = session
        if (current == null || current.messageId != messageId) {
            start(messageId, target)
            return
        }
        current.progress.floatValue = target
        // On its way to the player, it starts from there.
        val media = current.media ?: return
        player.seekTo((target * player.lengthOf(media)).toLong())
    }

    /** Pauses what plays, and stops waiting for a file to play. */
    fun pause() {
        val current = session ?: return
        if (current.media == null) {
            stop()
        } else if (current.isPlaying) {
            player.pause()
            current.isPlaying = false
            stopTicker(current)
            publish()
        }
    }

    /** Stops and frees the player for good. */
    fun release() {
        stop()
        player.listener = null
        player.release()
    }

    /**
     * Stops what was in the player and brings message [messageId] there: once its file is on the device, it plays
     * from [startFraction] of its length, unless it was paused or replaced meanwhile. Nothing plays for a message that
     * is not loaded or not a voice message or song, or whose download fails.
     */
    private fun start(
        messageId: Long,
        startFraction: Float,
    ) {
        stop(publish = false)
        val session = Session(messageId, startFraction)
        this.session = session
        publish()
        session.preparing =
            scope.launch {
                val media =
                    history()
                        .messages
                        .findById(messageId)
                        ?.content
                        ?.playable
                val file = media?.let { files.downloaded(it.fileId, onError) }
                if (media == null || file == null) {
                    if (this@ChatPlayback.session === session) stop()
                    return@launch
                }
                session.preparing = null
                session.media = media
                player.play(file.path, media.kind, (session.progress.floatValue * media.durationMillis).toLong())
                startTicker(session)
            }
    }

    private fun resume(current: Session) {
        if (current.media == null) return
        player.resume()
        current.isPlaying = true
        startTicker(current)
        publish()
    }

    /** Ends the session: stops waiting for its file, the ticker, and the player. */
    private fun stop(publish: Boolean = true) {
        val current = session ?: return
        session = null
        current.preparing?.cancel()
        ticker?.cancel()
        ticker = null
        if (current.media != null) player.stop()
        if (publish) publish()
    }

    private fun startTicker(session: Session) {
        ticker?.cancel()
        ticker =
            scope.launch {
                while (true) {
                    session.progress.floatValue = player.playedFraction(session)
                    delay(TICK_MILLIS)
                }
            }
    }

    private fun stopTicker(session: Session) {
        ticker?.cancel()
        ticker = null
        session.progress.floatValue = player.playedFraction(session)
    }

    private fun publish() {
        mutableState.value = session?.let { MessagePlayback(it.messageId, Playback(it.isPlaying, it.readProgress)) }
    }

    private companion object {
        /** How often the position goes into the progress while playing: about every second frame at 60 Hz. */
        const val TICK_MILLIS = 32L
    }
}

/**
 * Message [messageId] in the player, or on its way there while [media] is null: playing (or about to) while
 * [isPlaying], and how far, in snapshot state of its own, so a message that leaves the player keeps no reader.
 */
private class Session(
    val messageId: Long,
    startFraction: Float,
) {
    val progress = mutableFloatStateOf(startFraction)
    val readProgress: () -> Float = { progress.floatValue }
    var isPlaying = true
    var media: Playable? = null
    var preparing: Job? = null
}

/** What the player needs of a voice message or song: its file, what kind of audio it is, and its length. */
private class Playable(
    val fileId: Int,
    val kind: AudioKind,
    val durationMillis: Long,
)

private val MessageContent.playable: Playable?
    get() =
        when (this) {
            is MessageContent.VoiceNote -> Playable(voiceNote.file.id, AudioKind.Speech, voiceNote.duration * MILLIS)
            is MessageContent.Audio -> Playable(audio.file.id, AudioKind.Music, audio.duration * MILLIS)
            else -> null
        }

/** The player's length of [media], or the message's until the player knows it. */
private fun AudioPlayer.lengthOf(media: Playable): Long = durationMillis ?: media.durationMillis

/** How much of [session] has played, 0–1; what it shows while it is not in the player yet. */
private fun AudioPlayer.playedFraction(session: Session): Float {
    val media = session.media ?: return session.progress.floatValue
    val length = lengthOf(media)
    return if (length > 0) (positionMillis.toFloat() / length).coerceIn(0f, 1f) else 0f
}

/**
 * File [fileId] once it is on the device, downloading it at visible priority first unless it already downloads; null
 * if the download cannot start, which goes to [onError].
 */
private suspend fun FileRepository.downloaded(
    fileId: Int,
    onError: (DomainError) -> Unit,
): FileState.Downloaded? {
    var requested = false
    var failure: DomainError? = null
    val file =
        observe(fileId)
            .transformWhile { state ->
                if (state is FileState.Downloaded) {
                    emit(state)
                    false
                } else {
                    if (!requested && !(state is FileState.Downloading && state.isActive)) {
                        requested = true
                        download(fileId, DownloadPriority.Visible).onFailure { failure = it }
                    }
                    failure == null
                }
            }.firstOrNull()
    failure?.let(onError)
    return file
}

/** The voice message that follows [messageId] in time: the oldest newer one, as the history is newest first. */
private fun ChatHistory.nextVoiceNoteAfter(messageId: Long): Message? =
    messages.lastOrNull { it.id > messageId && it.content is MessageContent.VoiceNote }

private const val MILLIS = 1000L
