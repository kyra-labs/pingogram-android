package dev.kyralabs.pingogram.feature.settings.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.component.AvatarShape
import dev.kyralabs.pingogram.core.ui.component.LocalIsDemo
import dev.kyralabs.pingogram.core.ui.text.LocalPhoneNumbersHidden
import dev.kyralabs.pingogram.core.ui.text.maskPhoneNumber
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.experimentalTypography
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.core.ui.theme.spacing
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.AccountLabel
import dev.kyralabs.pingogram.feature.settings.ui.AccountUiModel
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The account at the top of Settings home (`docs/DESIGN_SYSTEM.md` §13.9): a card on
 * `primaryContainer` / `onPrimaryContainer` with `extraLargeIncreased` corners. The avatar
 * (`experimentalAccountAvatar`) in `MaterialShapes.Cookie9Sided` with a camera badge in `tertiaryContainer`; the
 * name in `headlineSmallWide` (a heading), the username, and the phone number masked, with an eye button that shows
 * it on this screen (while "Hide phone numbers" is on it stays masked, without the button). Then "Edit profile"
 * ([onEditProfile], a filled button in the card's inverse colors), "QR code" ([onQrCode], tonal) and, where there is
 * anything to switch to ([switcher]), a tonal icon button opening the account menu. In the demo a "Demo" label stands
 * in the phone number's place, and a Telegram Premium subscriber's card says "Premium" under the username.
 */
@Composable
internal fun AccountCard(
    account: AccountUiModel,
    onEditProfile: () -> Unit,
    onQrCode: () -> Unit,
    switcher: AccountSwitcher?,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLargeIncreased)
                .background(colors.primaryContainer)
                .padding(spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardAvatar(account)
            Column(Modifier.weight(1f).padding(start = spacing.large)) {
                Text(
                    text = account.name,
                    modifier = Modifier.semantics { heading() },
                    color = colors.onPrimaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.experimentalTypography.headlineSmallWide.userContent,
                )
                account.username?.let {
                    Text(
                        text = stringResource(R.string.settings_account_username, it),
                        color = colors.onPrimaryContainer,
                        style = MaterialTheme.typography.bodyMediumEmphasized.userContent,
                    )
                }
                // "Premium" for Telegram Premium subscribers, a status and never an offer (REQUIREMENTS.md §1.2).
                if (account.isPremium && !LocalIsDemo.current) {
                    AccountLabel(
                        text = stringResource(R.string.settings_account_premium),
                        container = colors.tertiaryContainer,
                        content = colors.onTertiaryContainer,
                        modifier = Modifier.padding(vertical = spacing.extraSmall),
                    )
                }
                if (LocalIsDemo.current) {
                    AccountLabel(
                        text = stringResource(R.string.settings_account_demo),
                        container = colors.onPrimaryContainer,
                        content = colors.primaryContainer,
                        modifier = Modifier.padding(top = spacing.extraSmall),
                    )
                } else if (account.phoneNumber.isNotEmpty()) {
                    PhoneNumber(account.phoneNumber)
                }
            }
        }
        CardActions(onEditProfile, onQrCode, switcher)
    }
}

/** The avatar in `Cookie9Sided` and its camera badge, decorative: "Edit profile" changes the photo. */
@Composable
private fun CardAvatar(account: AccountUiModel) {
    val sizes = MaterialTheme.sizes
    val colors = MaterialTheme.colorScheme
    Box {
        Avatar(
            photo = account.photo,
            name = account.name,
            id = account.userId,
            size = sizes.experimentalAccountAvatar,
            // The group shape's Cookie9Sided, as the owner's board draws the account.
            shape = AvatarShape.Group,
        )
        Icon(
            painter = painterResource(UiR.drawable.ic_photo_camera_filled),
            contentDescription = null,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(sizes.avatarBadge)
                    .background(colors.tertiaryContainer, CircleShape)
                    .border(sizes.avatarRing, colors.primaryContainer, CircleShape)
                    .padding((sizes.avatarBadge - sizes.badgeIcon) / 2),
            tint = colors.onTertiaryContainer,
        )
    }
}

