package dev.kyralabs.pingogram.feature.chat.ui.transcription

import dev.kyralabs.pingogram.feature.chat.ui.ChatContent
import dev.kyralabs.pingogram.feature.chat.ui.ChatItem
import dev.kyralabs.pingogram.feature.chat.ui.ChatUiState
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList

/**
 * The state with each bubble of [transcripts] showing its transcript, and whether the device can transcribe
 * ([canTranscribe], the Transcribe button); the items of every other bubble stay as they are, so only the bubbles that
 * change compose again.
 */
internal fun ChatUiState.withTranscripts(
    transcripts: ImmutableMap<Long, BubbleTranscript>,
    canTranscribe: Boolean,
): ChatUiState {
    val state = if (canTranscribe != this.canTranscribe) copy(canTranscribe = canTranscribe) else this
    val content = state.content as? ChatContent.Content
    if (transcripts.isEmpty() || content == null) return state
    var changed = false
    val items =
        content.items.map { item ->
            val transcript = (item as? ChatItem.Bubble)?.let { transcripts[it.id] }
            if (item !is ChatItem.Bubble || transcript == null) {
                item
            } else {
                changed = true
                item.copy(transcript = transcript)
            }
        }
    return if (changed) state.copy(content = content.copy(items = items.toImmutableList())) else state
}
