package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.ContactSyncStatus
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.contactsync.ContactSyncHost
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.errorMessage
import dev.kyralabs.pingogram.core.ui.text.isDebuggable
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.core.ui.text.userContent
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.account.AccountGroup
import dev.kyralabs.pingogram.feature.settings.ui.lockedchats.lockedChatsItem
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import java.text.NumberFormat
import dev.kyralabs.pingogram.core.ui.R as UiR

/**
 * The Privacy and security page: hoists [PrivacySecurityViewModel] and, for "Sync contacts",
 * [ContactSyncSettingViewModel] with its dialogs, permission request and snackbars ([ContactSyncHost]); both read
 * again whenever the page shows again (back from blocked users, sessions or Android's settings). A row calls [onOpen]
 * with the page it opens; [onBack] leaves the page.
 */
@Composable
internal fun PrivacySecurityRoute(
    onOpen: (PrivacySecurityPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacySecurityViewModel = hiltViewModel(),
    contactSyncViewModel: ContactSyncSettingViewModel = hiltViewModel(),
    secretChatPreviewsViewModel: SecretChatPreviewsViewModel = hiltViewModel(),
    phoneNumberVisibilityViewModel: PhoneNumberVisibilityViewModel = hiltViewModel(),
    frequentContactsViewModel: FrequentContactsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contactSync by contactSyncViewModel.status.collectAsStateWithLifecycle()
    val controls by contactSyncViewModel.contactSync.controls.collectAsStateWithLifecycle()
    val secretChatPreviews by secretChatPreviewsViewModel.enabled.collectAsStateWithLifecycle()
    val phoneNumbersHidden by phoneNumberVisibilityViewModel.hidden.collectAsStateWithLifecycle()
    val suggestsFrequent by frequentContactsViewModel.suggests.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val showErrorDetails = isDebuggable()
    LaunchedEffect(secretChatPreviewsViewModel, phoneNumberVisibilityViewModel, frequentContactsViewModel) {
        merge(
            secretChatPreviewsViewModel.errors,
            phoneNumberVisibilityViewModel.errors,
            frequentContactsViewModel.errors,
        ).collect { error ->
            launch { snackbarHostState.showSnackbar(errorMessage(resources, error, showErrorDetails)) }
        }
    }
    LifecycleStartEffect(viewModel, contactSyncViewModel) {
        viewModel.onEvent(PrivacySecurityEvent.Refresh)
        contactSyncViewModel.onStarted()
        onStopOrDispose {}
    }
    ContactSyncHost(
        controls = controls,
        effects = contactSyncViewModel.contactSync.effects,
        onEvent = contactSyncViewModel.contactSync::onEvent,
        snackbarHostState = snackbarHostState,
    )
    PrivacySecurityScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpen = onOpen,
        onBack = onBack,
        modifier = modifier,
        contactSync = contactSync,
        onContactSyncChange = contactSyncViewModel::onCheckedChange,
        secretChatPreviews = secretChatPreviews,
        onSecretChatPreviewsChange = secretChatPreviewsViewModel::onCheckedChange,
        phoneNumbersHidden = phoneNumbersHidden,
        onPhoneNumbersHiddenChange = phoneNumberVisibilityViewModel::onCheckedChange,
        snackbarHostState = snackbarHostState,
        suggestsFrequent = suggestsFrequent,
        onSuggestsFrequentChange = frequentContactsViewModel::onCheckedChange,
    )
}

/**
 * Privacy and security (`REQUIREMENTS.md` FR-S1):
 * - Privacy: every privacy setting with its main choice ("My contacts + exceptions"), each opening its page;
 * - Contacts: "Sync contacts" ([contactSync], `docs/adr/0011-contacts-sync.md`), a switch with what it sends in its
 *   summary, on while sync is on or paused for the permission ([onContactSyncChange]), and "Suggest frequent contacts"
 *   ([suggestsFrequent]);
 * - Secret chats: "Link previews in secret chats" ([secretChatPreviews], off by default), whose summary says who makes
 *   them;
 * - On screen: "Hide phone numbers" ([phoneNumbersHidden], off by default), every number then shown with its calling
 *   code only (`core:ui`'s `phoneNumberToShow`);
 * - Security: blocked users and active sessions with their counts, whether 2-step verification is on, and whether
 *   the passcode lock is on, each opening its page;
 * - Account: the login email, the self-destruct timer of new chats, and "Delete account" with how long the account
 *   may stay unused, each opening its page.
 */
@Suppress("LongParameterList") // The page's state and events, and contacts sync's.
@Composable
internal fun PrivacySecurityScreen(
    state: PrivacySecurityUiState,
    onEvent: (PrivacySecurityEvent) -> Unit,
    onOpen: (PrivacySecurityPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contactSync: ContactSyncStatus = ContactSyncStatus.Off,
    onContactSyncChange: (Boolean) -> Unit = {},
    secretChatPreviews: Boolean? = null,
    onSecretChatPreviewsChange: (Boolean) -> Unit = {},
    phoneNumbersHidden: Boolean? = null,
    onPhoneNumbersHiddenChange: (Boolean) -> Unit = {},
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    suggestsFrequent: Boolean? = null,
    onSuggestsFrequentChange: (Boolean) -> Unit = {},
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_privacy_security),
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) {
        when (state) {
            PrivacySecurityUiState.Loading -> {
                SettingsLoading(stringResource(R.string.settings_privacy_security_loading))
            }

            is PrivacySecurityUiState.Error -> {
                SettingsError(state.error, onRetry = { onEvent(PrivacySecurityEvent.Retry) })
            }

            is PrivacySecurityUiState.Content -> {
                PrivacySection(state, onOpen)
                ContactsSection(contactSync, onContactSyncChange, suggestsFrequent, onSuggestsFrequentChange)
                SecretChatsSection(secretChatPreviews, onSecretChatPreviewsChange)
                OnScreenSection(phoneNumbersHidden, onPhoneNumbersHiddenChange)
                SecuritySection(state, onOpen)
                AccountGroup(
                    loginEmailPattern = state.loginEmailPattern,
                    autoDeleteSeconds = state.defaultAutoDeleteSeconds,
                    accountTtlDays = state.accountTtlDays,
                    onOpen = onOpen,
                )
            }
        }
    }
}

