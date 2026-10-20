package dev.kyralabs.pingogram.feature.chat.ui.voice

/**
 * Records voice messages from the microphone into files, one at a time: the platform side of a conversation's voice
 * recording (`ChatRecorder`), behind an interface so tests use a fake. [MediaVoiceRecorder] in the app. Called from the
 * main thread; every call that touches the platform's recorder suspends while it runs elsewhere, so none blocks a
 * frame. Public only because `ChatViewModel`'s injected constructor takes it.
 */
interface VoiceRecorder {
    /** The format the device records voice messages in, whose extension the file to record into needs. */
    suspend fun format(): VoiceFormat

    /**
     * Starts recording into the empty file at [path] in [format], taking the audio focus. False when the microphone
     * cannot be used (another app records, the permission is missing): nothing is recorded then.
     */
    suspend fun start(
        path: String,
        format: VoiceFormat,
    ): Boolean

    /** Pauses the recording; the file goes on where it stopped on [resume]. */
    suspend fun pause()

    suspend fun resume()

    /** How loud the microphone was at its loudest since the last call, from 0 (silent) to 1; 0 while not recording. */
    suspend fun level(): Float

    /** Ends the recording and finishes its file. False when the file holds no audio (too short, or it failed). */
    suspend fun stop(): Boolean

    /** Ends the recording and gives up its file, which the caller deletes; returns at once. Nothing if none runs. */
    fun cancel()

    /** Frees the recorder for good, ending a recording as [cancel] does. */
    fun release()
}

/**
 * How a voice message is recorded (`docs/adr/0003-voice-recording.md`): Ogg Opus, Telegram's own voice note format,
 * where the platform has an Opus encoder (Android 10 and later); AAC in an MP4 (`.m4a`) container before that, which
 * Telegram also takes as a voice note. [extension] is the file's: TDLib reads its type from it.
 */
enum class VoiceFormat(
    val extension: String,
) {
    OggOpus("ogg"),
    Aac("m4a"),
}
