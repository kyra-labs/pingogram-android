package dev.kyralabs.pingogram.feature.chat.ui.videonote

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.CameraRationale
import dev.kyralabs.pingogram.feature.chat.ui.voice.RecordModeUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.VideoRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.VoiceRecordingUiState
import dev.kyralabs.pingogram.feature.chat.ui.voice.previewMeter

// Round video messages (docs/adr/0032-round-video-messages.md): the composer's camera button, its recording held,
// locked and stopped, the round preview on its placeholder frame, and the rationale before Android asks and once it
// no longer does.

private val VIDEO = RecordModeUiState(video = true, canSwitch = true)

@Composable
private fun VideoComposer(recording: VoiceRecordingUiState?) {
    ChatBottomBar(
        composer = ComposerUiState.Available(canSchedule = true),
        text = remember { ComposerText() },
        onSend = {},
        onFocusChanged = {},
        recording = recording?.copy(meter = previewMeter()),
        recordMode = VIDEO,
    )
}

@PingogramPreviews
@Composable
private fun ComposerCameraPreview() {
    PingogramPreview { VideoComposer(recording = null) }
}

@PingogramPreviews
@Composable
private fun ComposerVideoHeldPreview() {
    PingogramPreview { VideoComposer(VoiceRecordingUiState(video = VideoRecordingUiState(canFlip = true))) }
}

@PingogramPreviews
@Composable
private fun ComposerVideoLockedPreview() {
    PingogramPreview {
        VideoComposer(VoiceRecordingUiState(isLocked = true, video = VideoRecordingUiState(canFlip = true)))
    }
}

@PingogramPreviews
@Composable
private fun ComposerVideoStoppedPreview() {
    PingogramPreview {
        VideoComposer(
            VoiceRecordingUiState(isLocked = true, isPaused = true, video = VideoRecordingUiState(isStopped = true)),
        )
    }
}

@PingogramPreviews
@Composable
private fun RoundVideoFramePreview() {
    PingogramPreview {
        RoundVideoFrame(
            recording = VoiceRecordingUiState(meter = previewMeter(), video = VideoRecordingUiState()),
            surfaceRequest = null,
            side = MaterialTheme.sizes.illustrationMaxSize,
        )
    }
}

@PingogramPreviews
@Composable
private fun VideoNotePermissionPreview() {
    PingogramPreview { VideoNotePermissionContent(CameraRationale(), onNotNow = {}, onContinue = {}) }
}

@PingogramPreviews
@Composable
private fun VideoNotePermissionDeniedPreview() {
    PingogramPreview {
        VideoNotePermissionContent(CameraRationale(deniedForGood = true), onNotNow = {}, onContinue = {})
    }
}
