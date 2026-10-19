package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthNotice
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthPrimaryButton
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthStepLayout
import dev.kyralabs.pingogram.feature.auth.ui.components.AuthTextButton
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthPreviewData
import dev.kyralabs.pingogram.feature.auth.ui.preview.AuthScreenPreview
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * Telegram requires this account to buy Telegram Premium before it can log in. Purchases are out of scope and are
 * never bypassed (`REQUIREMENTS.md` §1.2): this only explains, offers Telegram's support address and another number.
 * The account cannot log in here, so the badge is an error's.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PremiumRequiredScreen(
    state: AuthStepUiState.PremiumRequired,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_premium_title),
        badgeShape = MaterialShapes.Cookie4Sided,
        badgeIcon = UiR.drawable.ic_error,
        description = stringResource(R.string.auth_premium_description),
        isError = true,
        modifier = modifier,
        primaryAction = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_premium_contact_support),
                onClick = { onEvent(AuthEvent.ContactSupport) },
                enabled = true,
                inProgress = false,
            )
        },
        secondaryAction = {
            AuthTextButton(
                text = stringResource(R.string.auth_premium_change_number),
                onClick = { onEvent(AuthEvent.ChangePhoneNumber) },
            )
        },
    ) {
        AuthNotice(text = stringResource(R.string.auth_premium_not_offered))
        AuthNotice(text = stringResource(R.string.auth_premium_support, state.supportEmailAddress))
    }
}

@PingogramPreviews
@Composable
private fun PremiumRequiredScreenPreview() {
    AuthScreenPreview(AuthPreviewData.premiumRequired)
}
