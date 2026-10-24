package dev.kyralabs.pingogram.feature.calls.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.theme.pingogramColors
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.calls.R

/**
 * "Share screen" at the top end of the call screen (`docs/adr/0026-screen-sharing.md`), opposite "Back to the app": a
 * toggle icon button, the screen-share symbol, the stop symbol while sharing. Over video it takes the media overlay's
 * colors, as "Back to the app" does.
 */
@Composable
internal fun ShareScreenButton(
    sharing: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: Boolean = false,
) {
    IconToggleButton(
        checked = sharing,
        onCheckedChange = { onToggle() },
        shapes = IconButtonDefaults.toggleableShapes(),
        colors =
            if (overlay) {
                IconButtonDefaults.filledIconToggleButtonColors(
                    containerColor = MaterialTheme.pingogramColors.mediaOverlay,
                    contentColor = MaterialTheme.pingogramColors.onMediaOverlay,
                )
            } else {
                IconButtonDefaults.iconToggleButtonColors()
            },
        modifier = modifier,
    ) {
        Icon(
            painterResource(if (sharing) R.drawable.ic_stop_screen_share else R.drawable.ic_screen_share),
            contentDescription =
                stringResource(
                    if (sharing) R.string.call_stop_sharing else R.string.call_share_screen,
                ),
        )
    }
}

/**
 * "You're sharing your screen" with "Stop", under the call's header while the user shares: a `primaryContainer` pill,
 * so it reads apart from the call's own information. Screen readers hear it as it comes.
 */
@Composable
internal fun ScreenSharingPill(
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = MaterialTheme.spacing
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = CircleShape,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(start = spacing.medium, end = spacing.extraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(R.drawable.ic_screen_share),
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.sizes.inlineIcon),
            )
            Spacer(Modifier.width(spacing.small))
            Text(
                stringResource(R.string.call_sharing_screen),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
            Spacer(Modifier.width(spacing.small))
            Button(
                onClick = onStop,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(vertical = spacing.extraSmall),
            ) {
                Text(
                    stringResource(R.string.call_sharing_stop),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                )
            }
        }
    }
}
