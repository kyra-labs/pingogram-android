package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData

/**
 * The logged-in account at the top of Settings: its photo (or placeholder) in a large [Avatar], its name
 * (`headlineSmallEmphasized`), its phone number and its username. Read-only; editing the profile comes later. Screen
 * readers read it as one item.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountHeader(
    account: AccountUiModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.small)
                .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.extraSmall),
    ) {
        Avatar(
            photo = account.photo,
            name = account.name,
            id = account.userId,
            size = MaterialTheme.sizes.avatarLarge,
        )
        Spacer(Modifier.height(MaterialTheme.spacing.small))
        Text(
            text = account.name,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineSmallEmphasized,
        )
        if (account.phoneNumber.isNotEmpty()) {
            Text(
                text = account.phoneNumber,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        account.username?.let { username ->
            Text(
                text = stringResource(R.string.settings_account_username, username),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@PingogramPreviews
@Composable
private fun AccountHeaderPreview() {
    PingogramPreview { AccountHeader(SettingsPreviewData.account) }
}

@PingogramPreviews
@Composable
private fun AccountHeaderWithoutUsernamePreview() {
    PingogramPreview { AccountHeader(SettingsPreviewData.account.copy(username = null)) }
}
