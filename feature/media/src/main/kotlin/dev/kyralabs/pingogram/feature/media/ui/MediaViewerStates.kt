package dev.kyralabs.pingogram.feature.media.ui

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.media.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// What the viewer shows instead of media: empty states (docs/DESIGN_SYSTEM.md §6) in its dark theme.

/** The first media could not be loaded because of [error]: "Try again" calls [onRetry]. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ViewerError(
    error: DomainError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.media_error_title),
        body = errorText(error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.media_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

/** The media was deleted, or cannot be shown: "This media is no longer available". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ViewerUnavailable(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_image_filled,
        title = stringResource(R.string.media_unavailable_title),
        body = stringResource(R.string.media_unavailable_body),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
        iconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
