package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.mapper.toFormattedText
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.domain.model.LinkTarget
import dev.kyralabs.pingogram.core.domain.model.NewChatKind
import dev.kyralabs.pingogram.core.domain.model.SettingsPage
import dev.kyralabs.pingogram.core.domain.repository.LinkRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import org.drinkless.tdlib.TdApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LinkRepository] on TDLib's `getInternalLinkType`, which knows every form of Telegram's links (`tg://resolve`,
 * `t.me/username`, `username.t.me`, `t.me/c/123/456` …) better than any parsing of ours, then on the lookup each kind
 * needs (`docs/adr/0013-deep-links.md`). No cache: TDLib answers usernames and messages it resolved before from memory.
 *
 * - **Usernames** (`searchPublicChat`): a person opens their profile, a bot its chat (where the user talks to it), a
 *   group or channel its chat, or its info with `?profile`. A link with `?text=` opens the chat, whose composer gets
 *   the text ([prefillDraft]).
 * - **Phone numbers** (`searchUserByPhoneNumber`, not only local) and **contact tokens** (`searchUserByToken`) as a
 *   username of a person.
 * - **Bot start links** (`t.me/bot?start=…`, `searchPublicChat`): the bot's chat with the start parameter, which its
 *   "Start" sends once the user taps it; a link's `autostart` is not honoured, nothing starts by itself.
 * - **Messages** (`getMessageLinkInfo`): the chat at the message; the chat alone when the message is gone but the chat
 *   is there. A message in a topic or a thread opens in the chat itself, as `ChatRoute` has no topic yet.
 * - **Boosts** (`getChatBoostLinkInfo`): the chat whose boosts the link shows.
 * - **Saved Messages** (`getMe`, `createPrivateChat`), and the pages of the app a link names.
 *
 * A lookup that finds nothing (`USERNAME_NOT_OCCUPIED`, any other 400, or a 404) is [LinkTarget.NotFound]; network
 * errors and `FLOOD_WAIT` are failures. A link `getInternalLinkType` refuses is no internal link at all:
 * [LinkTarget.Unsupported], like the kinds Pingogram does not open.
 */
