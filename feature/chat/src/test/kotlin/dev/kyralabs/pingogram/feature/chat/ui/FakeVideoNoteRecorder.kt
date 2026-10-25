package dev.kyralabs.pingogram.feature.chat.ui

import androidx.camera.core.SurfaceRequest
import dev.kyralabs.pingogram.core.domain.model.AttachmentThumbnail
import dev.kyralabs.pingogram.feature.chat.ui.videonote.RecordedVideo
import dev.kyralabs.pingogram.feature.chat.ui.videonote.VideoNoteRecorder
import dev.kyralabs.pingogram.feature.chat.ui.videonote.VideoNoteStart
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The video recorder: it records nothing and shows no preview, answers what tests set ([hasCamera], [starts] with two
 * cameras or one ([canFlip]), [flips], [stops], the [level]), and records what it was asked as
 * `start:<path>:front|back`, `flip:front|back`, `pause`, `resume`, `stop:<thumbnail>`, `cancel` and `release`, in
 * order ([calls]; the level reads are left out).
 */
internal class FakeVideoNoteRecorder : VideoNoteRecorder {
    override var hasCamera = true
    var starts = true
    var canFlip = true
    var flips = true
    var stops: RecordedVideo? = RecordedVideo(LENGTH, AttachmentThumbnail(THUMBNAIL, 320, 320))
    var level = 0.4f

    val calls = mutableListOf<String>()

    override val preview = MutableStateFlow<SurfaceRequest?>(null)

    override suspend fun start(
        path: String,
        front: Boolean,
    ): VideoNoteStart? {
        calls += "start:$path:${lens(front)}"
        return if (starts) VideoNoteStart(front = front, canFlip = canFlip) else null
    }

    override suspend fun flip(front: Boolean): Boolean {
        calls += "flip:${lens(front)}"
        return flips
    }

    override suspend fun pause() {
        calls += "pause"
    }

    override suspend fun resume() {
        calls += "resume"
    }

    override suspend fun level(): Float = level

    override suspend fun stop(thumbnail: String): RecordedVideo? {
        calls += "stop:$thumbnail"
        return stops
    }

    override fun cancel() {
        calls += "cancel"
    }

    override fun release() {
        calls += "release"
    }

    private fun lens(front: Boolean) = if (front) "front" else "back"

    companion object {
        const val LENGTH = 480
        const val THUMBNAIL = "/cache/uploads/video/video_note.jpg"
    }
}
