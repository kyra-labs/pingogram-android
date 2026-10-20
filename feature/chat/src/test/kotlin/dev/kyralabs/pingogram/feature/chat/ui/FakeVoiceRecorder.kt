package dev.kyralabs.pingogram.feature.chat.ui

import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceFormat
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecorder

/**
 * The voice recorder: it records nothing, answers what tests set ([format], [starts], [stops], the [levels] in turn,
 * then the last one), and records what it was asked as `start:<path>:<format>`, `pause`, `resume`, `stop`, `cancel`
 * and `release`, in order ([calls]; the level reads are left out).
 */
internal class FakeVoiceRecorder : VoiceRecorder {
    var format = VoiceFormat.OggOpus
    var starts = true
    var stops = true
    var levels = listOf(0.5f)
    private var levelReads = 0

    val calls = mutableListOf<String>()

    override suspend fun format(): VoiceFormat = format

    override suspend fun start(
        path: String,
        format: VoiceFormat,
    ): Boolean {
        calls += "start:$path:$format"
        return starts
    }

    override suspend fun pause() {
        calls += "pause"
    }

    override suspend fun resume() {
        calls += "resume"
    }

    override suspend fun level(): Float = levels[minOf(levelReads++, levels.lastIndex)]

    override suspend fun stop(): Boolean {
        calls += "stop"
        return stops
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun release() {
        calls += "release"
    }
}
