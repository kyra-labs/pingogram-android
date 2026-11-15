package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R

/** Whether this item is a live photo: a photo with its motion (td_api.tl `messagePhoto.video`), not hidden. */
internal val MediaItem.isLivePhoto: Boolean get() = (media as? ViewerMedia.Image)?.motion != null && !isHidden

/**
 * "LIVE" over a live photo in the viewer (board 25): a ring and the word in `labelSmallEmphasized`, in `onMediaOverlay`
 * on a full `mediaOverlay` pill, as the bubble's badge. Decorative: the page's description says it is a live photo.
 */
@Composable
internal fun LiveBadge(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.pingogramColors
    Row(
        modifier =
            modifier
                .clearAndSetSemantics {}
                .background(colors.mediaOverlay, CircleShape)
                .padding(horizontal = MaterialTheme.spacing.small, vertical = MaterialTheme.spacing.extraSmall),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(MaterialTheme.sizes.badgeIcon)
                .border(MaterialTheme.sizes.avatarRing, colors.onMediaOverlay, CircleShape),
        )
        Text(
            text = stringResource(R.string.media_live_badge),
            color = colors.onMediaOverlay,
            style = MaterialTheme.typography.labelSmallEmphasized,
        )
    }
}
