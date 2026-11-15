package dev.kyralabs.pingogram.feature.media.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.FileState
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.media.R
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * A video that plays as it loads (`docs/adr/0028-streaming-media.md`, board 25), until its first frame: the download's
 * determinate wavy ring on a `MaterialShapes.Cookie9Sided` of the media overlay, over "Streaming · 34%" and
 * "18.4 of 54 MB · plays as it loads" on a pill of it, and inside the ring a cancel button ([onCancel], "Stop loading")
 * that stops the stream: the video then waits for a tap to play. The text follows each whole percent; screen readers
 * hear it in quarters, politely. Nothing for a file already on the device.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun StreamingStatus(
    fileId: Int,
    state: FileState?,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (state !is FileState.Downloading) return
    val lookup = LocalFileProgress.current
    val progress = { progressOf(fileId, state, lookup) }
    val percent by remember(fileId, state, lookup) { derivedStateOf { (progress() * PERCENT).toInt() } }
    val quarter by remember(fileId, state, lookup) { derivedStateOf { (progress() * QUARTERS).toInt() } }
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val format = remember(locale) { NumberFormat.getPercentInstance(locale) }
    val title = stringResource(R.string.media_streaming, format.format(percent / PERCENT.toDouble()))
    val loaded = Formatter.formatShortFileSize(context, state.size * percent / PERCENT)
    val total = Formatter.formatShortFileSize(context, state.size)
    val detail =
        if (state.size > 0) stringResource(R.string.media_streaming_sizes, loaded, total) else null
    val spoken = stringResource(R.string.media_streaming, format.format(quarter / QUARTERS.toDouble()))
    val colors = MaterialTheme.pingogramColors
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        Box(
            modifier =
                Modifier
                    .background(colors.mediaOverlay, MaterialShapes.Cookie9Sided.toShape())
                    .padding(MaterialTheme.spacing.medium),
            contentAlignment = Alignment.Center,
        ) {
            CircularWavyProgressIndicator(
                progress = progress,
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
            )
            if (onCancel != null) {
                IconButton(
                    onClick = onCancel,
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = colors.onMediaOverlay),
                ) {
                    Icon(
                        painterResource(UiR.drawable.ic_close),
                        contentDescription = stringResource(R.string.media_streaming_cancel),
                    )
                }
            }
        }
        StatusText(
            title,
            detail,
            Modifier.clearAndSetSemantics {
                contentDescription = spoken
                liveRegion = LiveRegionMode.Polite
            },
        )
    }
}

/** "Streaming · 33%" over [detail] on a pill of the media overlay. */
@Composable
private fun StatusText(
    title: String,
    detail: String?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.pingogramColors
    Column(
        modifier =
            modifier
                .background(colors.mediaOverlay, MaterialTheme.shapes.large)
                .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            color = colors.onMediaOverlay,
            style = MaterialTheme.typography.labelLargeEmphasized.copy(fontFeatureSettings = "tnum"),
            textAlign = TextAlign.Center,
        )
        if (detail != null) {
            Text(
                detail,
                color = colors.onMediaOverlay,
                style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val PERCENT = 100

/** Screen readers hear a stream's progress in quarters (board 25). */
private const val QUARTERS = 4