@Singleton
class TdlibLinkRepository
    @Inject
    constructor(
        private val client: TdlibClient,
    ) : LinkRepository {
        override suspend fun resolve(link: String): Outcome<LinkTarget> {
            val type =
                when (val found = client.request(TdApi.GetInternalLinkType(link))) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return if (found.error.isTransient()) found else unsupported(link)
                }
            return when (type) {
                is TdApi.InternalLinkTypePublicChat -> {
                    publicChat(type.chatUsername, type.draftText, type.openProfile)
                }

                is TdApi.InternalLinkTypeUserPhoneNumber -> {
                    userByPhoneNumber(type)
                }

                is TdApi.InternalLinkTypeUserToken -> {
                    val search = TdApi.SearchUserByToken(type.token)
                    user(search, LinkSubject.UserToken, draftText = null, openProfile = true)
                }

                is TdApi.InternalLinkTypeMessage -> {
                    message(type.url)
                }

                is TdApi.InternalLinkTypeSavedMessages -> {
                    savedMessages()
                }

                is TdApi.InternalLinkTypeChatBoost -> {
                    boostedChat(type.url)
                }

                is TdApi.InternalLinkTypeBotStart -> {
                    // td_api.tl: the user must be a bot, whose username opens its private chat, of its user's id.
                    publicChat(type.botUsername, draftText = null, openProfile = false).map { target ->
                        if (target is LinkTarget.Chat && target.chatId > 0) {
                            LinkTarget.BotStart(target.chatId, target.chatId, type.startParameter)
                        } else {
                            LinkTarget.NotFound(LinkSubject.Username)
                        }
                    }
                }

                else -> {
                    Outcome.Success(type.toTarget(link))
                }
            }
        }

        override suspend fun prefillDraft(
            chatId: Long,
            text: String,
        ): Outcome<Unit> {
            if (text.isBlank()) return Outcome.Success(Unit)
            return when (val found = client.request(TdApi.GetChat(chatId))) {
                is Outcome.Success -> {
                    if (found.value.draftMessage != null) Outcome.Success(Unit) else client.setDraft(chatId, text)
                }

                is Outcome.Failure -> {
                    found
                }
            }
        }

        private suspend fun publicChat(
            username: String,
            draftText: String?,
            openProfile: Boolean,
        ): Outcome<LinkTarget> =
            when (val found = client.request(TdApi.SearchPublicChat(username))) {
                is Outcome.Success -> chatTarget(found.value, draftText, openProfile)
                is Outcome.Failure -> found.orNotFound(LinkSubject.Username)
            }

        /**
         * Where a link to [chat] leads: for a user's chat as [userTarget] says; for a group or channel, its chat, or
         * its info if the link asks for the profile ([openProfile]).
         */
        private suspend fun chatTarget(
            chat: TdApi.Chat,
            draftText: String?,
            openProfile: Boolean,
        ): Outcome<LinkTarget> {
            val type = chat.type
            return if (type is TdApi.ChatTypePrivate) {
                val isBot = client.request(TdApi.GetUser(type.userId)).isBot()
                userTarget(type.userId, chat.id, isBot, draftText, openProfile)
            } else {
                val draft = draftText.orNull()
                Outcome.Success(
                    if (openProfile) LinkTarget.ChatInfo(chat.id) else LinkTarget.Chat(chat.id, null, draft),
                )
            }
        }

        private suspend fun userByPhoneNumber(type: TdApi.InternalLinkTypeUserPhoneNumber): Outcome<LinkTarget> =
            user(
                TdApi.SearchUserByPhoneNumber(type.phoneNumber, false),
                LinkSubject.PhoneNumber,
                type.draftText,
                type.openProfile,
            )

        /** The user [search] finds ([userTarget]); nobody found is [LinkTarget.NotFound] of [subject]. */
        private suspend fun user(
            search: TdApi.Function<TdApi.User>,
            subject: LinkSubject,
            draftText: String?,
            openProfile: Boolean,
        ): Outcome<LinkTarget> {
            val user =
                when (val found = client.request(search)) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return found.orNotFound(subject)
                }
            return userTarget(user.id, chatId = null, user.type is TdApi.UserTypeBot, draftText, openProfile)
        }

        /**
         * Where a link to user [userId] leads: their profile, or the chat with them (a bot, or a text to write to them)
         * unless the link asks for the profile ([openProfile]); [chatId] is their chat if known.
         */
        private suspend fun userTarget(
            userId: Long,
            chatId: Long?,
            isBot: Boolean,
            draftText: String?,
            openProfile: Boolean,
        ): Outcome<LinkTarget> {
            val draft = draftText.orNull()
            if (openProfile || (!isBot && draft == null)) return Outcome.Success(LinkTarget.UserProfile(userId))
            val chat = chatId?.let { Outcome.Success(it) } ?: client.privateChat(userId)
            return chat.map { id -> LinkTarget.Chat(id, null, draft) }
        }

        private suspend fun message(url: String): Outcome<LinkTarget> =
            when (val found = client.request(TdApi.GetMessageLinkInfo(url))) {
                is Outcome.Success -> Outcome.Success(found.value.toTarget())
                is Outcome.Failure -> found.orNotFound(LinkSubject.Message)
            }

        /** The chat a boost link names (`getChatBoostLinkInfo`); td_api.tl: `chat_id` 0 when it is not accessible. */
        private suspend fun boostedChat(url: String): Outcome<LinkTarget> =
            when (val found = client.request(TdApi.GetChatBoostLinkInfo(url))) {
                is Outcome.Success -> {
                    val chatId = found.value.chatId
                    Outcome.Success(
                        if (chatId == 0L) LinkTarget.NotFound(LinkSubject.Chat) else LinkTarget.ChatBoost(chatId),
                    )
                }

                is Outcome.Failure -> {
                    found.orNotFound(LinkSubject.Chat)
                }
            }

        private suspend fun savedMessages(): Outcome<LinkTarget> {
            val me =
                when (val found = client.request(TdApi.GetMe())) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return found
                }
            return client.privateChat(me.id).map { LinkTarget.Chat(it) }
        }
    }

/**
 * The target of a link that needs no lookup: a page of the app, or what a screen Pingogram does not have yet needs.
 * Kinds outside `docs/adr/0013-deep-links.md`'s scope are [LinkTarget.Unsupported] of [link].
 */
private fun TdApi.InternalLinkType.toTarget(link: String): LinkTarget =
    when (this) {
        is TdApi.InternalLinkTypeMyProfilePage -> LinkTarget.EditProfile

        is TdApi.InternalLinkTypeContactsPage -> LinkTarget.Contacts

        is TdApi.InternalLinkTypeSettings -> section.toTarget()

        is TdApi.InternalLinkTypeNewPrivateChat -> LinkTarget.NewChat(NewChatKind.Private)

        is TdApi.InternalLinkTypeNewGroupChat -> LinkTarget.NewChat(NewChatKind.Group)

        is TdApi.InternalLinkTypeNewChannelChat -> LinkTarget.NewChat(NewChatKind.Channel)

        is TdApi.InternalLinkTypeSearch -> LinkTarget.Search

        is TdApi.InternalLinkTypeChatInvite -> LinkTarget.ChatInvite(inviteLink)

        // The link itself is what confirmQrCodeAuthentication takes; TDLib's type carries nothing.
        is TdApi.InternalLinkTypeQrCodeAuthentication -> LinkTarget.QrCodeLogin(link)

        is TdApi.InternalLinkTypeMessageDraft -> LinkTarget.MessageDraft(text.toFormattedText(), containsLink)

        // Never entered anywhere: someone may be logging in to the user's account with it.
        is TdApi.InternalLinkTypeAuthenticationCode -> LinkTarget.LoginCode

        else -> LinkTarget.Unsupported(link)
    }

