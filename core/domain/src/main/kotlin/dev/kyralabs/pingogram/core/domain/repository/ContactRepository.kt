package dev.kyralabs.pingogram.core.domain.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.User
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.Flow

/**
 * The current user's Telegram contacts (`REQUIREMENTS.md` FR-P1): the list, kept current by TDLib's updates, search,
 * adding a contact by phone number or username, editing and deleting one, and the link to invite someone to Telegram.
 * Every change is one the user asked for (Telegram's API terms: nothing automated). Contacts on the phone are not
 * read here.
 *
 * Failures are `DomainError.Network` (no answer in time), `DomainError.FloodWait` and others as TDLib reports them.
 */
interface ContactRepository {
    /**
     * The contacts as users, sorted by name in the user's language, once TDLib listed them, then the list again after
     * every change: a contact added, edited or deleted, here or on another device, and every change of a contact's
     * name, photo or status. Cold: each collection asks TDLib for the list; a failure to list them comes instead, and
     * ends the flow.
     */
    fun contacts(): Flow<Outcome<ImmutableList<User>>>

    /**
     * The ids of the contacts whose name or username matches [query] by TDLib's own matching, which also finds names
     * in other scripts (transliteration); none for a blank query, without asking TDLib.
     */
    suspend fun search(query: String): Outcome<ImmutableList<Long>>

    /**
     * Adds the owner of [phoneNumber] (international, with or without `+`) as a contact named [firstName] and
     * [lastName]: [AddContactResult.Added] with their user id, or [AddContactResult.NotOnTelegram].
     */
    suspend fun addByPhoneNumber(
        phoneNumber: String,
        firstName: String,
        lastName: String,
    ): Outcome<AddContactResult>

    /**
     * Adds the user with [username] (without `@`) as a contact named [firstName] and [lastName], or by their own names
     * where [firstName] is blank: their user id. `DomainError.NotFound` if no user has the username (a channel's or a
     * bot's included, as they cannot be contacts).
     */
    suspend fun addByUsername(
        username: String,
        firstName: String,
        lastName: String,
    ): Outcome<Long>

    /** User [userId] with what editing them as a contact needs. */
    suspend fun editableContact(userId: Long): Outcome<EditableContact>

    /**
     * Saves user [userId] as a contact named [firstName] and [lastName], adding them if they are none yet;
     * [sharePhoneNumber] lets them see the current user's phone number ([EditableContact.canSharePhoneNumber]).
     */
    suspend fun save(
        userId: Long,
        firstName: String,
        lastName: String,
        sharePhoneNumber: Boolean,
    ): Outcome<Unit>

    /** Takes user [userId] out of the contacts. */
    suspend fun delete(userId: Long): Outcome<Unit>

    /** The id of the private chat with user [userId], created on the device if needed, to open it. */
    suspend fun privateChatId(userId: Long): Outcome<Long>

    /**
     * The link to download Telegram that an invitation carries: Telegram's own for the account, or its general
     * download page when Telegram does not answer, so inviting works offline too.
     */
    suspend fun inviteLink(): String
}
