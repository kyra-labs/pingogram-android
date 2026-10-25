package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.common.result.getOrNull
import dev.kyralabs.pingogram.core.common.result.map
import dev.kyralabs.pingogram.core.data.di.AccountScoped
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.EditableContact
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.domain.model.UserType
import dev.kyralabs.pingogram.core.domain.repository.ChatRepository
import dev.kyralabs.pingogram.core.domain.repository.ContactRepository
import dev.kyralabs.pingogram.core.domain.repository.UserRepository
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.text.Collator
import javax.inject.Inject

/**
 * [ContactRepository] on TDLib's contacts. It caches nothing of its own, so it is no `TdlibUpdateCache`: TDLib has no
 * update for the contact list, but announces every user with `updateUser`, `is_contact` included, before any of its
 * ids reaches the app, and again whenever a contact is added, renamed or deleted, on any device. [UserRepository]
 * keeps those users, so the contacts are its users that are contacts.
 *
 * - **The list**: `getContacts` makes TDLib load and announce every contact; then the contacts follow
 *   [UserRepository.users], once those updates reached it, sorted by name with a [Collator] of the user's language.
 *   Deleted accounts are left out.
 * - **Search**: `searchContacts`, which TDLib answers from the device; a query it cannot search finds nothing.
 * - **Adding by phone number**: `importContacts` with one `importedContact`; a user id of 0 means the number is not
 *   on Telegram.
 * - **Adding by username**: `searchPublicChat`, then `addContact` for the user of its private chat, without sharing
 *   the phone number.
 * - **Editing**: `addContact`, which adds or renames, keeping the note the user wrote about the contact elsewhere
 *   (`userFullInfo.note`); whether to offer sharing the phone number comes from `userFullInfo`.
 * - **Deleting**: `removeContacts`. **Opening**: `createPrivateChat` through [ChatRepository.privateChat].
 * - **Invitations**: `getApplicationDownloadLink`, else Telegram's download page.
 */
