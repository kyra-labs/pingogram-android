package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import org.drinkless.tdlib.TdApi

internal fun PrivacySetting.toTdPrivacySetting(): TdApi.UserPrivacySetting =
    when (this) {
        PrivacySetting.PhoneNumber -> TdApi.UserPrivacySettingShowPhoneNumber()
        PrivacySetting.LastSeen -> TdApi.UserPrivacySettingShowStatus()
        PrivacySetting.ProfilePhoto -> TdApi.UserPrivacySettingShowProfilePhoto()
        PrivacySetting.ForwardedMessages -> TdApi.UserPrivacySettingShowLinkInForwardedMessages()
        PrivacySetting.Calls -> TdApi.UserPrivacySettingAllowCalls()
        PrivacySetting.ChatInvites -> TdApi.UserPrivacySettingAllowChatInvites()
        PrivacySetting.VoiceMessages -> TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages()
    }

/** Null for the settings Pingogram does not show, and for ones added by a newer TDLib. */
internal fun TdApi.UserPrivacySetting.toPrivacySetting(): PrivacySetting? =
    when (this) {
        is TdApi.UserPrivacySettingShowPhoneNumber -> PrivacySetting.PhoneNumber
        is TdApi.UserPrivacySettingShowStatus -> PrivacySetting.LastSeen
        is TdApi.UserPrivacySettingShowProfilePhoto -> PrivacySetting.ProfilePhoto
        is TdApi.UserPrivacySettingShowLinkInForwardedMessages -> PrivacySetting.ForwardedMessages
        is TdApi.UserPrivacySettingAllowCalls -> PrivacySetting.Calls
        is TdApi.UserPrivacySettingAllowChatInvites -> PrivacySetting.ChatInvites
        is TdApi.UserPrivacySettingAllowPrivateVoiceAndVideoNoteMessages -> PrivacySetting.VoiceMessages
        else -> null
    }

/**
 * The main choice is the first main rule ([isMainRule]) that allows or restricts everyone or the contacts; with none,
 * nobody matches, which TDLib treats as not allowed (td_api.tl `userPrivacySettingRules`; TDLib drops a trailing
 * "restrict all", td/telegram/UserPrivacySettingRule.cpp). "Restrict contacts" alone decides nothing here. The users
 * and groups always or never allowed are counted; bot rules are left out.
 */
internal fun TdApi.UserPrivacySettingRules.toPrivacyRules(): PrivacyRules =
    PrivacyRules(
        choice = rules.firstNotNullOfOrNull { it.choice() } ?: PrivacyChoice.Nobody,
        exceptions = rules.fold(PrivacyExceptions()) { exceptions, rule -> exceptions.with(rule) },
    )

/** The main choice [this] rule makes; null for an exception, a bot rule or "restrict contacts". */
private fun TdApi.UserPrivacySettingRule.choice(): PrivacyChoice? =
    when (this) {
        is TdApi.UserPrivacySettingRuleAllowAll -> PrivacyChoice.Everybody
        is TdApi.UserPrivacySettingRuleAllowContacts -> PrivacyChoice.Contacts
        is TdApi.UserPrivacySettingRuleRestrictAll -> PrivacyChoice.Nobody
        else -> null
    }

/** These exceptions with [rule]'s users, groups or Premium subscribers counted; the same for any other rule. */
private fun PrivacyExceptions.with(rule: TdApi.UserPrivacySettingRule): PrivacyExceptions =
    when (rule) {
        is TdApi.UserPrivacySettingRuleAllowUsers -> {
            copy(allowedUserCount = allowedUserCount + rule.userIds.size)
        }

        is TdApi.UserPrivacySettingRuleAllowChatMembers -> {
            copy(allowedChatCount = allowedChatCount + rule.chatIds.size)
        }

        is TdApi.UserPrivacySettingRuleRestrictUsers -> {
            copy(restrictedUserCount = restrictedUserCount + rule.userIds.size)
        }

        is TdApi.UserPrivacySettingRuleRestrictChatMembers -> {
            copy(restrictedChatCount = restrictedChatCount + rule.chatIds.size)
        }

        is TdApi.UserPrivacySettingRuleAllowPremiumUsers -> {
            copy(allowsPremiumUsers = true)
        }

        else -> {
            this
        }
    }

/**
 * These rules with [choice] as their main choice: the main rules ([isMainRule]) go, every other rule stays as it is
 * and in its order (the users and groups always or never allowed, Premium subscribers, bots), and the rule for
 * [choice] follows them, so the exceptions keep deciding first. [PrivacyChoice.Nobody] needs no rule: what matches
 * nothing is not allowed.
 *
 * The exceptions are these rules' own objects, so call it on rules just received from TDLib, which nothing else holds.
 */
internal fun TdApi.UserPrivacySettingRules.withChoice(choice: PrivacyChoice): TdApi.UserPrivacySettingRules {
    val exceptions = rules.filterNot { it.isMainRule() }
    val main =
        when (choice) {
            PrivacyChoice.Everybody -> TdApi.UserPrivacySettingRuleAllowAll()
            PrivacyChoice.Contacts -> TdApi.UserPrivacySettingRuleAllowContacts()
            PrivacyChoice.Nobody -> null
        }
    return TdApi.UserPrivacySettingRules((exceptions + listOfNotNull(main)).toTypedArray())
}

/** A rule about everyone or about all contacts, rather than about some users, groups or bots. */
private fun TdApi.UserPrivacySettingRule.isMainRule(): Boolean =
    this is TdApi.UserPrivacySettingRuleAllowAll ||
        this is TdApi.UserPrivacySettingRuleAllowContacts ||
        this is TdApi.UserPrivacySettingRuleRestrictAll ||
        this is TdApi.UserPrivacySettingRuleRestrictContacts
