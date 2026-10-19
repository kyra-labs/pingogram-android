package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
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
 * (`headlineSmallEmphasized`), its phone number and its username, which screen readers read as one item, and an
 * "Edit profile" tonal button below them that shape-morphs when pressed ([onEditProfile]). TDLib reports the
 * account's changes, so they show here at once, made in Edit profile or on another device.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountHeader(
    account: AccountUiModel,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = MaterialTheme.spacing.small),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
    ) {
        AccountSummary(account)
        FilledTonalButton(onClick = onEditProfile, shapes = ButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(R.drawable.ic_edit),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(
                text = stringResource(R.string.settings_edit_profile_button),
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
    }
}

/** The account's avatar, name, phone number and username, read as one item. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AccountSummary(account: AccountUiModel) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
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
    PingogramPreview { AccountHeader(SettingsPreviewData.account, onEditProfile = {}) }
}

@PingogramPreviews
@Composable
private fun AccountHeaderWithoutUsernamePreview() {
    PingogramPreview { AccountHeader(SettingsPreviewData.account.copy(username = null), onEditProfile = {}) }
}
