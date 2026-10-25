package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.material3.Badge
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import dev.kyralabs.pingogram.core.ui.component.Avatar
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.core.ui.text.numericContent
import dev.kyralabs.pingogram.core.ui.text.phoneNumberToShow
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.core.ui.theme.sizes
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The device's other accounts and "Add account", a group below the account card (`docs/adr/0031-multiple-accounts.md`,
 * `docs/DESIGN_SYSTEM.md` §6 "Account switcher"), as Telegram's apps list them: a row per other account (its 40 dp
 * avatar where the rows' icons sit, its name as user content, its phone number left to right, its unread chats in a
 * `primary` [Badge]), which switches to it ([onSwitch], the app then opens again on that account), and "Add account"
 * ([ic_person_add][UiR.drawable.ic_person_add] in the group's tonal circle) while [canAdd]. Nothing shows while there
 * is neither.
 */
@Composable
internal fun OtherAccountsGroup(
    accounts: ImmutableList<OtherAccountUiModel>,
    canAdd: Boolean,
    onSwitch: (slot: Int) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (accounts.isEmpty() && !canAdd) return
    SettingsGroup(modifier) {
        accounts.forEach { account ->
            item(
                key = account.slot,
            ) { shapes -> OtherAccountItem(account, shapes, onClick = { onSwitch(account.slot) }) }
        }
        if (canAdd) {
            item(key = ADD_ACCOUNT_KEY) { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_add_account),
                    shapes = shapes,
                    icon = UiR.drawable.ic_person_add,
                    onClick = onAdd,
                )
            }
        }
    }
}

/**
 * Another account's row; screen readers hear its name, number and unread chats as one item that switches to it
 * ("Switch to Ada Lovelace").
 */
@Composable
private fun OtherAccountItem(
    account: OtherAccountUiModel,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    val resources = LocalResources.current
    val switchLabel = stringResource(R.string.settings_switch_account, account.name.bidiIsolated())
    SegmentedListItem(
        onClick = onClick,
        shapes = shapes,
        modifier = Modifier.semantics { onClick(label = switchLabel, action = null) },
        colors = settingsRowColors(),
        leadingContent = {
            Avatar(
                photo = account.photo,
                name = account.name,
                id = account.userId,
                size = MaterialTheme.sizes.leadingIconContainer,
            )
        },
        supportingContent =
            if (account.phoneNumber.isNotEmpty()) {
                { Text(phoneNumberToShow(account.phoneNumber), style = LocalTextStyle.current.numericContent) }
            } else {
                null
            },
        trailingContent =
            if (account.unreadChats > 0) {
                {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier =
                            Modifier.clearAndSetSemantics {
                                contentDescription =
                                    resources.getQuantityString(
                                        R.plurals.settings_account_unread_chats,
                                        account.unreadChats,
                                        account.unreadChats,
                                    )
                            },
                    ) {
                        Text(account.unreadChats.toString(), style = LocalTextStyle.current.numericContent)
                    }
                }
            } else {
                null
            },
    ) {
        Text(account.name, style = LocalTextStyle.current.userContent, maxLines = 2)
    }
}

private const val ADD_ACCOUNT_KEY = "add-account"

@PingogramPreviews
@Composable
private fun OtherAccountsGroupPreview() {
    PingogramPreview {
        OtherAccountsGroup(SettingsPreviewData.otherAccounts, canAdd = true, onSwitch = {}, onAdd = {})
    }
}

@PingogramPreviews
@Composable
private fun OtherAccountsGroupFullPreview() {
    PingogramPreview {
        OtherAccountsGroup(SettingsPreviewData.otherAccounts, canAdd = false, onSwitch = {}, onAdd = {})
    }
}

@PingogramPreviews
@Composable
private fun OtherAccountsGroupAddOnlyPreview() {
    PingogramPreview {
        OtherAccountsGroup(persistentListOf(), canAdd = true, onSwitch = {}, onAdd = {})
    }
}
