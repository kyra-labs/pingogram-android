package dev.kyralabs.pingogram.feature.auth.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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

/**
 * Telegram requires this account to buy Telegram Premium before it can log in. Purchases are out of scope and are
 * never bypassed (`REQUIREMENTS.md` §1.2): this only explains, offers Telegram's support address and another number.
 */
@Composable
fun PremiumRequiredScreen(
    state: AuthStepUiState.PremiumRequired,
    onEvent: (AuthEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    AuthStepLayout(
        title = stringResource(R.string.auth_premium_title),
        description = stringResource(R.string.auth_premium_description),
        modifier = modifier,
        actions = {
            AuthPrimaryButton(
                text = stringResource(R.string.auth_premium_contact_support),
                onClick = { onEvent(AuthEvent.ContactSupport) },
                enabled = true,
                inProgress = false,
            )
            AuthTextButton(
                text = stringResource(R.string.auth_premium_change_number),
                onClick = { onEvent(AuthEvent.ChangePhoneNumber) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
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
    AuthScreenPreview(AuthPreviewData.state(AuthStepUiState.PremiumRequired("support@example.org")))
}
