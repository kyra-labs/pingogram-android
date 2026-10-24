package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionLists
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

    /** The users and groups [setting] always or never allows, by id. */
    suspend fun exceptions(setting: PrivacySetting): Outcome<PrivacyExceptionLists>

    /**
     * Makes [lists] the users and groups [setting] always and never allows. Its main choice, Telegram Premium
     * subscribers and bots stay as they are.
     *
     * @return the rules of [setting] with these exceptions, which TDLib also reports through [changes].
     */
    suspend fun setExceptions(
        setting: PrivacySetting,
        lists: PrivacyExceptionLists,
    ): Outcome<PrivacyRules>

    /**
     * Whether the user's read time shows to the people they chat with privately (td_api.tl `readDatePrivacySettings`).
     * Those who may see when the user was last online always see it; without it, the user sees no one else's, unless
     * they have Telegram Premium.
     */
    suspend fun showsReadDate(): Outcome<Boolean>

    suspend fun setShowsReadDate(shows: Boolean): Outcome<Unit>

    /** Who may start a chat with the user. */
    suspend fun newChatPrivacy(): Outcome<NewChatPrivacy>

    /**
     * Lets non-contacts write first, or keeps them out ([NewChatPrivacy.canRestrict] first). Keeping them out drops a
     * price for their messages, which Telegram allows only while they may write.
     */
    suspend fun setAllowsNonContacts(allows: Boolean): Outcome<Unit>
}
