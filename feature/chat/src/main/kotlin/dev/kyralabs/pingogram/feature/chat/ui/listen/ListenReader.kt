package dev.kyralabs.pingogram.feature.chat.ui.listen

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.SpeechStart
import dev.kyralabs.pingogram.core.domain.repository.PlaybackSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.SpeechSession
import dev.kyralabs.pingogram.core.domain.repository.TextSpeaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Reads posts aloud in [scope] for Listen to posts (`REQUIREMENTS.md` FR-X26): each [read] opens a session of the
 * device's speech engine ([TextSpeaker], offline voices only) and reads the posts in turn at the voice messages' speed
 * ([PlaybackSettingsRepository]), while the [player] pill shows where it is. A paused post starts again from its
 * beginning; reading ends after the last post, when the engine fails, on [stop], or on [close], which the view model
 * calls as it is cleared, so it never outlives the chat. What it cannot read comes as [notices].
 */
internal class ListenReader(
    private val scope: CoroutineScope,
    private val speaker: TextSpeaker,
    private val playback: PlaybackSettingsRepository,
) {
    private val shownPlayer = MutableStateFlow<ListenPlayer?>(null)
    val player: StateFlow<ListenPlayer?> = shownPlayer.asStateFlow()

    private val noticeChannel = Channel<ListenNotice>(Channel.BUFFERED)
    val notices: Flow<ListenNotice> = noticeChannel.receiveAsFlow()

    private var posts: List<String> = emptyList()
    private var session: SpeechSession? = null
    private var reading: Job? = null

    /** Stops what is read now, then reads the posts [load] gives, or says why it cannot. */
    fun read(load: suspend () -> Outcome<List<String>>) {
        stop()
        reading =
            scope.launch {
                when (val loaded = load()) {
                    is Outcome.Failure -> noticeChannel.send(ListenNotice.NotLoaded)
                    is Outcome.Success -> begin(loaded.value)
                }
            }
    }

    fun pause() {
        reading?.cancel()
        shownPlayer.update { it?.copy(isPaused = true) }
    }

    fun resume() {
        shownPlayer.value?.let { readAfter(it.position - 1) }
    }

    /** Skips to the next post (staying paused if paused); after the last one, stops. */
    fun next() {
        val shown = shownPlayer.value ?: return
        when {
            !shown.hasNext -> stop()
            shown.isPaused -> shownPlayer.value = shown.copy(position = shown.position + 1)
            else -> readAfter(shown.position)
        }
    }

    /** Reads at the voice messages' next speed, saved for them too; a post being read starts again at it. */
    fun changeSpeed() {
        scope.launch {
            val speed = playback.playbackSpeed.first().next
            playback.setPlaybackSpeed(speed)
            val shown = shownPlayer.value ?: return@launch
            shownPlayer.value = shown.copy(speed = speed)
            if (!shown.isPaused) readAfter(shown.position - 1)
        }
    }

    fun stop() {
        reading?.cancel()
        reading = null
        close()
        posts = emptyList()
        shownPlayer.value = null
    }

    /** Releases the speech engine, on the main thread. */
    fun close() {
        session?.close()
        session = null
    }

    /** Reads [texts] with a new session of the speech engine; nothing to read opens none. */
    private suspend fun begin(texts: List<String>) {
        when (val started = if (texts.isEmpty()) null else speaker.open()) {
            null -> {
                noticeChannel.send(ListenNotice.NothingToRead)
            }

            is SpeechStart.Ready -> {
                session = started.session
                posts = texts
                readFrom(0)
            }

            SpeechStart.NoOfflineVoice -> {
                noticeChannel.send(ListenNotice.NoOfflineVoice)
            }

            SpeechStart.Unavailable -> {
                noticeChannel.send(ListenNotice.Unavailable)
            }
        }
    }

    /** Reads from post [first] (from 0) to the last one, then stops; a cancelled read keeps the session. */
    private suspend fun readFrom(first: Int) {
        val reader = session ?: return
        var index = first
        var read = true
        while (index < posts.size && read) {
            val speed = playback.playbackSpeed.first()
            shownPlayer.value = ListenPlayer(index + 1, posts.size, isPaused = false, speed)
            read = reader.speak(posts[index], speed.factor)
            index++
        }
        if (!read) noticeChannel.send(ListenNotice.Unavailable)
        // Cancels this read itself, which has nothing left to do.
        stop()
    }

    /** Reads from post [first] once the read before has stopped, so its engine's stop cannot cut this one. */
    private fun readAfter(first: Int) {
        val before = reading
        reading =
            scope.launch {
                before?.cancelAndJoin()
                readFrom(first)
            }
    }
}
