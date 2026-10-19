package dev.kyralabs.pingogram.core.domain.model

import kotlinx.collections.immutable.ImmutableList

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
)

/**
 * The reactions the current user can add to a message (TDLib's `getMessageAvailableReactions`): only those TDLib
 * offers, without the ones that need a Telegram Premium subscription the user does not have, and without the paid star
 * reaction, which is added another way.
 *
 * @property top the reactions to offer first, in TDLib's order (the most used ones); the full list comes with the
 *   reactions picker, later.
 */
data class AvailableReactions(
    val top: ImmutableList<ReactionType>,
)