@Suppress("TooManyFunctions") // One override per ContactRepository call, and the two lookups they share.
@AccountScoped
class TdlibContactRepository
    @Inject
    constructor(
        private val client: TdlibClient,
        private val userRepository: UserRepository,
        private val chatRepository: ChatRepository,
    ) : ContactRepository {
        override fun contacts(): Flow<Outcome<ImmutableList<User>>> =
            flow {
                val listed =
                    when (val contacts = client.request(TdApi.GetContacts())) {
                        is Outcome.Success -> {
                            contacts.value.userIds.toSet()
                        }

                        is Outcome.Failure -> {
                            emit(contacts)
                            return@flow
                        }
                    }
                // TDLib announced every contact before it answered; the user cache applies those updates on its own
                // coroutine, so wait for them, rather than show a list missing some for a moment.
                withTimeoutOrNull(REQUEST_TIMEOUT) {
                    userRepository.users.first { users ->
                        users.keys.containsAll(listed)
                    }
                }
                val sorted =
                    userRepository.users
                        .map { users -> sortedContacts(users.values) }
                        .distinctUntilChanged()
                        .map { Outcome.Success(it) }
                emitAll(sorted)
            }

        override suspend fun search(query: String): Outcome<ImmutableList<Long>> {
            val text = query.trim()
            if (text.isEmpty()) return Outcome.Success(persistentListOf())
            return when (val found = client.request(TdApi.SearchContacts(text, SEARCH_LIMIT))) {
                is Outcome.Success -> {
                    Outcome.Success(
                        found.value.userIds
                            .distinct()
                            .toImmutableList(),
                    )
                }

                is Outcome.Failure -> {
                    if (found.error.isBadRequest()) Outcome.Success(persistentListOf()) else found
                }
            }
        }

        override suspend fun addByPhoneNumber(
            phoneNumber: String,
            firstName: String,
            lastName: String,
        ): Outcome<AddContactResult> {
            val contact = TdApi.ImportedContact(phoneNumber.trim(), firstName.trim(), lastName.trim(), emptyNote())
            return client.request(TdApi.ImportContacts(arrayOf(contact))).map { imported ->
                val userId = imported.userIds.firstOrNull() ?: 0L
                if (userId == 0L) AddContactResult.NotOnTelegram else AddContactResult.Added(userId)
            }
        }

        override suspend fun addByUsername(
            username: String,
            firstName: String,
            lastName: String,
        ): Outcome<Long> =
            when (val found = publicUser(username.trim().removePrefix("@"))) {
                is Outcome.Success -> {
                    val user = found.value
                    val first = firstName.trim().ifEmpty { user.firstName }
                    val last = if (firstName.isBlank()) user.lastName else lastName.trim()
                    val contact = TdApi.ImportedContact(user.phoneNumber, first, last, note(user.id))
                    client.request(TdApi.AddContact(user.id, contact, false)).map { user.id }
                }

                is Outcome.Failure -> {
                    found
                }
            }

        override suspend fun editableContact(userId: Long): Outcome<EditableContact> {
            val user =
                when (val found = userRepository.getUser(userId)) {
                    is Outcome.Success -> found.value
                    is Outcome.Failure -> return found
                }
            // Without the full info the switch is left out; editing the names still works.
            val fullInfo = client.request(TdApi.GetUserFullInfo(userId)).getOrNull()
            return Outcome.Success(EditableContact(user, fullInfo?.needPhoneNumberPrivacyException == true))
        }

        override suspend fun save(
            userId: Long,
            firstName: String,
            lastName: String,
            sharePhoneNumber: Boolean,
        ): Outcome<Unit> {
            val phoneNumber =
                userRepository
                    .getUser(userId)
                    .getOrNull()
                    ?.phoneNumber
                    .orEmpty()
            val contact = TdApi.ImportedContact(phoneNumber, firstName.trim(), lastName.trim(), note(userId))
            return client.request(TdApi.AddContact(userId, contact, sharePhoneNumber)).map {}
        }

        override suspend fun delete(userId: Long): Outcome<Unit> =
            client.request(TdApi.RemoveContacts(longArrayOf(userId))).map {}

        override suspend fun privateChatId(userId: Long): Outcome<Long> =
            chatRepository.privateChat(userId).map { it.id }

        override suspend fun inviteLink(): String =
            client
                .request(TdApi.GetApplicationDownloadLink())
                .getOrNull()
                ?.url
                ?.ifBlank { null } ?: DOWNLOAD_PAGE

        /**
         * The regular user whose username is [name] (`searchPublicChat`); `DomainError.NotFound` for none, and for a
         * channel's, a group's or a bot's username.
         */
        private suspend fun publicUser(name: String): Outcome<User> {
            if (name.isEmpty()) return Outcome.Failure(DomainError.NotFound)
            val chat = client.request(TdApi.SearchPublicChat(name))
            val userId = (chat.getOrNull()?.type as? TdApi.ChatTypePrivate)?.userId
            val user = userId?.let { userRepository.getUser(it) }
            return when {
                chat is Outcome.Failure -> {
                    chat
                }

                user == null -> {
                    Outcome.Failure(DomainError.NotFound)
                }

                user.getOrNull()?.type.let { it != null && it != UserType.Regular } -> {
                    Outcome.Failure(DomainError.NotFound)
                }

                else -> {
                    user
                }
            }
        }

        /**
         * The note the user wrote about user [userId], which `addContact` would otherwise replace; an empty one if
         * there is none or TDLib cannot tell.
         */
        private suspend fun note(userId: Long): TdApi.FormattedText =
            client.request(TdApi.GetUserFullInfo(userId)).getOrNull()?.note ?: emptyNote()

        private companion object {
            /** As many contacts as search shows. */
            const val SEARCH_LIMIT = 50

            /** Telegram's download page, for invitations while Telegram does not answer. */
            const val DOWNLOAD_PAGE = "https://telegram.org/dl"
        }
    }

/** The contacts among [users], without deleted accounts, sorted by name in the user's language. */
private fun sortedContacts(users: Collection<User>): ImmutableList<User> {
    val collator = Collator.getInstance()
    return users
        .filter { it.isContact && it.type != UserType.Deleted && it.type != UserType.Unknown }
        .sortedWith { a, b -> collator.compare(a.sortName(), b.sortName()).takeIf { it != 0 } ?: a.id.compareTo(b.id) }
        .toImmutableList()
}

/** The name a contact is sorted by: the first name, then the last name, as Android's contacts sort them by default. */
private fun User.sortName(): String = listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")

private fun emptyNote() = TdApi.FormattedText("", emptyArray())

/** A 400 of TDLib's own, for what it cannot search, e.g. a query of only punctuation. */
private fun DomainError.isBadRequest(): Boolean = (this as? DomainError.Unknown)?.code == BAD_REQUEST

private const val BAD_REQUEST = 400
