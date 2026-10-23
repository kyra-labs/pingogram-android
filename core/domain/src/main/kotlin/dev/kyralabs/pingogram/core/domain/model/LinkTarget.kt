package dev.kyralabs.pingogram.core.domain.model

/**
 * Where a Telegram link (`tg://…`, `https://t.me/…`) leads, as `LinkRepository.resolve` finds it with TDLib's
 * `getInternalLinkType` (`docs/adr/0013-deep-links.md`). The app opens the screen each target names; the target of a
 * screen Pingogram does not have yet ([MessageDraft]) carries what it will need, and the app says it cannot open it
 * yet.
 *
 * Nothing here changes anything on Telegram: joining a chat, confirming a login and sending a message wait for the
 * user's confirmation on the screen the target opens. `toString()` of the targets that carry user content leaves it
 * out, so targets can be logged (`REQUIREMENTS.md` §6.2).
 */
sealed interface LinkTarget {
    /**
     * The conversation of chat [chatId], at message [messageId] if the link named one. [draftText], a text the link
     * suggests writing, goes into the composer if it has none ([LinkTarget.Chat] of `t.me/username?text=…`).
     */
    data class Chat(
        val chatId: Long,
        val messageId: Long? = null,
        val draftText: String? = null,
    ) : LinkTarget {
        override fun toString() = "Chat(chatId=$chatId, messageId=$messageId, hasDraft=${draftText != null})"
    }

    /**
     * Starting bot [botUserId] in its private chat [chatId] with [parameter] (`t.me/bot?start=…`): the conversation
     * opens with "Start" in the composer's place, which sends the parameter to the bot (`sendBotStartMessage`) only
     * once the user taps it; a link that asks to start at once (`autostart`) waits for the tap too.
     */
    data class BotStart(
        val chatId: Long,
        val botUserId: Long,
        val parameter: String,
    ) : LinkTarget {
        override fun toString() = "BotStart(chatId=$chatId, botUserId=$botUserId, …)"
    }

    /** The info page of group or channel [chatId] (`t.me/username?profile`). */
    data class ChatInfo(
        val chatId: Long,
    ) : LinkTarget

    /** The profile of user [userId] (`t.me/username` of a person, `t.me/+15551234567`, a contact token). */
    data class UserProfile(
        val userId: Long,
    ) : LinkTarget

    /** Editing the user's own profile (`tg://settings/edit_profile`, `tg://settings/my_profile`). */
    data object EditProfile : LinkTarget

    /** The Contacts tab (`tg://contacts`). */
    data object Contacts : LinkTarget

    /** Settings, at [page] if the link names one Pingogram has (`tg://settings/privacy`); else Settings home. */
    data class Settings(
        val page: SettingsPage? = null,
    ) : LinkTarget

    /** Starting a chat of [kind] (`tg://new_group` …): the New chat screen. */
    data class NewChat(
        val kind: NewChatKind,
    ) : LinkTarget

    /** Global search (`tg://chats/search`). */
    data object Search : LinkTarget

    /**
     * An invitation to a chat ([inviteLink], `t.me/+…`, `t.me/joinchat/…`): the join sheet shows the chat and asks
     * before joining.
     */
    data class ChatInvite(
        val inviteLink: String,
    ) : LinkTarget {
        override fun toString() = "ChatInvite(…)"
    }

    /**
     * A shared chat folder ([inviteLink], `t.me/addlist/…`): its sheet shows the folder and its chats, and asks before
     * adding the folder and joining the chats the user picks.
     */
    data class ChatFolderInvite(
        val inviteLink: String,
    ) : LinkTarget {
        override fun toString() = "ChatFolderInvite(…)"
    }

    /**
     * A login of another device by QR code ([link], `tg://login?token=…`): its page asks before confirming it with
     * `confirmQrCodeAuthentication`, then shows the device it logged in.
     */
    data class QrCodeLogin(
        val link: String,
    ) : LinkTarget {
        override fun toString() = "QrCodeLogin(…)"
    }

    /**
     * Boosting channel or supergroup [chatId] (`t.me/channel?boost`, `t.me/boost?c=123`): its boosts, read only in
     * Pingogram (`REQUIREMENTS.md` FR-P5).
     */
    data class ChatBoost(
        val chatId: Long,
    ) : LinkTarget

    /** The user's own QR code, to share their profile (`tg://settings/qr_code`). */
    data object MyQrCode : LinkTarget

    /**
     * A message to share ([text], `t.me/share?url=…`): its screen will let the user pick the chat it goes to, with the
     * text in the composer, never sent by itself. [containsLink]: the text starts with a link the link carried.
     */
    data class MessageDraft(
        val text: FormattedText,
        val containsLink: Boolean,
    ) : LinkTarget

    /**
     * A login code (`t.me/login/12345`): never entered or acted upon, since someone may be trying to log in to the
     * user's account with it. The app warns not to share it.
     */
    data object LoginCode : LinkTarget

    /** The link leads to nothing the user can see: [subject] is what it named (`DomainError.NotFound` of a step). */
    data class NotFound(
        val subject: LinkSubject,
    ) : LinkTarget

    /**
     * A link Pingogram does not open ([url]): a kind it does not support, or one TDLib does not know. The app offers a
     * browser for `https` links.
     */
    data class Unsupported(
        val url: String,
    ) : LinkTarget {
        override fun toString() = "Unsupported(…)"
    }
}

/** The pages of Settings that a `tg://settings/…` link can open. */
enum class SettingsPage {
    Appearance,
    ChatFolders,
    DataAndStorage,
    ActiveSessions,
    Language,
    Notifications,
    PrivacyAndSecurity,
}

/** What a New chat link asks to create. */
enum class NewChatKind {
    Private,
    Group,
    Channel,
}

/** What a link named that leads to nothing ([LinkTarget.NotFound]), so the app can say what is missing. */
enum class LinkSubject {
    /** `t.me/username`: nobody has the username. */
    Username,

    /** `t.me/+15551234567`: nobody on Telegram has the number, or lets it be found by it. */
    PhoneNumber,

    /** A contact token that expired or was revoked. */
    UserToken,

    /** `t.me/channel/123`: the chat or the message is gone, or the user cannot see it. */
    Message,

    /** `t.me/boost?c=123`: the channel or group is gone, or the user cannot see it. */
    Chat,
}