/** The page of Settings a `tg://settings/…` link names, Settings home for the ones Pingogram has no page for. */
private fun TdApi.SettingsSection?.toTarget(): LinkTarget =
    when (this) {
        is TdApi.SettingsSectionEditProfile -> LinkTarget.EditProfile
        is TdApi.SettingsSectionAppearance -> LinkTarget.Settings(SettingsPage.Appearance)
        is TdApi.SettingsSectionChatFolders -> LinkTarget.Settings(SettingsPage.ChatFolders)
        is TdApi.SettingsSectionDataAndStorage -> LinkTarget.Settings(SettingsPage.DataAndStorage)
        is TdApi.SettingsSectionDevices -> LinkTarget.Settings(SettingsPage.ActiveSessions)
        is TdApi.SettingsSectionLanguage -> LinkTarget.Settings(SettingsPage.Language)
        is TdApi.SettingsSectionNotifications -> LinkTarget.Settings(SettingsPage.Notifications)
        is TdApi.SettingsSectionPrivacyAndSecurity -> LinkTarget.Settings(SettingsPage.PrivacyAndSecurity)
        is TdApi.SettingsSectionQrCode -> LinkTarget.MyQrCode
        else -> LinkTarget.Settings()
    }

/** Sets [text] as the draft of chat [chatId]: the chat itself, no topic, no reply. */
private suspend fun TdlibClient.setDraft(
    chatId: Long,
    text: String,
): Outcome<Unit> {
    val content = TdApi.DraftMessageContentText(TdApi.FormattedText(text, emptyArray()), null)
    val draft = TdApi.DraftMessage(null, 0, content, 0, null)
    return request(TdApi.SetChatDraftMessage(chatId, null, draft)).map {}
}

/** The id of the private chat with user [userId], which TDLib makes locally if there is none yet. */
private suspend fun TdlibClient.privateChat(userId: Long): Outcome<Long> =
    request(TdApi.CreatePrivateChat(userId, false)).map { it.id }

/**
 * The chat at the linked message; td_api.tl: `chat_id` is 0 when the link is inaccessible, `message` null when it was
 * not found, which opens the chat alone.
 */
private fun TdApi.MessageLinkInfo.toTarget(): LinkTarget =
    if (chatId == 0L) LinkTarget.NotFound(LinkSubject.Message) else LinkTarget.Chat(chatId, message?.id)

private fun unsupported(link: String): Outcome<LinkTarget> = Outcome.Success(LinkTarget.Unsupported(link))

/** Whether trying again later may succeed: no network, a `FLOOD_WAIT`, or a session that is not ready. */
private fun DomainError.isTransient(): Boolean =
    this == DomainError.Network || this is DomainError.FloodWait || this == DomainError.Unauthorized

/**
 * A lookup's failure: [LinkTarget.NotFound] of [subject] when Telegram knows nothing of what the link names (a 404,
 * or a 400 such as `USERNAME_NOT_OCCUPIED`, `USERNAME_INVALID`, `PHONE_NOT_OCCUPIED`, `CHANNEL_PRIVATE`); else the
 * failure.
 */
private fun Outcome.Failure.orNotFound(subject: LinkSubject): Outcome<LinkTarget> {
    val error = error
    val isUnknown =
        error == DomainError.NotFound ||
            error == DomainError.UsernameInvalid ||
            (error as? DomainError.Unknown)?.code == BAD_REQUEST
    return if (isUnknown) Outcome.Success(LinkTarget.NotFound(subject)) else this
}

private const val BAD_REQUEST = 400

/** Whether `getUser` answered with a bot; a failure counts as a person, whose profile opens. */
private fun Outcome<TdApi.User>.isBot(): Boolean = (this as? Outcome.Success)?.value?.type is TdApi.UserTypeBot

/** Null for an empty text, which is how TDLib says there is none. */
private fun String?.orNull(): String? = this?.takeIf { it.isNotEmpty() }
