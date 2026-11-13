package dev.kyralabs.pingogram.feature.chat.ui.voice

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.chat.ui.composer.ChatBottomBar
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerText
import dev.kyralabs.pingogram.feature.chat.ui.composer.ComposerUiState
import kotlinx.collections.immutable.toImmutableList

// The composer and its recording bar while they record a voice message: held under the finger, locked, and locked
// and paused.

@PingogramPreviews
@Composable
private fun ComposerRecordingPreview() {
    PingogramPreview {
        ChatBottomBar(
            composer = ComposerUiState.Available(canSchedule = true),
            text = remember { ComposerText() },
            onSend = {},
            onFocusChanged = {},
            recording = VoiceRecordingUiState(meter = previewMeter()),
        )
    }
}

@PingogramPreviews
@Composable
private fun ComposerRecordingLockedPreview() {
    PingogramPreview {
        ChatBottomBar(
            composer = ComposerUiState.Available(canSchedule = true),
            text = remember { ComposerText() },
            onSend = {},
            onFocusChanged = {},
            recording = VoiceRecordingUiState(isLocked = true, meter = previewMeter()),
        )
    }
}

@PingogramPreviews
@Composable
private fun ComposerRecordingPausedPreview() {
    PingogramPreview {
        ChatBottomBar(
            composer = ComposerUiState.Available(canSchedule = true),
            text = remember { ComposerText() },
            onSend = {},
            onFocusChanged = {},
            recording = VoiceRecordingUiState(isLocked = true, isPaused = true, meter = previewMeter()),
        )
    }
}

@PingogramPreviews
@Composable
private fun RecordingBarHeldPreview() {
    PingogramPreview {
        RecordingBar(VoiceRecordingUiState(meter = previewMeter()), RecordDrag(), {})
    }
}

@PingogramPreviews
@Composable
private fun RecordingBarLockedPreview() {
    PingogramPreview {
        RecordingBar(VoiceRecordingUiState(isLocked = true, meter = previewMeter()), RecordDrag(), {})
    }
}

@PingogramPreviews
@Composable
private fun RecordingBarPausedPreview() {
    PingogramPreview {
        RecordingBar(VoiceRecordingUiState(isLocked = true, isPaused = true, meter = previewMeter()), RecordDrag(), {})
    }
}

@PingogramPreviews
@Composable
private fun RecordingBarTrimmedPreview() {
    PingogramPreview {
        RecordingBar(
            VoiceRecordingUiState(isLocked = true, isPaused = true, meter = previewMeter(), trim = previewTrim()),
            RecordDrag(),
            {},
        )
    }
}

/** The 0:07 of [previewMeter], trimmed to 0:01–0:06, for previews and goldens. */
internal fun previewTrim(): VoiceTrim =
    VoiceTrim(
        List(PREVIEW_TRIM_LEVELS) { index -> PREVIEW_LEVELS[index % PREVIEW_LEVELS.size] }.toImmutableList(),
        PREVIEW_DURATION,
        startMillis = PREVIEW_START,
        endMillis = PREVIEW_END,
    )

private const val PREVIEW_DURATION = 7_400L
private const val PREVIEW_START = 1_500L
private const val PREVIEW_END = 6_200L

/** 0:07 of ticks. */
private const val PREVIEW_TRIM_LEVELS = 148

/** A recording of 0:07 with a few seconds of speech-like levels, for previews and goldens. */
internal fun previewMeter(): RecordingMeter =
    RecordingMeter(
        elapsedMillis = 7_400L,
        recent = FloatArray(RecordingMeter.RECENT_LEVELS) { index -> PREVIEW_LEVELS[index % PREVIEW_LEVELS.size] },
    )

private val PREVIEW_LEVELS =
    floatArrayOf(0.1f, 0.35f, 0.6f, 0.8f, 0.55f, 0.3f, 0.7f, 0.9f, 0.65f, 0.4f, 0.2f, 0.05f, 0.25f, 0.5f, 0.75f, 0.45f)
