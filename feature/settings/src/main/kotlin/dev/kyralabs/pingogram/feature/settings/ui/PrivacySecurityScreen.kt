package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreview
import dev.kyralabs.pingogram.core.ui.preview.PingogramPreviews
import dev.kyralabs.pingogram.core.ui.text.resolve
import dev.kyralabs.pingogram.feature.settings.R
import dev.kyralabs.pingogram.feature.settings.ui.preview.SettingsPreviewData
import java.text.NumberFormat

/**
 * The Privacy and security page: hoists [PrivacySecurityViewModel], and loads the counts again whenever the page shows
 * again (back from blocked users or sessions). A row calls [onOpen] with the page it opens; [onBack] leaves the page.
 */
@Composable
internal fun PrivacySecurityRoute(
    onOpen: (PrivacySecurityPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PrivacySecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleStartEffect(viewModel) {
        viewModel.onEvent(PrivacySecurityEvent.Refresh)
        onStopOrDispose {}
    }
    PrivacySecurityScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpen = onOpen,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Privacy and security (`REQUIREMENTS.md` FR-S1):
 * - Privacy: every privacy setting with its main choice ("My contacts + exceptions"), each opening its page;
 * - Security: blocked users and active sessions with their counts, whether 2-step verification is on, and whether
 *   the passcode lock is on, each opening its page.
 */
@Composable
internal fun PrivacySecurityScreen(
    state: PrivacySecurityUiState,
    onEvent: (PrivacySecurityEvent) -> Unit,
    onOpen: (PrivacySecurityPage) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPageScaffold(
        title = stringResource(R.string.settings_privacy_security),
        onBack = onBack,
        modifier = modifier,
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
                SecuritySection(state, onOpen)
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
    }
}

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
            PageItem(
                title = stringResource(R.string.settings_blocked_users),
                summary =
                    if (state.blockedCount == 0) {
                        stringResource(R.string.settings_blocked_users_none)
                    } else {
                        pluralStringResource(
                            R.plurals.settings_blocked_users_count,
                            state.blockedCount,
                            numbers.format(state.blockedCount),
                        )
                    },
                icon = R.drawable.ic_block,
                shapes = shapes,
                onClick = { onOpen(PrivacySecurityPage.BlockedUsers) },
            )
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
                Text(summary.status.resolve())
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
