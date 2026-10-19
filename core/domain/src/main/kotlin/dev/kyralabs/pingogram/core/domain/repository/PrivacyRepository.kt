package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacyRulesChange
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import kotlinx.coroutines.flow.Flow

/**
 * The account's privacy settings (`REQUIREMENTS.md` FR-S1, Privacy and security). TDLib keeps them, asking Telegram
 * the first time and following its changes after, so nothing is cached here. A request that gets no answer in time
 * fails with `DomainError.Network`.
 */
interface PrivacyRepository {
    /** The rules of [setting] now. */
    suspend fun rules(setting: PrivacySetting): Outcome<PrivacyRules>

    /**
     * Every change of a privacy setting TDLib reports from now on, made here or on another device. No replay: collect
     * it before asking for [rules], so no change in between is missed.
     */
    val changes: Flow<PrivacyRulesChange>

    /**
     * Makes [choice] the main choice of [setting]. Its exceptions stay as they are: the users and groups always or
     * never allowed are kept, in their order, before the new main rule. Limiting voice and video messages
     * ([PrivacySetting.VoiceMessages]) fails with `DomainError.PremiumRequired` without Telegram Premium.
     *
     * @return the rules of [setting] with the new choice, which TDLib also reports through [changes].
     */
    suspend fun setChoice(
        setting: PrivacySetting,
        choice: PrivacyChoice,
    ): Outcome<PrivacyRules>
}
