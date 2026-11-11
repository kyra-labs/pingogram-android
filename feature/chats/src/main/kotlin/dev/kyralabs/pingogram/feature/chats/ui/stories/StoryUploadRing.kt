package dev.kyralabs.pingogram.feature.chats.ui.stories

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The user's own story ring while their stories are being posted (board 10c).

/**
 * The user's avatar inside a determinate [CircularWavyProgressIndicator] of what is sent (indeterminate while TDLib
 * does not know the sizes), with a cancel badge; once TDLib gave up, a ring in `error` with an error badge.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun UploadRing(
    ring: StoryRing,
    upload: StoryRingUpload,
) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    val failed = upload.failed > 0
    Box(Modifier.size(sizes.avatarMedium), contentAlignment = Alignment.Center) {
        val progress = upload.progress
        when {
            failed -> {
                Box(Modifier.matchParentSize().drawBehind { drawRing(colors.error, sizes.avatarRing.toPx()) })
            }

            progress != null -> {
                CircularWavyProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.matchParentSize(),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                )
            }

            else -> {
                CircularWavyProgressIndicator(
                    modifier = Modifier.matchParentSize(),
                    color = colors.primary,
                    trackColor = colors.surfaceContainerHighest,
                )
            }
        }
        // Smaller than in the other rings, leaving the wave its room.
        Avatar(photo = ring.photo, name = ring.name, id = ring.chatId, size = sizes.avatarSmall)
        RingBadge(
            icon = if (failed) UiR.drawable.ic_error else UiR.drawable.ic_close,
            container = if (failed) colors.errorContainer else colors.surfaceContainerHighest,
            content = if (failed) colors.onErrorContainer else colors.onSurface,
            modifier = Modifier.align(Alignment.BottomEnd),
        )
    }
}

/** "Posting 62%", "Posting…" while the sizes are unknown, or "Not posted" once TDLib gave up. */
internal fun uploadLabel(
    resources: Resources,
    upload: StoryRingUpload,
): String {
    val progress = upload.progress
    return when {
        upload.failed > 0 -> resources.getString(R.string.stories_not_posted)
        progress == null -> resources.getString(R.string.stories_posting)
        else -> resources.getString(R.string.stories_posting_percent, (progress * PERCENT).toInt())
    }
}

/** A symbol on a small circle, ringed in the row's `surface`, at the avatar's bottom end. Decorative. */
@Composable
internal fun RingBadge(
    icon: Int,
    container: Color,
    content: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(MaterialTheme.sizes.avatarBadge)
            .border(MaterialTheme.sizes.avatarRing, MaterialTheme.colorScheme.surface, CircleShape)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(MaterialTheme.sizes.inlineIconSmall),
        )
    }
}

/** A share of 1 as a percentage. */
private const val PERCENT = 100
