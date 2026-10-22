package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.AdministratorRights
import dev.kyralabs.pingogram.core.domain.model.MemberPermissions
import dev.kyralabs.pingogram.core.domain.model.MemberRole
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.ProfileFixtures
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [TdlibGroupAdministrationRepository] on a [FakeTdlibClient]: what each change sends, the photo's upload, and what is
 * refused first.
 */
class TdlibGroupAdministrationRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibGroupAdministrationRepository(client)

    init {
        client.on(TdApi.SetChatTitle::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.SetChatDescription::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.SetChatPermissions::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.SetChatSlowModeDelay::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.SetChatMemberStatus::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.DeleteChatHistory::class, TdResult.Success(TdApi.Ok()))
    }

    @Test
    fun `the title is trimmed, and a blank one is refused before anything is sent`() =
        runTest {
            assertEquals(Outcome.Success(Unit), repository.setTitle(CHAT, "  Rust Berlin "))
            assertEquals(Outcome.Failure(DomainError.TitleInvalid), repository.setTitle(CHAT, "   "))

            assertEquals("Rust Berlin", client.sentOf<TdApi.SetChatTitle>().single().title)
        }

    @Test
    fun `the description and the permissions are sent whole`() =
        runTest {
            repository.setDescription(CHAT, "Meetups ")
            repository.setPermissions(CHAT, MemberPermissions(canSendBasicMessages = true, canSendPolls = true))

            assertEquals("Meetups", client.sentOf<TdApi.SetChatDescription>().single().description)
            val permissions = client.sentOf<TdApi.SetChatPermissions>().single().permissions
            assertTrue(permissions.canSendBasicMessages && permissions.canSendPolls)
            assertFalse(permissions.canSendPhotos)
        }

    @Test
    fun `slow mode takes only the delays Telegram accepts`() =
        runTest {
            assertEquals(Outcome.Success(Unit), repository.setSlowModeDelay(CHAT, 60))
            assertEquals(Outcome.Failure(DomainError.InputRejected), repository.setSlowModeDelay(CHAT, 61))

            assertEquals(60, client.sentOf<TdApi.SetChatSlowModeDelay>().single().slowModeDelay)
        }

    @Test
    fun `roles become the member statuses TDLib takes`() =
        runTest {
            val sender = MessageSender.User(7)
            val rights = AdministratorRights(canDeleteMessages = true, canManageTags = true)
            repository.setMemberRole(CHAT, sender, MemberRole.Administrator(canBeEdited = true, rights = rights))
            repository.setMemberRole(CHAT, sender, MemberRole.Banned(untilDate = 0))
            repository.setMemberRole(CHAT, sender, MemberRole.Left)
            val restricted = MemberRole.Restricted(true, 1_800_000_000, MemberPermissions(canSendBasicMessages = true))
            repository.setMemberRole(CHAT, sender, restricted)

            val sent = client.sentOf<TdApi.SetChatMemberStatus>()
            assertEquals(7L, (sent[0].memberId as TdApi.MessageSenderUser).userId)
            val admin = sent[0].status as TdApi.ChatMemberStatusAdministrator
            assertTrue(admin.rights.canDeleteMessages && admin.rights.canManageTags)
            assertFalse(admin.rights.canPromoteMembers)
            assertEquals(0, (sent[1].status as TdApi.ChatMemberStatusBanned).bannedUntilDate)
            assertInstanceOf(TdApi.ChatMemberStatusLeft::class.java, sent[2].status)
            val limited = sent[3].status as TdApi.ChatMemberStatusRestricted
            assertEquals(1_800_000_000, limited.restrictedUntilDate)
            assertTrue(limited.permissions.canSendBasicMessages)
        }

    @Test
    fun `a change the rights do not allow fails as such`() =
        runTest {
            client.on(TdApi.SetChatMemberStatus::class, TdResult.Failure(TdError(400, "RIGHT_FORBIDDEN")))

            val result = repository.setMemberRole(CHAT, MessageSender.User(7), MemberRole.Member)

            assertEquals(Outcome.Failure(DomainError.AdminRightsRequired), result)
        }

    @Test
    fun `clearing the history keeps the chat and revokes nothing`() =
        runTest {
            repository.clearHistory(CHAT)

            val sent = client.sentOf<TdApi.DeleteChatHistory>().single()
            assertFalse(sent.removeFromChatList)
            assertFalse(sent.revoke)
        }

    @Test
    fun `a photo is uploaded from its copy as the chat's, reporting its progress`() =
        runTest {
            val path = "/data/cache/uploads/1/photo.jpg"
            client.on(TdApi.SetChatPhoto::class) {
                // Another file's progress does not count.
                client.emit(TdApiFixtures.updateFile(ProfileFixtures.uploadingFile(localPath = "/elsewhere.jpg")))
                client.emit(
                    TdApiFixtures.updateFile(ProfileFixtures.uploadingFile(localPath = path, uploadedSize = 500)),
                )
                TdResult.Success(TdApi.Ok())
            }
            val progress = mutableListOf<Float>()

            assertEquals(Outcome.Success(Unit), repository.setPhoto(CHAT, path) { progress += it })

            assertEquals(listOf(0.5f), progress)
            val sent = client.sentOf<TdApi.SetChatPhoto>().single()
            assertEquals(CHAT, sent.chatId)
            val photo = sent.photo as TdApi.InputChatPhotoStatic
            assertEquals(path, (photo.photo as TdApi.InputFileLocal).path)
        }

    @Test
    fun `removing the photo sends none, and a refusal fails as such`() =
        runTest {
            client.on(TdApi.SetChatPhoto::class, TdResult.Failure(TdError(400, "CHAT_ADMIN_REQUIRED")))

            assertEquals(Outcome.Failure(DomainError.AdminRightsRequired), repository.removePhoto(CHAT))

            assertNull(client.sentOf<TdApi.SetChatPhoto>().single().photo)
        }

    private companion object {
        const val CHAT = -100L
    }
}
