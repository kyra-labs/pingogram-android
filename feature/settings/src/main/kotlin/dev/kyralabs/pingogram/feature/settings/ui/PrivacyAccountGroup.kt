package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Composable
import dev.kyralabs.pingogram.feature.settings.ui.account.AccountGroup
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsEvent
import dev.kyralabs.pingogram.feature.settings.ui.network.DataOptionsUiState

/** The Account group, with "Delete cloud drafts" ([drafts]) before "Delete my account". */
@Composable
internal fun PrivacyAccountGroup(
    state: PrivacySecurityUiState.Content,
    onOpen: (PrivacySecurityPage) -> Unit,
    drafts: DataOptionsUiState,
    onDraftsEvent: (DataOptionsEvent) -> Unit,
) {
    AccountGroup(
        loginEmailPattern = state.loginEmailPattern,
        autoDeleteSeconds = state.defaultAutoDeleteSeconds,
        accountTtlDays = state.accountTtlDays,
        onOpen = onOpen,
        beforeDeleteAccount = {
            item(key = DELETE_DRAFTS) { shapes ->
                DeleteDraftsItem(drafts.isDeletingDrafts, shapes) { onDraftsEvent(DataOptionsEvent.DeleteDrafts) }
            }
        },
    )
}

/** The key of "Delete cloud drafts" among the Account group's rows. */
private const val DELETE_DRAFTS = "deleteDrafts"
