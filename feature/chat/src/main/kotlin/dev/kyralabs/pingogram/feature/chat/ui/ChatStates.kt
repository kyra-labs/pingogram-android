package dev.kyralabs.pingogram.feature.chat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.component.EmptyState
import dev.kyralabs.pingogram.core.ui.text.errorText
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.chat.R
import dev.kyralabs.pingogram.core.ui.R as UiR

// The conversation without messages to show (docs/DESIGN_SYSTEM.md §6: empty states), and the state of older pages
// above the oldest message.

/** The loading indicator while older messages load, or the error with a retry when they could not be loaded. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun OlderMessagesState(
    content: ChatContent.Content,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val error = content.olderError
        if (error != null) {
            Text(
                text = stringResource(R.string.chat_older_error),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.chat_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        } else {
            LoadingIndicator()
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Cookie9Sided,
        icon = UiR.drawable.ic_chat_filled,
        title = stringResource(R.string.chat_empty_title),
        body = stringResource(R.string.chat_empty_body),
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatError(
    content: ChatContent.Error,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    EmptyState(
        illustration = MaterialShapes.Burst,
        icon = UiR.drawable.ic_error,
        title = stringResource(R.string.chat_error_title),
        body = errorText(content.error),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        iconColor = MaterialTheme.colorScheme.onErrorContainer,
        action = {
            Button(onClick = onRetry) {
                Text(stringResource(R.string.chat_retry), style = MaterialTheme.typography.labelLargeEmphasized)
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ChatNotFound(modifier: Modifier = Modifier) {
    EmptyState(
        illustration = MaterialShapes.Pill,
        icon = UiR.drawable.ic_chat,
        title = stringResource(R.string.chat_not_found_title),
        body = stringResource(R.string.chat_not_found_body),
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        iconColor = MaterialTheme.colorScheme.onTertiaryContainer,
    )
}
