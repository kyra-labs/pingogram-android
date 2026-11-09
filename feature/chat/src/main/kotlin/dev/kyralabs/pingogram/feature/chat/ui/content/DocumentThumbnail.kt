package dev.kyralabs.pingogram.feature.chat.ui.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import dev.kyralabs.pingogram.core.domain.model.Document
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes

/**
 * The thumbnail of a picture sent as a file, in the file row's icon place (`REQUIREMENTS.md` FR-C4;
 * owner request 2026-11-07): `sizes.avatarMedium` square with `medium` corners, the thumbnail TDLib keeps for it
 * cropped to fill it over its blurred minithumbnail, decoded at its own size and dimmed in dark theme as the
 * conversation's photos are (§10). Decorative: the row names the file.
 */
@Composable
internal fun DocumentThumbnail(
    document: Document,
    modifier: Modifier = Modifier,
) {
    val thumbnail = document.thumbnail ?: return
    val size = MaterialTheme.sizes.avatarMedium
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val colorFilter = MaterialTheme.pingogramColors.mediaColorFilter
    val alpha = rememberImageAlpha(thumbnail.file.id)
    Box(modifier.size(size).clip(MaterialTheme.shapes.medium)) {
        MinithumbnailPlaceholder(
            minithumbnail = document.minithumbnail,
            modifier = Modifier.matchParentSize(),
            colorFilter = colorFilter,
            covered = coveredBy(alpha),
        )
        TdlibImage(
            fileId = thumbnail.file.id,
            widthPx = sizePx,
            heightPx = sizePx,
            alpha = alpha,
            modifier = Modifier.matchParentSize(),
            colorFilter = colorFilter,
        )
    }
}
