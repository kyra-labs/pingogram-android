package dev.kyralabs.pingogram.core.domain.model

/**
 * What TDLib suggests doing about a chat, at the top of its conversation (td_api.tl `ChatActionBar`), until the user
 * acts or dismisses it (`removeChatActionBar`): mostly a chat someone new started.
 */
sealed interface ChatActionBar {
    /**
     * A private chat with someone not in the contacts who wrote first: report, add to contacts or block; what Telegram
     * tells of their account ([account], null when it tells nothing), and whether the chat can leave the archive it was
     * put in ([canUnarchive]).
     */
    data class ReportAddBlock(
        val canUnarchive: Boolean,
        val account: AccountInfo? = null,
    ) : ChatActionBar

    /** A group or channel someone added the user to: report it as spam. */
    data class ReportSpam(
        val canUnarchive: Boolean,
    ) : ChatActionBar

    /** A private chat with someone not in the contacts: add them. */
    data object AddContact : ChatActionBar

    /** A private chat with a contact who cannot see the user's phone number: share it with them. */
    data object SharePhoneNumber : ChatActionBar

    /**
     * A private chat with someone who asked to join [title], a group or [isChannel] a channel the user administers, at
     * [requestDate] (Unix seconds): why the chat exists.
     */
    data class JoinRequest(
        val title: String,
        val isChannel: Boolean,
        val requestDate: Long,
    ) : ChatActionBar {
        override fun toString() = "JoinRequest(isChannel=$isChannel, …)"
    }
}

/**
 * What Telegram tells of an account that wrote first (td_api.tl `accountInfo`): when it signed up ([registrationMonth]
 * 1–12 of [registrationYear], 0 when unknown), the country of its phone number ([phoneCountryCode], ISO 3166-1
 * alpha-2, empty when unknown), and when it last changed its name and its photo (Unix seconds, 0 when it did not
 * lately).
 */
data class AccountInfo(
    val registrationMonth: Int = 0,
    val registrationYear: Int = 0,
    val phoneCountryCode: String = "",
    val lastNameChangeDate: Long = 0L,
    val lastPhotoChangeDate: Long = 0L,
)
