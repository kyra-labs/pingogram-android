package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R

/**
 * TDLib is starting or restarting ([AuthStepUiState.Loading]), logging out ([isLoggingOut], FR-A8), or the account
 * is logged in and the app is leaving the flow ([AuthStepUiState.Done]).
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthProgressScreen(
    isLoggingOut: Boolean,
    modifier: Modifier = Modifier,
) {
    val loading = stringResource(R.string.auth_loading)
    Column(
        modifier = modifier.fillMaxSize().padding(MaterialTheme.spacing.windowMarginCompact),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        LoadingIndicator(modifier = Modifier.semantics { contentDescription = loading })
        if (isLoggingOut) {
            Text(
                text = stringResource(R.string.auth_logging_out),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = MaterialTheme.spacing.large),
            )
        }
    }
}
