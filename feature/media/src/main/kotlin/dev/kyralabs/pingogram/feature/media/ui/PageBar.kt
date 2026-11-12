package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing

/** What the bottom bar shows of one page: [item]'s caption, and its video's [controls] once it is in the player. */
internal class PageBar(
    val item: MediaItem,
    val controls: VideoPlayback?,
)

/** [bar]'s caption and video controls, a small space apart, and a small space above the toolbar where there is one. */
@Composable
internal fun PageBarContent(
    bar: PageBar,
    onEvent: (MediaViewerEvent) -> Unit,
    canPictureInPicture: Boolean,
    spacedBelow: Boolean,
) {
    val maxWidth = Modifier.widthIn(max = MaterialTheme.sizes.contentMaxWidth)
    val item = bar.item
    val media = item.media
    val hasCaption = item.caption.text.isNotEmpty()
    val controls = bar.controls?.takeIf { media is ViewerMedia.Clip }
    val spacing = MaterialTheme.spacing.small
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = if ((hasCaption || controls != null) && spacedBelow) spacing else 0.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (hasCaption) {
            ExpandableCaption(
                caption = item.caption,
                key = item.messageId,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.windowMarginCompact).then(maxWidth),
            )
        }
        if (controls != null && media is ViewerMedia.Clip) {
            VideoControls(
                playback = controls,
                durationSeconds = media.video.duration,
                onEvent = onEvent,
                canPictureInPicture = canPictureInPicture,
                modifier = Modifier.padding(horizontal = MaterialTheme.spacing.small).then(maxWidth),
            )
        }
    }
}
