package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableMap

/** What the Privacy and security page shows (`REQUIREMENTS.md` FR-S1). */
@Immutable
sealed interface PrivacySecurityUiState {
    data object Loading : PrivacySecurityUiState

    /** Nothing could be loaded, because of [error]. */
    data class Error(
        val error: DomainError,
    ) : PrivacySecurityUiState

    /**
     * Everything loaded.
     *
     * @property rules the rules of every privacy setting Pingogram shows.
     * @property blockedCount how many users and chats are blocked.
     * @property sessionCount how many sessions the account has, this one included.
     * @property twoStepVerification whether a 2-step verification password is set.
     * @property passcodeLock whether the passcode lock is on, on this device.
     */
    data class Content(
        val rules: ImmutableMap<PrivacySetting, PrivacyRules>,
        val blockedCount: Int,
        val sessionCount: Int,
        val twoStepVerification: TwoStepVerificationSummary,
        val passcodeLock: Boolean = false,
    ) : PrivacySecurityUiState
}

/**
 * The state of 2-step verification as Privacy and security shows it.
 *
 * @property status whether a password is set, with or without a recovery email, or one waiting for confirmation.
 * @property pendingResetDate when a requested reset of the password can be completed, formatted; null if none was
 *   requested.
 */
@Immutable
data class TwoStepVerificationSummary(
    val status: UiText,
    val pendingResetDate: String? = null,
)

/** What the user does on the Privacy and security page, handled by [PrivacySecurityViewModel.onEvent]. */
sealed interface PrivacySecurityEvent {
    /** Load everything again after it failed. */
    data object Retry : PrivacySecurityEvent

    /** The page shows again, e.g. back from blocked users: load the counts again, keeping what shows meanwhile. */
    data object Refresh : PrivacySecurityEvent
}

/** The pages Privacy and security opens, each pushed above it. */
@Immutable
sealed interface PrivacySecurityPage {
    data class Setting(
        val setting: PrivacySetting,
    ) : PrivacySecurityPage

    data object BlockedUsers : PrivacySecurityPage

    data object ActiveSessions : PrivacySecurityPage

    data object TwoStepVerification : PrivacySecurityPage

    data object PasscodeLock : PrivacySecurityPage
}