@Composable
private fun PrivacySection(
    state: PrivacySecurityUiState.Content,
    onOpen: (PrivacySecurityPage) -> Unit,
) {
    SettingsGroup(title = stringResource(R.string.settings_section_privacy)) {
        PrivacySettings.forEach { setting ->
            val rules = state.rules[setting]
            if (rules != null) {
                item(key = setting) { shapes ->
                    PrivacySettingItem(
                        setting = setting,
                        rules = rules,
                        shapes = shapes,
                        onClick = { onOpen(PrivacySecurityPage.Setting(setting)) },
                    )
                }
            }
        }
        state.newChatPrivacy?.let { privacy ->
            item(key = NEW_CHATS) { shapes ->
                SettingsRow(
                    title = stringResource(R.string.settings_privacy_new_chats),
                    shapes = shapes,
                    supportingContent = {
                        Text(
                            stringResource(
                                if (privacy.allowsNonContacts) {
                                    R.string.settings_privacy_everybody
                                } else {
                                    R.string.settings_privacy_new_chats_contacts
                                },
                            ),
                        )
                    },
                    onClick = { onOpen(PrivacySecurityPage.NewChats) },
                )
            }
        }
    }
}

/** The key of the "Messages" row among the privacy settings' rows. */
private const val NEW_CHATS = "newChats"

/** A privacy setting and its main choice. */
@Composable
private fun PrivacySettingItem(
    setting: PrivacySetting,
    rules: PrivacyRules,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = stringResource(setting.titleRes()),
        shapes = shapes,
        supportingContent = { Text(privacySummary(rules)) },
        onClick = onClick,
    )
}

@Composable
private fun SecuritySection(
    state: PrivacySecurityUiState.Content,
    onOpen: (PrivacySecurityPage) -> Unit,
) {
    val numbers = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    SettingsGroup(title = stringResource(R.string.settings_section_security)) {
        item { shapes ->
            BlockedUsersItem(state.blockedCount, numbers, shapes) { onOpen(PrivacySecurityPage.BlockedUsers) }
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_active_sessions),
                summary =
                    pluralStringResource(
                        R.plurals.settings_active_sessions_count,
                        state.sessionCount,
                        numbers.format(state.sessionCount),
                    ),
                icon = R.drawable.ic_devices,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.ActiveSessions) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_websites),
                summary = stringResource(R.string.settings_websites_summary),
                icon = R.drawable.ic_public,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.Websites) },
            )
        }
        item { shapes ->
            TwoStepVerificationItem(
                summary = state.twoStepVerification,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.TwoStepVerification) },
            )
        }
        item { shapes ->
            PageItem(
                title = stringResource(R.string.settings_passcode_lock),
                summary =
                    if (state.passcodeLock) {
                        stringResource(R.string.settings_passcode_lock_on)
                    } else {
                        stringResource(R.string.settings_passcode_lock_off)
                    },
                icon = R.drawable.ic_pin,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.PasscodeLock) },
            )
        }
        lockedChatsItem { onOpen(PrivacySecurityPage.LockedChats) }
    }
}

/** Whether 2-step verification is on, opening its page; a requested reset of the password in the error role. */
@Composable
private fun TwoStepVerificationItem(
    summary: TwoStepVerificationSummary,
    shapes: ListItemShapes,
    onClick: () -> Unit,
) {
    SettingsRow(
        title = stringResource(R.string.settings_two_step_verification),
        shapes = shapes,
        icon = R.drawable.ic_password,
        supportingContent = {
            Column {
                // The pending recovery email in it is isolated (`bidiIsolated`): it takes its language's direction.
                Text(text = summary.status.resolve(), style = LocalTextStyle.current.userContent)
                summary.pendingResetDate?.let { date ->
                    Text(
                        text = stringResource(R.string.settings_two_step_verification_pending_reset, date),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        onClick = onClick,
    )
}

@PingogramPreviews
@Composable
private fun PrivacySecurityScreenPreview() {
    PingogramPreview {
        PrivacySecurityScreen(state = SettingsPreviewData.privacySecurity, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@Preview(name = "Expanded", widthDp = 840, heightDp = 891)
@Composable
private fun PrivacySecurityScreenExpandedPreview() {
    PingogramPreview {
        PrivacySecurityScreen(state = SettingsPreviewData.privacySecurity, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun PrivacySecurityScreenLoadingPreview() {
    PingogramPreview {
        PrivacySecurityScreen(state = PrivacySecurityUiState.Loading, onEvent = {}, onOpen = {}, onBack = {})
    }
}

@PingogramPreviews
@Composable
private fun PrivacySecurityScreenErrorPreview() {
    PingogramPreview {
        PrivacySecurityScreen(
            state = PrivacySecurityUiState.Error(DomainError.Network),
            onEvent = {},
            onOpen = {},
            onBack = {},
        )
    }
}
