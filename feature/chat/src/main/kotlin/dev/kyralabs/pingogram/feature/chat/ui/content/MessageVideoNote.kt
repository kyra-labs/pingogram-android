package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import dev.kyralabs.pingogram.core.domain.model.MessageContent
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.transcription.MessageTranscription
import dev.kyralabs.pingogram.feature.chat.ui.transcription.TranscribeButton
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A round video message (`REQUIREMENTS.md` FR-C4), bare ([isBare]): its thumbnail in a `videoNote` circle with a
 * [PlayBadge] in the middle and its length at the bottom, with a dot while nobody has watched it yet.
 * Self-destructing ones show only the blurred minithumbnail. Playback comes with the media viewer: tapping it calls
 * [onOpen].
 *
 * Where the device can transcribe ([transcription]), the Transcribe button sits on the circle's top end; the
 * transcript shows in a bubble under it (`VideoNoteTranscript`).
 *
 * Screen readers hear "Video message, 0:12".
 */
@Composable
internal fun MessageVideoNote(
    content: MessageContent.VideoNote,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    transcription: MessageTranscription? = null,
) {
    val videoNote = content.videoNote
    val side = MaterialTheme.sizes.videoNote
    val duration = mediaDurationText(videoNote.duration)
    val description =
        stringResource(R.string.content_kind_and_details, stringResource(UiR.string.preview_video_message), duration)
            .let {
                if (content.isViewed) {
                    it
                } else {
                    stringResource(R.string.content_kind_and_details, it, stringResource(R.string.content_not_played))
                }
            }
    Box(modifier) {
        Box(
            modifier =
                Modifier
                    .size(side)
                    .clip(CircleShape)
                    .clickable(onClickLabel = stringResource(R.string.content_open), onClick = onOpen)
                    .semantics { contentDescription = description },
        ) {
            VideoThumbnail(
                still = videoNote.still(),
                displaySize = DpSize(side, side),
                hidden = content.isSecret,
                modifier = Modifier.matchParentSize(),
            )
            Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
                PlayBadge(Modifier.align(Alignment.Center))
                MediaLabel(
                    text = duration,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = MaterialTheme.spacing.large),
                    dot = !content.isViewed,
                )
            }
        }
        // The Transcribe button on the circle's top end, clear of the time at its bottom end (board 28c).
        if (transcription?.showsButton == true) {
            TranscribeButton(
                transcription = transcription,
                colors = MaterialTheme.pingogramColors.incomingBubble,
                isVideo = true,
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
    }
}