/** The phone number, masked until the eye button shows it; always masked while "Hide phone numbers" is on. */
@Composable
private fun PhoneNumber(formatted: String) {
    val hiddenEverywhere = LocalPhoneNumbersHidden.current
    var shown by rememberSaveable { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (shown && !hiddenEverywhere) formatted else maskPhoneNumber(formatted),
            modifier = Modifier.weight(1f, fill = false),
            color = colors.onPrimaryContainer,
            style = MaterialTheme.typography.bodyMedium.numericContent,
        )
        if (!hiddenEverywhere) {
            IconButton(onClick = { shown = !shown }, shapes = IconButtonDefaults.shapes()) {
                Icon(
                    painter =
                        painterResource(
                            if (shown) UiR.drawable.ic_visibility_off else UiR.drawable.ic_visibility,
                        ),
                    contentDescription =
                        stringResource(
                            if (shown) R.string.settings_home_hide_phone else R.string.settings_home_show_phone,
                        ),
                    tint = colors.onPrimaryContainer,
                )
            }
        }
    }
}

/** "Edit profile", "QR code" and the account menu's button, sharing the row. */
@Composable
private fun CardActions(
    onEditProfile: () -> Unit,
    onQrCode: () -> Unit,
    switcher: AccountSwitcher?,
) {
    val colors = MaterialTheme.colorScheme
    val spacing = MaterialTheme.spacing
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.small), verticalAlignment = Alignment.CenterVertically) {
        Button(
            onClick = onEditProfile,
            modifier = Modifier.weight(1f),
            shapes = ButtonDefaults.shapes(),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colors.onPrimaryContainer,
                    contentColor = colors.primaryContainer,
                ),
        ) {
            Text(
                text = stringResource(R.string.settings_edit_profile_button),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLargeEmphasized,
            )
        }
        FilledTonalButton(onClick = onQrCode, modifier = Modifier.weight(1f), shapes = ButtonDefaults.shapes()) {
            Text(
                text = stringResource(R.string.settings_home_qr_code),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        switcher?.let { SwitchAccountButton(it) }
    }
}

/**
 * The account menu from the card: an expressive [DropdownMenuPopup] of the other accounts (avatar, name, their unread
 * chats in a `primary` badge), then "Add account" in a second group while one can be added.
 */
@Composable
private fun SwitchAccountButton(switcher: AccountSwitcher) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        FilledTonalIconButton(onClick = { expanded = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(
                painter = painterResource(R.drawable.ic_switch_account),
                contentDescription = stringResource(R.string.settings_home_switch_account),
            )
        }
        DropdownMenuPopup(expanded = expanded, onDismissRequest = { expanded = false }) {
            AccountMenuItems(switcher, onDone = { expanded = false })
        }
    }
}

/** The account menu's groups: the other accounts, then "Add account"; [onDone] closes the menu before acting. */
@Composable
private fun AccountMenuItems(
    switcher: AccountSwitcher,
    onDone: () -> Unit,
) {
    val groups = (if (switcher.accounts.isEmpty()) 0 else 1) + (if (switcher.canAdd) 1 else 0)
    if (switcher.accounts.isNotEmpty()) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = 0, count = groups)) {
            switcher.accounts.forEachIndexed { index, account ->
                DropdownMenuItem(
                    onClick = {
                        onDone()
                        switcher.onSwitch(account.slot)
                    },
                    text = {
                        Text(
                            account.name,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyLarge.userContent,
                        )
                    },
                    shape = MenuDefaults.itemShape(index, switcher.accounts.size).shape,
                    leadingIcon = {
                        Avatar(
                            photo = account.photo,
                            name = account.name,
                            id = account.userId,
                            size = MaterialTheme.sizes.avatarExtraSmall,
                        )
                    },
                    trailingContent =
                        if (account.unreadChats > 0) {
                            {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                ) { Text(account.unreadChats.toString()) }
                            }
                        } else {
                            null
                        },
                )
            }
        }
    }
    if (switcher.canAdd) {
        DropdownMenuGroup(shapes = MenuDefaults.groupShape(index = groups - 1, count = groups)) {
            DropdownMenuItem(
                onClick = {
                    onDone()
                    switcher.onAdd()
                },
                text = { Text(stringResource(R.string.settings_add_account)) },
                shape = MenuDefaults.itemShape(0, 1).shape,
                leadingIcon = { Icon(painterResource(UiR.drawable.ic_person_add), contentDescription = null) },
            )
        }
    }
}
