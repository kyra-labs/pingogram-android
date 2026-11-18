package dev.kyralabs.pingogram.feature.chats.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chats.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** "Earlier · read" in `labelLarge` `onSurfaceVariant` between two `outlineVariant` hairlines, a heading. */
@Composable
internal fun FeedDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(vertical = MaterialTheme.spacing.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = stringResource(R.string.feed_earlier),
            modifier = Modifier.semantics { heading() },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The end of the new posts once all are read: a `primaryContainer` card with `extraLarge` corners, the double tick on
 * a `Cookie9Sided` badge, "You're all caught up" and how many were read here ([readHere]); announced as it appears.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun FeedCaughtUp(
    readHere: Int,
    hasOlder: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            modifier
                .background(colors.primaryContainer, MaterialTheme.shapes.extraLarge)
                .padding(MaterialTheme.spacing.large)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(MaterialTheme.sizes.avatarSmall)
                .background(colors.primary, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(UiR.drawable.ic_done_all), contentDescription = null, tint = colors.onPrimary)
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.feed_caught_up),
                color = colors.onPrimaryContainer,
                style = MaterialTheme.typography.titleMediumEmphasized,
            )
            val read = pluralStringResource(R.plurals.feed_read_here, readHere, readHere)
            Text(
                text = if (hasOlder) stringResource(R.string.feed_read_here_older, read) else read,
                color = colors.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Reading new posts failed: a card on `errorContainer` with `extraLarge` corners above the posts already on the phone,
 * "Couldn't load new posts", why, and "Try again" ([onRetry], the sheet's first control); announced once.
 */
@Composable
internal fun FeedErrorCard(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .background(colors.errorContainer, MaterialTheme.shapes.extraLarge)
                .padding(MaterialTheme.spacing.large)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small),
    ) {
        Text(
            text = stringResource(R.string.feed_error_title),
            color = colors.onErrorContainer,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(errorText(error), color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
        FilledTonalButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.feed_retry))
        }
    }
}
