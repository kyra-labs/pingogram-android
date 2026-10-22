package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.CreatedGroup
import dev.kyralabs.pingogram.core.domain.model.MessageAutoDelete
import dev.kyralabs.pingogram.core.domain.model.UsernameCheck
import kotlinx.collections.immutable.ImmutableList

/**
 * Creating groups and channels (`REQUIREMENTS.md` FR-P4): the chat itself with its photo, a channel's public username
 * or invite link, and the people the user picked to add. Every request is one the user asked for, one chat at a time,
 * adding only whom they picked (Telegram's API terms: nothing automated).
 *
 * Failures are `DomainError.Network` (no answer in time), `DomainError.FloodWait`, `DomainError.TooManyChats`,
 * `DomainError.AccountRestricted`, `DomainError.TitleInvalid` and others as TDLib reports them.
 */
interface ChatCreationRepository {
    /**
     * Copies the image at [uri] (the photo picker's content URI, as a string) into the app's cache as an upright JPEG
     * without metadata, for a new chat's photo: its path. `DomainError.PhotoInvalid` for what cannot be read as an
     * image. The copy stays until [discardPhoto] or a creation uses it.
     */
    suspend fun preparePhoto(uri: String): Outcome<String>

    /** Deletes [path], a copy [preparePhoto] made that no chat will use. */
    fun discardPhoto(path: String)

    /**
     * Creates a group named [title] with the users [userIds] (the user's picks, at most
     * `ChatCreationLimits.MEMBERS_TO_ADD_MAX`), whose messages are deleted after [autoDelete]: a basic group, or a
     * supergroup when no one or more people than a basic group holds are added
     * (`ChatCreationLimits.BASIC_GROUP_SIZE_MAX`).
     * With [photoPath] (from [preparePhoto]) the group gets that photo once created; uploading it carries on after
     * this returns, and the copy is deleted then. A failure keeps the copy, for another attempt or [discardPhoto].
     */
    suspend fun createGroup(
        title: String,
        userIds: List<Long>,
        autoDelete: MessageAutoDelete,
        photoPath: String?,
    ): Outcome<CreatedGroup>

    /**
     * Creates a channel named [title] with [description] (may be empty), private until it gets a username
     * ([setUsername]): its chat id. [photoPath] as for [createGroup].
     */
    suspend fun createChannel(
        title: String,
        description: String,
        photoPath: String?,
    ): Outcome<Long>

    /** Whether channel [chatId] could take [username] (without `@`), asking Telegram. */
    suspend fun checkUsername(
        chatId: Long,
        username: String,
    ): Outcome<UsernameCheck>

    /** Makes channel [chatId] public under [username] (without `@`). */
    suspend fun setUsername(
        chatId: Long,
        username: String,
    ): Outcome<Unit>

    /** The primary invite link of group or channel [chatId], created if it has none yet. */
    suspend fun inviteLink(chatId: Long): Outcome<String>

    /**
     * Adds the users [userIds] to channel or supergroup [chatId]: the ids of those Telegram did not add, usually for
     * their privacy settings.
     */
    suspend fun addMembers(
        chatId: Long,
        userIds: List<Long>,
    ): Outcome<ImmutableList<Long>>
}
