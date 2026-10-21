package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData

/**
 * The logged-in account at the top of Settings, as Google's Material 3 Expressive account menus show it: a card (a
 * [SegmentedListItem] on its own, with `extraLarge` corners that morph to the list item's pressed shape on the motion
 * scheme's fast spatial spring) with the account's photo (or placeholder) in a ring of `primary` and an edit badge, its
 * name (`titleLarge`), its phone number and username, a "Premium" label when the account subscribes to Telegram
 * Premium (a status, never an offer), and a tonal chevron button at the end.
 *
 * The whole card opens Edit profile ([onEditProfile]), and so does the chevron. Screen readers read the card as one
 * item that edits the profile; the chevron repeats the card's action for touch, so it is left out of the semantics
 * and of keyboard focus rather than announced twice. TDLib reports the account's changes, so they show here at once,
 * made in Edit profile or on another device.
 */
@Composable
internal fun AccountCard(
    account: AccountUiModel,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val editProfile = stringResource(R.string.settings_edit_profile_button)
    SegmentedListItem(
        onClick = onEditProfile,
        shapes = ListItemDefaults.shapes(shape = MaterialTheme.shapes.extraLarge),
        // Names what a tap does; the list item's click supplies the action.
        modifier = modifier.semantics { onClick(label = editProfile, action = null) },
        leadingContent = { AccountAvatar(account) },
        trailingContent = { ChevronButton(onEditProfile) },
        supportingContent = { AccountDetails(account) },
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(MaterialTheme.spacing.large),
        colors = settingsRowColors(),
    ) {
        Text(text = account.name, style = MaterialTheme.typography.titleLarge.userContent)
    }
}

/**
 * The account's avatar in a ring of `primary`, set off from it by a gap of the ring's width, and a round edit badge
 * over its bottom end, set off from the avatar by a border of the card's color.
 */
@Composable
private fun AccountAvatar(account: AccountUiModel) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    Box {
        Avatar(
            photo = account.photo,
            name = account.name,
            id = account.userId,
            size = sizes.avatarMedium,
            modifier =
                Modifier
                    .border(sizes.avatarRing, colors.primary, CircleShape)
                    .padding(sizes.avatarRing * 2),
        )
        Icon(
            painter = painterResource(R.drawable.ic_edit),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(sizes.avatarBadge)
                    .background(colors.surfaceContainerHighest, CircleShape)
                    .border(sizes.avatarRing, settingsRowContainerColor(), CircleShape)
                    .padding((sizes.avatarBadge - sizes.badgeIcon) / 2),
            tint = colors.onSurfaceVariant,
        )
    }
}

/**
 * The phone number (`bodyLarge`) and the username (the list item's `bodyMedium`) in `onSurfaceVariant`, and the
 * "Premium" label below them for Telegram Premium subscribers.
 */
@Composable
private fun AccountDetails(account: AccountUiModel) {
    Column {
        if (account.phoneNumber.isNotEmpty()) {
            Text(text = account.phoneNumber, style = MaterialTheme.typography.bodyLarge.numericContent)
        }
        account.username?.let { username ->
            Text(
                text = stringResource(R.string.settings_account_username, username),
                style = LocalTextStyle.current.userContent,
            )
        }
        if (account.isPremium) PremiumLabel(Modifier.padding(top = MaterialTheme.spacing.small))
    }
}

/**
 * "Premium", a status of the account: a full pill in `tertiaryContainer` with `labelMediumEmphasized` text. Not a chip:
 * it does nothing when tapped, and Pingogram sells nothing (`REQUIREMENTS.md` §1.2).
 */
@Composable
private fun PremiumLabel(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Text(
        text = stringResource(R.string.settings_account_premium),
        modifier =
            modifier
                .background(colors.tertiaryContainer, CircleShape)
                .padding(horizontal = spacing.small, vertical = spacing.extraSmall / 2),
        color = colors.onTertiaryContainer,
        style = MaterialTheme.typography.labelMediumEmphasized,
    )
}

/** The tonal chevron at the end of the card, whose shape morphs when pressed; see [AccountCard] for its semantics. */
@Composable
private fun ChevronButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        modifier =
            Modifier
                .clearAndSetSemantics {}
                .focusProperties { canFocus = false },
    ) {
        Icon(painter = painterResource(R.drawable.ic_chevron_right), contentDescription = null)
    }
}

@PingogramPreviews
@Composable
private fun AccountCardPreview() {
    PingogramPreview { AccountCard(SettingsPreviewData.account, onEditProfile = {}) }
}

@PingogramPreviews
@Composable
private fun AccountCardPremiumPreview() {
    PingogramPreview { AccountCard(SettingsPreviewData.premiumAccount, onEditProfile = {}) }
}

@PingogramPreviews
@Composable
private fun AccountCardWithoutUsernamePreview() {
    PingogramPreview { AccountCard(SettingsPreviewData.account.copy(username = null), onEditProfile = {}) }
}
