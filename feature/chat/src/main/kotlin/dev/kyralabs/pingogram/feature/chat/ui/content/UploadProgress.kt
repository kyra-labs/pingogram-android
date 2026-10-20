package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.feature.chat.ui.attach.MessageUpload
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The upload of a photo, video or GIF being sent, drawn over it (`REQUIREMENTS.md` FR-C5): a wavy ring around a cancel
 * button ([onCancel]), in `onMediaOverlay` on a `mediaOverlay` circle, so it stays legible on any picture. The ring is
 * determinate once the upload has a progress, which moves on the motion scheme's default effects spring and is read in
 * the draw phase; indeterminate before.
 */
@Composable
internal fun MediaUploadProgress(
    upload: MessageUpload,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    UploadRing(
        upload = upload,
        color = colors.onMediaOverlay,
        trackColor = Color.Transparent,
        onCancel = onCancel,
        modifier = modifier.background(colors.mediaOverlay, CircleShape),
    )
}

/**
 * The upload of [upload] as a [CircularWavyProgressIndicator] in [color] on [trackColor] around a cancel [IconButton]
 * ([onCancel]); determinate once there is a progress, which moves on the motion scheme's default effects spring.
 */
@Composable
internal fun UploadRing(
    upload: MessageUpload,
    color: Color,
    trackColor: Color,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val target = upload.progress
    val progress by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "uploadProgress",
    )
    Box(modifier.size(WavyProgressIndicatorDefaults.CircularContainerSize), contentAlignment = Alignment.Center) {
        if (target == null) {
            CircularWavyProgressIndicator(Modifier.matchParentSize(), color = color, trackColor = trackColor)
        } else {
            CircularWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier.matchParentSize(),
                color = color,
                trackColor = trackColor,
            )
        }
        IconButton(onClick = onCancel, colors = IconButtonDefaults.iconButtonColors(contentColor = color)) {
            Icon(
                painter = painterResource(UiR.drawable.ic_close),
                contentDescription = stringResource(R.string.attach_cancel_upload),
            )
        }
    }
}

/**
 * The download button of a photo that automatic download leaves for a tap (`docs/PERFORMANCE.md` §7), drawn over it
 * like [MediaUploadProgress]: a download [IconButton] in `onMediaOverlay` on a `mediaOverlay` circle ([onDownload]);
 * while [isDownloading], an indeterminate wavy ring around a cancel button ([onCancel]) instead.
 */
@Composable
internal fun MediaDownloadButton(
    isDownloading: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    Box(
        modifier
            .size(WavyProgressIndicatorDefaults.CircularContainerSize)
            .background(colors.mediaOverlay, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isDownloading) {
            CircularWavyProgressIndicator(
                Modifier.matchParentSize(),
                color = colors.onMediaOverlay,
                trackColor = Color.Transparent,
            )
        }
        IconButton(
            onClick = if (isDownloading) onCancel else onDownload,
            colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onMediaOverlay),
        ) {
            Icon(
                painter = painterResource(if (isDownloading) UiR.drawable.ic_close else UiR.drawable.ic_download),
                contentDescription =
                    stringResource(if (isDownloading) R.string.content_cancel_download else R.string.content_download),
            )
        }
    }
}
