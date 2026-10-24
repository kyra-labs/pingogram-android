package dev.kyralabs.pingogram.feature.profile.ui

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.FormattedText
import dev.kyralabs.pingogram.core.domain.model.Photo
import dev.kyralabs.pingogram.core.domain.model.ProfilePhoto
import dev.kyralabs.pingogram.core.ui.text.MentionTarget
import dev.kyralabs.pingogram.core.ui.text.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What a user's profile shows (`REQUIREMENTS.md` FR-P2), apart from its shared media ([SharedMediaUiState]). */
@Immutable
sealed interface ProfileUiState {
    data object Loading : ProfileUiState

    /** The user could not be found ([DomainError.NotFound]) or read, because of [error]; [ProfileEvent.Retry]. */
    data class Error(
        val error: DomainError,
    ) : ProfileUiState

    data class Content(
        val header: ProfileHeader,
        val info: ProfileInfo,
        val actions: ProfileActions,
        val photos: ProfilePhotos = ProfilePhotos(),
        val dialog: ProfileDialog? = null,
    ) : ProfileUiState
}

/**
 * The top of the profile: the avatar, the name and the status line, and the badges after the name.
 *
 * @property userId the avatar's id, which picks its placeholder.
 * @property name the user's full name; "Deleted Account" for a deleted one.
 * @property placeholderIcon a Material Symbol on the avatar placeholder instead of initials: the person of a deleted
 *   account.
 * @property status "online", "last seen …", "bot"; null for a deleted account.
 * @property isOnline the user is online now: the status shows in `primary`.
 */
@Immutable
data class ProfileHeader(
    val userId: Long,
    val name: UiText,
    val photo: ProfilePhoto?,
    @param:DrawableRes val placeholderIcon: Int? = null,
    val status: UiText? = null,
    val isOnline: Boolean = false,
    val isVerified: Boolean = false,
    val isPremium: Boolean = false,
    val warning: ProfileWarning? = null,
) {
    override fun toString() = "ProfileHeader(userId=$userId, …)"
}

/** Telegram's warning about an account, after its name in the error role. */
enum class ProfileWarning {
    /** Many users reported it as a scam. */
    Scam,

    /** Many users reported it as impersonating someone. */
    Fake,
}

/**
 * The information rows, each null (or empty) when there is nothing to show.
 *
 * @property bio the bio with its links and mentions, or a bot's description ([isBot]).
 * @property usernames the active usernames without `@`, the first one to show first.
 * @property phoneNumber the phone number's digits, `+` first, as Telegram sends them; the row formats it.
 * @property birthday the birthday, formatted ("December 10, 1815", or "December 10" without a year).
 * @property personalChannel the channel the user shows on their profile (td_api.tl `personal_chat_id`).
 */
@Immutable
data class ProfileInfo(
    val bio: FormattedText? = null,
    val isBot: Boolean = false,
    val usernames: ImmutableList<String> = persistentListOf(),
    val phoneNumber: String? = null,
    val birthday: String? = null,
    val personalChannel: PersonalChannel? = null,
) {
    val isEmpty: Boolean
        get() = bio == null && usernames.isEmpty() && phoneNumber == null && birthday == null && personalChannel == null

    override fun toString() = "ProfileInfo(usernames=${usernames.size}, …)"
}

/** A channel a user shows on their profile, by its chat [chatId] and [title]. */
@Immutable
data class PersonalChannel(
    val chatId: Long,
    val title: String,
)

/**
 * What the user can do on the profile. An action that does not apply is false (or null): nothing to block on one's
 * own profile, no secret chat with a bot.
 *
 * @property isSelf the profile is the current user's: "Message" opens Saved Messages.
 * @property canMessage a chat can be opened; not for a deleted account TDLib has no chat with.
 * @property isMuted the private chat's notifications are off; null where they cannot be changed (Saved Messages, or
 *   until the chat is known).
 * @property canStartSecretChat a secret chat can be started: with regular users only, not with oneself.
 * @property isStartingSecretChat a secret chat was asked for, and TDLib has not answered yet.
 * @property contact whether the user can be added to the contacts or the contact edited; null for oneself, bots and
 *   deleted accounts.
 * @property isBlocked the user is blocked; null where blocking does not apply (oneself) or the full info is not known.
 * @property isChangingBlock blocking or unblocking was asked for, and TDLib has not answered yet.
 * @property canReport the account can be reported; not one's own.
 * @property exportChatId the private chat "Export chat" exports (Saved Messages on one's own profile); null until it is
 *   known, or where its content may not be saved.
 * @property callUserId the user "Call" calls (`REQUIREMENTS.md` FR-V1): a person, not oneself, whose privacy settings
 *   allow it; null otherwise.
 */
@Immutable
data class ProfileActions(
    val isSelf: Boolean = false,
    val canMessage: Boolean = false,
    val isMuted: Boolean? = null,
    val canStartSecretChat: Boolean = false,
    val isStartingSecretChat: Boolean = false,
    val contact: ContactAction? = null,
    val isBlocked: Boolean? = null,
    val isChangingBlock: Boolean = false,
    val canReport: Boolean = false,
    val callUserId: Long? = null,
    val exportChatId: Long? = null,
)

