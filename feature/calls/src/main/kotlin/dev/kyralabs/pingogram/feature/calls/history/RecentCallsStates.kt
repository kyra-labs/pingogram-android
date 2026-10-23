package dev.kyralabs.pingogram.feature.calls.history

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.feature.calls.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The states of Recent calls that fill its sheet: no calls, and a first page that failed.

/** No calls, or no missed calls: an [EmptyState] on `Cookie9Sided` with the call symbol. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun NoCalls(
    filter: CallFilter,
    modifier: Modifier = Modifier,
) {
    val missed = filter == CallFilter.Missed
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_call,
        title = stringResource(if (missed) R.string.recent_calls_no_missed else R.string.recent_calls_empty),
        body =
            stringResource(if (missed) R.string.recent_calls_no_missed_body else R.string.recent_calls_empty_body),
        modifier = modifier,
    )
}

/** The first page failed: an [EmptyState] on `Cookie4Sided` in `errorContainer` with why and "Try again". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LoadFailed(
    content: RecentCallsContent.Error,
    onRetry: () -> Unit,
) {
    EmptyState(
        illustration = MaterialShapes.Cookie4Sided,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.recent_calls_error),
        body = errorText(content.error),
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) {
                Text(stringResource(R.string.recent_calls_try_again))
            }
        },
    )
}
