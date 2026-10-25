package dev.kyralabs.pingogram.feature.chat.ui.interactions

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.graphics.shapes.RoundedPolygon
import dev.kyralabs.pingogram.core.ui.component.DelayedLoadingIndicator
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

/** The tab's first page on its way: the delayed loading indicator (`docs/DESIGN_SYSTEM.md` §5). */
@Composable
internal fun InteractionsLoading() {
    val description = stringResource(R.string.interactions_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.extraLarge), contentAlignment = Alignment.Center) {
        DelayedLoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** The next page on its way, below the rows. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MoreLoading() {
    val description = stringResource(R.string.interactions_loading)
    Box(Modifier.fillMaxWidth().padding(MaterialTheme.spacing.medium), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.semantics { contentDescription = description })
    }
}

/** The next page failed: why, and "Try again", below the rows. */
@Composable
internal fun MoreFailed(
    more: MoreInteractions.Failed,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(MaterialTheme.spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorText(more.error),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.interactions_retry))
        }
    }
}

/** Nobody to show: a soft `Cookie9Sided` badge in `primaryContainer`, as the app's empty states have it. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InteractionsEmpty(isSeen: Boolean) {
    val colors = MaterialTheme.colorScheme
    InteractionsNotice(
        shape = MaterialShapes.Cookie9Sided,
        icon = if (isSeen) UiR.drawable.ic_done_all else UiR.drawable.ic_favorite,
        container = colors.primaryContainer,
        content = colors.onPrimaryContainer,
        title =
            stringResource(
                if (isSeen) R.string.interactions_empty_seen_title else R.string.interactions_empty_reactions_title,
            ),
        body =
            stringResource(
                if (isSeen) R.string.interactions_empty_seen_body else R.string.interactions_empty_reactions_body,
            ),
    )
}

/** The tab's first page failed: the error icon on `Cookie4Sided` in `errorContainer`, why, and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InteractionsError(
    error: InteractionsContent.Error,
    onRetry: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    InteractionsNotice(
        shape = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        container = colors.errorContainer,
        content = colors.onErrorContainer,
        title = stringResource(R.string.interactions_error_title),
        body = errorText(error.error),
    ) {
        Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
            Text(stringResource(R.string.interactions_retry), style = MaterialTheme.typography.labelLargeEmphasized)
        }
    }
}

/**
 * A notice in place of a tab's rows: a `MaterialShapes` badge with [icon], a `titleMediumEmphasized` [title], a
 * `bodyMedium` [body] and an [action]; `EmptyState` fills a screen, so it cannot sit in a sheet that fits its content.
 */
@Suppress("LongParameterList") // The badge's shape, icon and colors, and the texts and action.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InteractionsNotice(
    shape: RoundedPolygon,
    @DrawableRes icon: Int,
    container: Color,
    content: Color,
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    val spacing = MaterialTheme.spacing
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.extraLarge, vertical = spacing.large),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Box(
            modifier = Modifier.size(MaterialTheme.sizes.headlineBadge).background(container, shape.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painter = painterResource(icon), contentDescription = null, tint = content)
        }
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMediumEmphasized,
        )
        Text(
            text = body,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        action?.invoke()
    }
}
