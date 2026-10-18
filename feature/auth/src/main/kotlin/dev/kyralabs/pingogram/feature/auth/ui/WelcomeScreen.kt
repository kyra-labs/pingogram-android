package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.domain.model.ConnectionStatus
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview

/**
 * FR-A1: the brand, an honest note that Pingogram is not Telegram's own app (CLAUDE.md rule 3), "Start messaging",
 * and Telegram's terms of service and privacy policy.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WelcomeScreen(
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_welcome_title),
        description = stringResource(R.string.auth_welcome_tagline),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_start_messaging),
                onClick = { onEvent(AuthEvent.StartMessaging) },
                enabled = true,
                inProgress = false,
            )
            Text(
                text = stringResource(R.string.auth_welcome_agreement),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.small, Alignment.CenterHorizontally),
            ) {
                AuthTextButton(
                    text = stringResource(R.string.auth_terms_of_service),
                    onClick = { onEvent(AuthEvent.OpenTermsOfService) },
                )
                AuthTextButton(
                    text = stringResource(R.string.auth_privacy_policy),
                    onClick = { onEvent(AuthEvent.OpenPrivacyPolicy) },
                )
            }
        },
    ) {
        // Brand artwork: an Expressive shape, not a logo (CLAUDE.md rule 9).
        val shape = MaterialShapes.Cookie9Sided.toShape()
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(HERO_WIDTH_FRACTION)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .background(MaterialTheme.colorScheme.primaryContainer, shape)
                    .clearAndSetSemantics {},
        )
        Text(
            text = stringResource(R.string.auth_welcome_disclaimer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private const val HERO_WIDTH_FRACTION = 0.5f

@PingogramPreviews
@Composable
private fun WelcomeScreenPreview() {
    AuthScreenPreview(AuthPreviewData.state(AuthStepUiState.Welcome, canGoBack = false))
}

@PingogramPreviews
@Composable
private fun LoadingScreenPreview() {
    AuthScreenPreview(
        AuthPreviewData.state(AuthStepUiState.Loading, connection = ConnectionStatus.Connecting, canGoBack = false),
    )
}

@PingogramPreviews
@Composable
private fun LoggingOutScreenPreview() {
    AuthScreenPreview(AuthPreviewData.state(AuthStepUiState.LoggingOut, canGoBack = false))
}
