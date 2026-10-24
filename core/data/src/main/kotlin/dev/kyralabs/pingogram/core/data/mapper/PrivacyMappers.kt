package dev.kyralabs.pingogram.core.data.mapper

import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionLists
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import org.drinkless.tdlib.TdApi

internal fun PrivacySetting.toTdPrivacySetting(): TdApi.UserPrivacySetting =
    when (this) {
        PrivacySetting.PhoneNumber -> TdApi.UserPrivacySettingShowPhoneNumber()
        PrivacySetting.LastSeen -> TdApi.UserPrivacySettingShowStatus()
        PrivacySetting.ProfilePhoto -> TdApi.UserPrivacySettingShowProfilePhoto()
        PrivacySetting.Bio -> TdApi.UserPrivacySettingShowBio()
        PrivacySetting.Birthdate -> TdApi.UserPrivacySettingShowBirthdate()
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
        is TdApi.UserPrivacySettingShowBio -> PrivacySetting.Bio
        is TdApi.UserPrivacySettingShowBirthdate -> PrivacySetting.Birthdate
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

/** The users and groups these rules always or never allow, as TDLib lists them. */
internal fun TdApi.UserPrivacySettingRules.toExceptionLists(): PrivacyExceptionLists =
    PrivacyExceptionLists(
        alwaysUserIds =
            rules.filterIsInstance<TdApi.UserPrivacySettingRuleAllowUsers>().flatMap {
                it.userIds.asList()
            },
        alwaysChatIds =
            rules.filterIsInstance<TdApi.UserPrivacySettingRuleAllowChatMembers>().flatMap { it.chatIds.asList() },
        neverUserIds =
            rules.filterIsInstance<TdApi.UserPrivacySettingRuleRestrictUsers>().flatMap {
                it.userIds.asList()
            },
        neverChatIds =
            rules.filterIsInstance<TdApi.UserPrivacySettingRuleRestrictChatMembers>().flatMap { it.chatIds.asList() },
    )

/**
 * These rules with [lists] as their users and groups always and never allowed: those rules go, and the new ones come
 * first, the never allowed before the always allowed (one id is never on both sides), then every other rule in its
 * order (Telegram Premium subscribers, bots, the main choice). Call it on rules just received from TDLib, as
 * [withChoice].
 */
internal fun TdApi.UserPrivacySettingRules.withExceptions(lists: PrivacyExceptionLists): TdApi.UserPrivacySettingRules {
    val kept = rules.filterNot { it.isUserOrGroupRule() }
    val exceptions =
        listOfNotNull(
            lists.neverUserIds.takeIf { it.isNotEmpty() }?.let {
                TdApi.UserPrivacySettingRuleRestrictUsers(it.toLongArray())
            },
            lists.neverChatIds.takeIf { it.isNotEmpty() }?.let {
                TdApi.UserPrivacySettingRuleRestrictChatMembers(it.toLongArray())
            },
            lists.alwaysUserIds.takeIf { it.isNotEmpty() }?.let {
                TdApi.UserPrivacySettingRuleAllowUsers(it.toLongArray())
            },
            lists.alwaysChatIds.takeIf { it.isNotEmpty() }?.let {
                TdApi.UserPrivacySettingRuleAllowChatMembers(it.toLongArray())
            },
        )
    return TdApi.UserPrivacySettingRules((exceptions + kept).toTypedArray())
}

/** A rule about some users or the members of some groups. */
private fun TdApi.UserPrivacySettingRule.isUserOrGroupRule(): Boolean =
    this is TdApi.UserPrivacySettingRuleAllowUsers ||
        this is TdApi.UserPrivacySettingRuleAllowChatMembers ||
        this is TdApi.UserPrivacySettingRuleRestrictUsers ||
        this is TdApi.UserPrivacySettingRuleRestrictChatMembers

/** A rule about everyone or about all contacts, rather than about some users, groups or bots. */
private fun TdApi.UserPrivacySettingRule.isMainRule(): Boolean =
    this is TdApi.UserPrivacySettingRuleAllowAll ||
        this is TdApi.UserPrivacySettingRuleAllowContacts ||
        this is TdApi.UserPrivacySettingRuleRestrictAll ||
        this is TdApi.UserPrivacySettingRuleRestrictContacts
