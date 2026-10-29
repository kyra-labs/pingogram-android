package dev.kyralabs.pingogram.feature.chat.ui.videonote

import dev.kyralabs.pingogram.core.domain.repository.ComposerSettingsRepository
import dev.kyralabs.pingogram.core.domain.repository.VideoNoteRepository
import javax.inject.Inject

/**
 * What a conversation records round video messages with (`docs/adr/0032-round-video-messages.md`), handed to
 * `ChatViewModel` as one: the camera's [recorder], where the files go and how they are sent ([repository]), and
 * whether the record button records video ([settings]' `recordsVideoMessages`, which a tap on the button switches).
 */
class VideoNoteRecording
    @Inject
    constructor(
        val recorder: VideoNoteRecorder,
        val repository: VideoNoteRepository,
        val settings: ComposerSettingsRepository,
    )
