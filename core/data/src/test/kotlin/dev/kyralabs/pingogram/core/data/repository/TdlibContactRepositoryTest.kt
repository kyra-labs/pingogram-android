package dev.kyralabs.pingogram.core.data.repository

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AddContactResult
import dev.kyralabs.pingogram.core.domain.model.User
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.chat
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.formattedText
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.updateUser
import dev.kyralabs.pingogram.core.testing.TdApiFixtures.user
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibContactRepository] on a [FakeTdlibClient], through the real user and chat repositories: the contacts as TDLib
 * announces them and changes them, sorted by name; search; adding by phone number and by username; editing (keeping
 * the contact's note), deleting, opening the chat, the invitation link, and errors.
 */
class TdlibContactRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository(): TdlibContactRepository {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        return TdlibContactRepository(client, users, chats)
    }

    private fun failure(
        code: Int,
        message: String,
    ) = TdResult.Failure(TdError(code, message))

    /** TDLib answers getContacts with [contacts], announcing each of them first, as it does. */
    private fun answerContacts(vararg contacts: TdApi.User) {
        client.on(TdApi.GetContacts::class) {
            contacts.forEach { client.emit(updateUser(it)) }
            TdResult.Success(TdApi.Users(contacts.size, contacts.map { it.id }.toLongArray()))
        }
    }

    private fun Outcome<ImmutableList<User>>.ids() = (this as Outcome.Success).value.map { it.id }

    // The list

    @Test
    fun `the contacts are the users TDLib announced as contacts, sorted by name`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 5, firstName = "Zed", isContact = false)))
            answerContacts(
                user(id = 7, firstName = "Grace", lastName = "Hopper", isContact = true),
                user(id = 8, firstName = "ada", isContact = true),
                user(id = 9, firstName = "Bea", isContact = true),
            )

            repository.contacts().test {
                assertEquals(listOf(8L, 9L, 7L), awaitItem().ids())
            }
        }

    @Test
    fun `a contact added, renamed or deleted anywhere changes the list`() =
        runTest {
            val repository = repository()
            answerContacts(user(id = 7, firstName = "Grace", isContact = true))

            repository.contacts().test {
                assertEquals(listOf(7L), awaitItem().ids())

                client.emit(updateUser(user(id = 8, firstName = "Ada", isContact = true)))
                assertEquals(listOf(8L, 7L), awaitItem().ids())

                client.emit(updateUser(user(id = 8, firstName = "Zoe", isContact = true)))
                assertEquals(listOf(7L, 8L), awaitItem().ids())

                client.emit(updateUser(user(id = 7, firstName = "Grace", isContact = false)))
                assertEquals(listOf(8L), awaitItem().ids())
            }
        }

    @Test
    fun `a change of a user who is no contact does not emit the list again`() =
        runTest {
            val repository = repository()
            answerContacts(user(id = 7, firstName = "Grace", isContact = true))

            repository.contacts().test {
                awaitItem()
                client.emit(updateUser(user(id = 9, firstName = "Someone", isContact = false)))
                runCurrent()
                expectNoEvents()
            }
        }

    @Test
    fun `deleted accounts are left out`() =
        runTest {
            val repository = repository()
            answerContacts(
                user(id = 7, firstName = "Grace", isContact = true),
                user(id = 8, firstName = "", isContact = true, type = TdApi.UserTypeDeleted()),
            )

            repository.contacts().test { assertEquals(listOf(7L), awaitItem().ids()) }
        }

    @Test
    fun `failing to list the contacts reports its error and ends`() =
        runTest {
            client.on(TdApi.GetContacts::class, failure(429, "Too Many Requests: retry after 3"))

            repository().contacts().test {
                assertEquals(Outcome.Failure(DomainError.FloodWait(3)), awaitItem())
                awaitComplete()
            }
        }

    // Search

    @Test
    fun `search finds the ids TDLib matches, trimmed, without asking for a blank query`() =
        runTest {
            client.on(TdApi.SearchContacts::class) { TdResult.Success(TdApi.Users(3, longArrayOf(7, 8, 7))) }
            val repository = repository()

            assertEquals(Outcome.Success(listOf<Long>()), repository.search("  "))
            assertTrue(client.sent.isEmpty())
            assertEquals(Outcome.Success(listOf(7L, 8L)), repository.search(" gr "))
            assertEquals("gr", client.sentOf<TdApi.SearchContacts>().single().query)
        }

    @Test
    fun `a query TDLib cannot search finds nothing, other failures are reported`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SearchContacts::class, failure(400, "Invalid query"))
            assertEquals(Outcome.Success(listOf<Long>()), repository.search("%"))

            client.on(TdApi.SearchContacts::class, failure(429, "Too Many Requests: retry after 5"))
            assertEquals(Outcome.Failure(DomainError.FloodWait(5)), repository.search("gr"))
        }

    // Adding

    @Test
    fun `adding by phone number imports one contact with its names`() =
        runTest {
            client.on(
                TdApi.ImportContacts::class,
            ) { TdResult.Success(TdApi.ImportedContacts(longArrayOf(7), intArrayOf(0))) }

            val result = repository().addByPhoneNumber(" +44 7911 123456 ", " Grace ", "Hopper ")

            assertEquals(Outcome.Success(AddContactResult.Added(7)), result)
            val contact =
                client
                    .sentOf<TdApi.ImportContacts>()
                    .single()
                    .contacts
                    .single()
            assertEquals("+44 7911 123456", contact.phoneNumber)
            assertEquals("Grace", contact.firstName)
            assertEquals("Hopper", contact.lastName)
            assertEquals("", contact.note.text)
        }

    @Test
    fun `a phone number not on Telegram imports user 0`() =
        runTest {
            client.on(
                TdApi.ImportContacts::class,
            ) { TdResult.Success(TdApi.ImportedContacts(longArrayOf(0), intArrayOf(2))) }

            assertEquals(
                Outcome.Success(AddContactResult.NotOnTelegram),
                repository().addByPhoneNumber("+447911123456", "Grace", ""),
            )
        }

    @Test
    fun `a rejected phone number is reported`() =
        runTest {
            client.on(TdApi.ImportContacts::class, failure(400, "PHONE_NUMBER_INVALID"))

            assertEquals(
                Outcome.Failure(DomainError.PhoneNumberInvalid),
                repository().addByPhoneNumber("+1", "Grace", ""),
            )
        }

    @Test
    fun `adding by username adds the user of the public chat, by their own names without names given`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 7, firstName = "Grace", lastName = "Hopper", phoneNumber = "")))
            client.on(TdApi.SearchPublicChat::class) { TdResult.Success(chat(id = 7, type = TdApi.ChatTypePrivate(7))) }
            client.on(TdApi.GetUserFullInfo::class, failure(400, "User not found"))
            client.on(TdApi.AddContact::class) { TdResult.Success(TdApi.Ok()) }

            assertEquals(Outcome.Success(7L), repository.addByUsername(" @grace ", "", ""))

            assertEquals("grace", client.sentOf<TdApi.SearchPublicChat>().single().username)
            val add = client.sentOf<TdApi.AddContact>().single()
            assertEquals(7L, add.userId)
            assertEquals("Grace", add.contact.firstName)
            assertEquals("Hopper", add.contact.lastName)
            assertFalse(add.sharePhoneNumber)
        }

    @Test
    fun `adding by username takes the names given`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 7, firstName = "Grace", lastName = "Hopper")))
            client.on(TdApi.SearchPublicChat::class) { TdResult.Success(chat(id = 7, type = TdApi.ChatTypePrivate(7))) }
            client.on(TdApi.GetUserFullInfo::class, failure(400, "User not found"))
            client.on(TdApi.AddContact::class) { TdResult.Success(TdApi.Ok()) }

            repository.addByUsername("grace", "Admiral", "")

            val contact = client.sentOf<TdApi.AddContact>().single().contact
            assertEquals("Admiral", contact.firstName)
            assertEquals("", contact.lastName)
        }

    @Test
    fun `a username of a channel, a bot or no one is not found`() =
        runTest {
            val repository = repository()
            client.on(TdApi.SearchPublicChat::class) {
                TdResult.Success(chat(id = -100, type = TdApi.ChatTypeSupergroup(100, true)))
            }
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.addByUsername("news", "", ""))

            client.emit(updateUser(user(id = 9, firstName = "Bot", type = TdApi.UserTypeBot())))
            client.on(TdApi.SearchPublicChat::class) { TdResult.Success(chat(id = 9, type = TdApi.ChatTypePrivate(9))) }
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.addByUsername("somebot", "", ""))

            client.on(TdApi.SearchPublicChat::class, failure(400, "USERNAME_NOT_OCCUPIED"))
            assertEquals(Outcome.Failure(DomainError.NotFound), repository.addByUsername("nobody", "", ""))
            assertTrue(client.sentOf<TdApi.AddContact>().isEmpty())
        }

    // Editing and deleting

    @Test
    fun `an editable contact says whether saving can share the phone number`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 7, firstName = "Grace", isContact = true)))
            client.on(TdApi.GetUserFullInfo::class) {
                TdResult.Success(TdApi.UserFullInfo().apply { needPhoneNumberPrivacyException = true })
            }

            val contact = (repository.editableContact(7) as Outcome.Success).value

            assertEquals(7L, contact.user.id)
            assertTrue(contact.canSharePhoneNumber)
        }

    @Test
    fun `without its full info a user can still be edited, without sharing the number`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 7, firstName = "Grace")))
            client.on(TdApi.GetUserFullInfo::class, failure(500, "Network is unreachable"))

            assertFalse((repository.editableContact(7) as Outcome.Success).value.canSharePhoneNumber)
        }

    @Test
    fun `saving adds or renames the contact, keeping its note and its phone number`() =
        runTest {
            val repository = repository()
            client.emit(updateUser(user(id = 7, firstName = "Grace", phoneNumber = "447911123456", isContact = true)))
            client.on(TdApi.GetUserFullInfo::class) {
                TdResult.Success(TdApi.UserFullInfo().apply { note = formattedText("Met at the conference") })
            }
            client.on(TdApi.AddContact::class) { TdResult.Success(TdApi.Ok()) }

            assertEquals(Outcome.Success(Unit), repository.save(7, " Admiral ", " Hopper", sharePhoneNumber = true))

            val add = client.sentOf<TdApi.AddContact>().single()
            assertEquals(7L, add.userId)
            assertEquals("447911123456", add.contact.phoneNumber)
            assertEquals("Admiral", add.contact.firstName)
            assertEquals("Hopper", add.contact.lastName)
            assertEquals("Met at the conference", add.contact.note.text)
            assertTrue(add.sharePhoneNumber)
        }

    @Test
    fun `deleting removes the one contact`() =
        runTest {
            client.on(TdApi.RemoveContacts::class) { TdResult.Success(TdApi.Ok()) }

            assertEquals(Outcome.Success(Unit), repository().delete(7))

            assertEquals(
                listOf(7L),
                client
                    .sentOf<TdApi.RemoveContacts>()
                    .single()
                    .userIds
                    .toList(),
            )
        }

    @Test
    fun `a failed delete is reported`() =
        runTest {
            client.on(TdApi.RemoveContacts::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }

            assertEquals(Outcome.Failure(DomainError.Network), repository().delete(7))
        }

    // Opening and inviting

    @Test
    fun `a contact's chat is its private chat`() =
        runTest {
            client.on(TdApi.CreatePrivateChat::class) { request ->
                TdResult.Success(chat(id = request.userId * 10, type = TdApi.ChatTypePrivate(request.userId)))
            }

            assertEquals(Outcome.Success(70L), repository().privateChatId(7))
            assertFalse(client.sentOf<TdApi.CreatePrivateChat>().single().force)
        }

    @Test
    fun `the invitation carries Telegram's link, or its download page without an answer`() =
        runTest {
            val repository = repository()
            client.on(TdApi.GetApplicationDownloadLink::class) { TdResult.Success(TdApi.HttpUrl("https://t.me/dl?x")) }
            assertEquals("https://t.me/dl?x", repository.inviteLink())

            client.on(TdApi.GetApplicationDownloadLink::class, failure(500, "Network is unreachable"))
            assertEquals("https://telegram.org/dl", repository.inviteLink())
        }
}
