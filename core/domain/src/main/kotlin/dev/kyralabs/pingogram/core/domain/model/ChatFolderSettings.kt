package dev.kyralabs.pingogram.core.domain.model

/**
 * What a chat folder holds (TDLib's `chatFolder`, `REQUIREMENTS.md` FR-L3): its [title] and icon, the chats and kinds of
 * chats it includes, and those it leaves out. A chat is in the folder if it is one of [includedChatIds] or of the
 * included kinds, unless it is one of [excludedChatIds] or of the excluded kinds; [pinnedChatIds], pinned at the top of
 * the folder, are always in it. A folder must include at least one chat or kind of chat.
 *
 * `toString()` leaves the title and the chats out.
 */
data class ChatFolderSettings(
    /** 1–12 characters; its only entities are custom emoji. */
    val title: FormattedText,
    /** TDLib's name of the icon chosen for the folder ([ChatFolder.iconName]); null to let Telegram choose one. */
    val iconName: String? = null,
    /** Telegram's color of the folder's tag, 0–6; null for none. */
    val colorId: Int? = null,
    /** Whether the folder has invite links, so it is shared and its chats must be groups or channels. */
    val isShareable: Boolean = false,
    val pinnedChatIds: List<Long> = emptyList(),
    val includedChatIds: List<Long> = emptyList(),
    val excludedChatIds: List<Long> = emptyList(),
    val includedTypes: Set<ChatFolderIncludedType> = emptySet(),
    val excludedTypes: Set<ChatFolderExcludedType> = emptySet(),
) {
    /** Whether the folder holds anything: TDLib refuses a folder that includes no chat and no kind of chat. */
    val includesAnything: Boolean
        get() = includedTypes.isNotEmpty() || includedChatIds.isNotEmpty() || pinnedChatIds.isNotEmpty()

    override fun toString() =
        "ChatFolderSettings(iconName=$iconName, colorId=$colorId, isShareable=$isShareable, " +
            "chats=${pinnedChatIds.size + includedChatIds.size}, excluded=${excludedChatIds.size}, " +
            "includedTypes=$includedTypes, excludedTypes=$excludedTypes)"

    companion object {
        /** The longest title Telegram takes, in characters. */
        const val MAX_TITLE_LENGTH = 12
    }
}

/** A kind of chats a folder can include as a whole (td_api.tl `chatFolder.include_*`). */
enum class ChatFolderIncludedType { Contacts, NonContacts, Groups, Channels, Bots }

/** A kind of chats a folder can leave out as a whole (td_api.tl `chatFolder.exclude_*`). */
enum class ChatFolderExcludedType { Muted, Read, Archived }

/** A folder Telegram suggests from the user's chats (td_api.tl `recommendedChatFolder`), with what it holds. */
data class RecommendedChatFolder(
    val settings: ChatFolderSettings,
    val description: String,
)

/** What a folder's invite link (`t.me/addlist/…`) offers (td_api.tl `chatFolderInviteLinkInfo`). */
data class ChatFolderInvite(
    /** The folder as its owner named it; its id is 0 unless the user already has it. */
    val folder: ChatFolder,
    /** The folder's chats the user is not in yet, which they can choose to join. */
    val missingChatIds: List<Long>,
    /** The folder's chats the user is in already. */
    val addedChatIds: List<Long>,
) {
    /** Whether the user already added this folder: the link then only offers its new chats. */
    val isAdded: Boolean get() = folder.id != 0
}

/** An invite link to one of the user's folders (td_api.tl `chatFolderInviteLink`). */
data class ChatFolderInviteLink(
    val link: String,
    /** The name the user gave the link; may be empty. */
    val name: String,
    /** The folder's chats the link shares. */
    val chatIds: List<Long>,
)