/** What the contact row does. */
enum class ContactAction {
    Add,
    Edit,
}

/**
 * The user's profile photos for the carousel, newest first, as far as loaded.
 *
 * @property totalCount how many the user has; the carousel shows only with more than one, as the first is the avatar.
 */
@Immutable
data class ProfilePhotos(
    val items: ImmutableList<ProfilePhotoItem> = persistentListOf(),
    val totalCount: Int = 0,
)

/** One photo of the carousel: its sizes, from which the carousel loads the smallest that fills an item. */
@Immutable
data class ProfilePhotoItem(
    val id: Long,
    val photo: Photo,
)

/** A confirmation the profile shows. */
@Immutable
sealed interface ProfileDialog {
    /** Starting a secret chat, which lives on this device only. */
    data object SecretChat : ProfileDialog

    /** Blocking the user named [name]. */
    data class Block(
        val name: UiText,
    ) : ProfileDialog
}

/** What the user does on the profile, handled by [ProfileViewModel.onEvent]. */
sealed interface ProfileEvent {
    /** Look the user up again after it failed. */
    data object Retry : ProfileEvent

    /** Open the private chat (Saved Messages on one's own profile). */
    data object SendMessage : ProfileEvent

    /** Ask whether to start a secret chat. */
    data object StartSecretChat : ProfileEvent

    data object ConfirmSecretChat : ProfileEvent

    /** Mute or unmute the private chat. */
    data object ToggleMute : ProfileEvent

    /** Ask whether to block the user. */
    data object Block : ProfileEvent

    data object ConfirmBlock : ProfileEvent

    data object Unblock : ProfileEvent

    /** Close the dialog shown without doing anything. */
    data object DismissDialog : ProfileEvent

    /** Report the account: opens the report sheet. */
    data object Report : ProfileEvent

    /** Report with the reason at [index] of the step the sheet shows. */
    data class ChooseReportOption(
        val index: Int,
    ) : ProfileEvent

    /** Report with the comment written. */
    data object SendReportComment : ProfileEvent

    data object DismissReport : ProfileEvent

    /** Put `@username` on the clipboard. */
    data class CopyUsername(
        val username: String,
    ) : ProfileEvent {
        override fun toString() = "CopyUsername(…)"
    }

    /** Share the `t.me` link of [username]. */
    data class ShareUsername(
        val username: String,
    ) : ProfileEvent {
        override fun toString() = "ShareUsername(…)"
    }

    data object CopyPhone : ProfileEvent

    /** Open the dialer with the phone number. */
    data object CallPhone : ProfileEvent

    /** Put the bio (or a bot's description) on the clipboard. */
    data object CopyBio : ProfileEvent

    /** The carousel reached its last loaded photos. */
    data object LoadMorePhotos : ProfileEvent

    /** The personal channel's row was tapped: open its chat. */
    data class OpenPersonalChannel(
        val chatId: Long,
    ) : ProfileEvent

    /** A mention in the bio was tapped: open who it names. */
    data class OpenMention(
        val target: MentionTarget,
    ) : ProfileEvent
}

/** One-off side effects of [ProfileViewModel], handled once by its route. */
sealed interface ProfileEffect {
    /** Open chat [chatId]'s conversation. */
    data class OpenChat(
        val chatId: Long,
    ) : ProfileEffect

    /** Open the profile of user [userId], whom a mention named. */
    data class OpenProfile(
        val userId: Long,
    ) : ProfileEffect

    /** An action failed: a snackbar shows the error's text, none for [DomainError.Silent]. Errors are never dialogs. */
    data class ShowError(
        val error: DomainError,
    ) : ProfileEffect

    /** Put [text] on the clipboard; before Android 13 a snackbar says [what] was copied. */
    data class CopyText(
        val text: String,
        val what: CopiedText,
    ) : ProfileEffect {
        override fun toString() = "CopyText(what=$what, …)"
    }

    /** Open the dialer with [phoneNumber] (`ACTION_DIAL`, which needs no permission). */
    data class Dial(
        val phoneNumber: String,
    ) : ProfileEffect {
        override fun toString() = "Dial(…)"
    }

    /** Share [text] through the system's share sheet. */
    data class Share(
        val text: String,
    ) : ProfileEffect {
        override fun toString() = "Share(…)"
    }

    /** The user named [name] was blocked, or unblocked ([blocked] false): a snackbar says so. */
    data class BlockChanged(
        val name: UiText,
        val blocked: Boolean,
    ) : ProfileEffect

    /** Telegram has the report: a snackbar thanks the user. */
    data object Reported : ProfileEffect

    /** Telegram wants messages to go with the report: a snackbar says how to report them. */
    data object ReportNeedsMessages : ProfileEffect
}

/** What [ProfileEffect.CopyText] copied, for its snackbar. */
enum class CopiedText {
    Username,
    Phone,
    Bio,
}
