package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * What the current user can do with a message right now (TDLib's `messageProperties`), as far as the conversation's
 * message actions offer it (`REQUIREMENTS.md` FR-C6). TDLib works it out from the chat, the user's rights and the
 * message's age, so it changes over time: ask again before acting.
 */
data class MessageProperties(
    /** It can be replied to in its chat. */
    val canBeReplied: Boolean,
    /** Its text or caption can be edited. */
    val canBeEdited: Boolean,
    /** It can be pinned and unpinned. */
    val canBePinned: Boolean,
    /** It can be deleted for the current user only; the others keep it. */
    val canBeDeletedOnlyForSelf: Boolean,
    /** It can be deleted for everyone in the chat. */
    val canBeDeletedForAllUsers: Boolean,
    /**
     * Its content may be saved or copied: false where the chat or the sender protects its content (Telegram's
     * "restrict saving content"), which copying its text must respect too.
     */
    val canBeSaved: Boolean,
    /**
     * It can be forwarded to another chat (`REQUIREMENTS.md` FR-C6): false for protected content, service messages and
     * a message still being sent, among others.
     */
    val canBeForwarded: Boolean = false,
    /** It can be forwarded as a copy, without its sender's name (td_api.tl `forwardMessages.send_copy`). */
    val canBeCopied: Boolean = false,
    /** It can be forwarded into a secret chat, which takes only copies. */
    val canBeCopiedToSecretChat: Boolean = false,
    /** It can be reported to Telegram's moderators (td_api.tl `messageProperties.can_report_chat`, `reportChat`). */
    val canBeReported: Boolean = false,
)

/**
 * The reactions the current user can add to a message (TDLib's `getMessageAvailableReactions`): only those TDLib
 * offers, without the ones that need a Telegram Premium subscription the user does not have, and without the paid star
 * reaction, which is added another way. Each reaction is in one list only, the first TDLib gave it in.
 *
 * TDLib also says whether Premium users may react with any custom emoji (`allowCustomEmoji`); that needs a custom emoji
 * picker, which Pingogram does not have, so only the custom emoji listed here are offered.
 *
 * @property top the reactions to offer first, in TDLib's order (the most used ones in the chat): the quick reactions.
 * @property recent the reactions the user used recently, newest first.
 * @property popular the reactions most used on Telegram.
 */
data class AvailableReactions(
    val top: ImmutableList<ReactionType>,
    val recent: ImmutableList<ReactionType> = persistentListOf(),
    val popular: ImmutableList<ReactionType> = persistentListOf(),
)
