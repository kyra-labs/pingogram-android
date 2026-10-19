package dev.kyralabs.pingogram.feature.settings.ui

import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting

/**
 * What the page of a privacy setting shows (`REQUIREMENTS.md` FR-S1).
 *
 * @property rules the setting's rules: loading, loaded, or why they could not be loaded.
 * @property savingChoice the choice being saved, shown selected meanwhile; null when nothing is being saved.
 * @property premiumRequired the setting limits voice and video messages, which only Telegram Premium subscribers can
 *   do, and the user has no Premium: only "Everybody" can be chosen.
 */
@Immutable
data class PrivacySettingUiState(
    val setting: PrivacySetting,
    val rules: PrivacyRulesState = PrivacyRulesState.Loading,
    val savingChoice: PrivacyChoice? = null,
    val premiumRequired: Boolean = false,
) {
    /** The rules as they show: with the choice being saved, if any. */
    val shownRules: PrivacyRules?
        get() {
            val loaded = (rules as? PrivacyRulesState.Loaded)?.rules ?: return null
            return if (savingChoice == null) loaded else loaded.copy(choice = savingChoice)
        }
}

/** The rules of a privacy setting while they load. */
@Immutable
sealed interface PrivacyRulesState {
    data object Loading : PrivacyRulesState

    data class Loaded(
        val rules: PrivacyRules,
    ) : PrivacyRulesState

    data class Failed(
        val error: DomainError,
    ) : PrivacyRulesState
}

/** What the user does on a privacy setting's page, handled by [PrivacySettingViewModel.onEvent]. */
sealed interface PrivacySettingEvent {
    /** [choice] was selected. */
    data class Select(
        val choice: PrivacyChoice,
    ) : PrivacySettingEvent

    /** Load the rules again after they failed. */
    data object Retry : PrivacySettingEvent
}

/** One-off side effects of [PrivacySettingViewModel], handled once by its route. */
sealed interface PrivacySettingEffect {
    /** Saving a choice failed, shown in a snackbar (errors are never dialogs). */
    data class ShowError(
        val error: DomainError,
    ) : PrivacySettingEffect
}
