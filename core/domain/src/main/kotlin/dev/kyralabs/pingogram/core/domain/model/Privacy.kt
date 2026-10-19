package dev.kyralabs.pingogram.core.domain.model

/**
 * A privacy setting of the account (`REQUIREMENTS.md` FR-S1, Privacy and security): who can see or do something. The
 * ones Pingogram shows, in its order. TDLib has more (bio, birthdate, finding by phone number, peer-to-peer calls,
 * gifts, paid messages); Pingogram leaves them as they are.
 */
enum class PrivacySetting {
    /** Who can see the phone number. */
    PhoneNumber,

    /** Who can see when the user was last online, and whether they are online now. */
    LastSeen,

    /** Who can see the profile photos. */
    ProfilePhoto,

    /** Whether messages forwarded from the user link to their account. */
    ForwardedMessages,

    /** Who can call the user. */
    Calls,

    /** Who can add the user to groups and channels. */
    ChatInvites,

    /** Who can send the user voice and video messages in private chats; only Premium subscribers can limit it. */
    VoiceMessages,
}

/** The main choice of a [PrivacySetting]: who may, unless an exception says otherwise. */
enum class PrivacyChoice {
    Everybody,
    Contacts,
    Nobody,
}

/**
 * What a [PrivacySetting] allows: its main [choice] and, counted, its [exceptions].
 *
 * TDLib matches a setting's rules in order, and the first that matches decides; when none matches, it is not allowed.
 * [choice] is the first of "allow all", "allow contacts" and "restrict all" among them, [PrivacyChoice.Nobody] when
 * there is none. Rules for bots, which only some settings use, are neither a choice nor counted.
 */
data class PrivacyRules(
    val choice: PrivacyChoice,
    val exceptions: PrivacyExceptions = PrivacyExceptions(),
) {
    /** Whether some people are allowed although [choice] leaves them out; with [PrivacyChoice.Everybody] none are. */
    val hasAlwaysAllowed: Boolean
        get() = choice != PrivacyChoice.Everybody && exceptions.hasAllowed

    /** Whether some people are not allowed although [choice] includes them; with [PrivacyChoice.Nobody] none are. */
    val hasNeverAllowed: Boolean
        get() = choice != PrivacyChoice.Nobody && exceptions.hasRestricted

    /** Whether an exception changes what [choice] allows. */
    val hasExceptions: Boolean
        get() = hasAlwaysAllowed || hasNeverAllowed
}

/**
 * The people a [PrivacySetting] treats differently from its main choice: users and groups (their members) that are
 * always allowed or never allowed, and, for invitations to chats only, Telegram Premium subscribers. Only counted: the
 * lists stay in TDLib, and changing the main choice keeps them.
 */
data class PrivacyExceptions(
    val allowedUserCount: Int = 0,
    val allowedChatCount: Int = 0,
    val restrictedUserCount: Int = 0,
    val restrictedChatCount: Int = 0,
    val allowsPremiumUsers: Boolean = false,
) {
    val hasAllowed: Boolean
        get() = allowedUserCount > 0 || allowedChatCount > 0 || allowsPremiumUsers

    val hasRestricted: Boolean
        get() = restrictedUserCount > 0 || restrictedChatCount > 0
}

/** TDLib reported new [rules] for [setting], e.g. after a change here or on another device. */
data class PrivacyRulesChange(
    val setting: PrivacySetting,
    val rules: PrivacyRules,
)
