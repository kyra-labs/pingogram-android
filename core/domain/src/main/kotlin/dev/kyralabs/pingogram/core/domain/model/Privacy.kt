package dev.kyralabs.pingogram.core.domain.model

/**
 * A privacy setting of the account (`REQUIREMENTS.md` FR-S1, Privacy and security): who can see or do something. The
 * ones Pingogram shows, in its order. TDLib has more (finding by phone number, peer-to-peer calls,
 * gifts, paid messages); Pingogram leaves them as they are.
 */
enum class PrivacySetting {
    /** Who can see the phone number. */
    PhoneNumber,

    /** Who can see when the user was last online, and whether they are online now. */
    LastSeen,

    /** Who can see the profile photos. */
    ProfilePhoto,

    /** Who can see the bio. */
    Bio,

    /** Who can see the birthday, where the user set one (Edit profile). */
    Birthdate,

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

/**
 * Who may start a chat with the user (td_api.tl `newChatPrivacySettings`), the "Messages" privacy setting.
 *
 * @property allowsNonContacts people who are not contacts may write first; Telegram Premium subscribers may whatever
 *   it is.
 * @property paidMessageStars Telegram Stars non-contacts pay per message, as another app set it; 0 for none. Pingogram
 *   offers no paid messages and keeps what is set while non-contacts may write.
 * @property canRestrict Telegram lets this account keep non-contacts out (td_api.tl option
 *   `can_set_new_chat_privacy_settings`, which Telegram gives Telegram Premium subscribers).
 */
data class NewChatPrivacy(
    val allowsNonContacts: Boolean,
    val paidMessageStars: Long = 0,
    val canRestrict: Boolean = false,
)

/** Which exceptions of a [PrivacySetting]: those always allowed, or those never allowed, whatever its main choice. */
enum class PrivacyExceptionSide { Always, Never }

/**
 * The users and groups (their members) a [PrivacySetting] treats apart from its main choice, by id, as TDLib lists
 * them (td_api.tl `userPrivacySettingRuleAllowUsers` and the others). Chat ids are those of basic groups and
 * supergroups.
 */
data class PrivacyExceptionLists(
    val alwaysUserIds: List<Long> = emptyList(),
    val alwaysChatIds: List<Long> = emptyList(),
    val neverUserIds: List<Long> = emptyList(),
    val neverChatIds: List<Long> = emptyList(),
) {
    fun userIds(side: PrivacyExceptionSide): List<Long> =
        if (side == PrivacyExceptionSide.Always) alwaysUserIds else neverUserIds

    fun chatIds(side: PrivacyExceptionSide): List<Long> =
        if (side == PrivacyExceptionSide.Always) alwaysChatIds else neverChatIds

    /**
     * These lists with [side] holding [userIds] and [chatIds]; they leave the other side, since one user or group
     * cannot be both always and never allowed.
     */
    fun with(
        side: PrivacyExceptionSide,
        userIds: List<Long>,
        chatIds: List<Long>,
    ): PrivacyExceptionLists =
        when (side) {
            PrivacyExceptionSide.Always -> {
                PrivacyExceptionLists(
                    alwaysUserIds = userIds,
                    alwaysChatIds = chatIds,
                    neverUserIds = neverUserIds - userIds.toSet(),
                    neverChatIds = neverChatIds - chatIds.toSet(),
                )
            }

            PrivacyExceptionSide.Never -> {
                PrivacyExceptionLists(
                    alwaysUserIds = alwaysUserIds - userIds.toSet(),
                    alwaysChatIds = alwaysChatIds - chatIds.toSet(),
                    neverUserIds = userIds,
                    neverChatIds = chatIds,
                )
            }
        }
}
