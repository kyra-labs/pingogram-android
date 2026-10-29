package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import dev.kyralabs.pingogram.core.domain.model.ConferenceInviteResult
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoin
import dev.kyralabs.pingogram.core.domain.model.ConferenceJoinParameters
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.ConferenceVerification
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Conference calls: the verification and members from updates, joining, inviting and the frames' encryption. */
class TdlibConferenceCallRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibConferenceCallRepository(client, backgroundScope)

    private val parameters =
        ConferenceJoinParameters(audioSourceId = 7, payload = "{}", isMuted = false, isVideo = true)

    @Test
    fun `the verification and the members follow their updates until the call ends`() =
        runTest {
            val repository = repository()
            assertNull(repository.verification(5).first())

            client.emit(TdApi.UpdateGroupCallVerificationState(5, 2, arrayOf("🐶", "🍎", "🚀", "🎸")))
            client.emit(TdApi.UpdateGroupCallParticipants(5, longArrayOf(42, 43)))
            assertEquals(
                ConferenceVerification(2, persistentListOf("🐶", "🍎", "🚀", "🎸")),
                repository.verification(5).first(),
            )
            assertEquals(persistentListOf(42L, 43L), repository.members(5).first())

            client.emit(TdApi.UpdateGroupCall(TdApi.GroupCall().also { it.id = 5 }))
            assertNull(repository.verification(5).first())
            assertEquals(persistentListOf<Long>(), repository.members(5).first())
        }

    @Test
    fun `everything is dropped when TDLib closes`() =
        runTest {
            val repository = repository()
            client.emit(TdApi.UpdateGroupCallParticipants(5, longArrayOf(42)))

            client.emit(TdApiFixtures.authorizationStateUpdate(TdApi.AuthorizationStateClosed()))

            assertEquals(persistentListOf<Long>(), repository.members(5).first())
        }

    @Test
    fun `creating and joining send the engine's parameters and answer the connection's`() =
        runTest {
            val repository = repository()
            client.on(TdApi.CreateGroupCall::class, TdResult.Success(TdApi.GroupCallInfo(5, "{\"answer\":1}")))
            client.on(TdApi.JoinGroupCall::class, TdResult.Success(TdApi.GroupCallInfo(6, "{\"answer\":2}")))

            assertEquals(Outcome.Success(ConferenceJoin(5, "{\"answer\":1}")), repository.create(parameters))
            val created = client.sentOf<TdApi.CreateGroupCall>().single().joinParameters
            assertEquals(7, created.audioSourceId)
            assertTrue(created.isMyVideoEnabled)

            repository.join(ConferenceTarget.Link("https://t.me/call/abc"), parameters)
            repository.join(ConferenceTarget.Invitation(chatId = 9, messageId = 10), parameters)
            val (byLink, byMessage) = client.sentOf<TdApi.JoinGroupCall>().map { it.inputGroupCall }
            assertEquals("https://t.me/call/abc", (byLink as TdApi.InputGroupCallLink).link)
            assertEquals(10, (byMessage as TdApi.InputGroupCallMessage).messageId)
        }

    @Test
    fun `an invitation reports what Telegram did`() =
        runTest {
            val repository = repository()
            client.on(
                TdApi.InviteGroupCallParticipant::class,
                TdResult.Success(TdApi.InviteGroupCallParticipantResultSuccess(9, 10)),
            )
            assertEquals(
                Outcome.Success(ConferenceInviteResult.Invited(9, 10)),
                repository.invite(5, 42, isVideo = false),
            )

            client.on(
                TdApi.InviteGroupCallParticipant::class,
                TdResult.Success(TdApi.InviteGroupCallParticipantResultUserPrivacyRestricted()),
            )
            assertEquals(
                Outcome.Success(ConferenceInviteResult.PrivacyRestricted),
                repository.invite(5, 42, isVideo = false),
            )
        }

    @Test
    fun `frames go through TDLib and come back null when it fails or is late`() =
        runTest {
            val repository = repository()
            client.on(TdApi.EncryptGroupCallData::class) { TdResult.Success(TdApi.Data(byteArrayOf(9) + it.data)) }
            client.on(TdApi.DecryptGroupCallData::class, TdResult.Failure(TdError(400, "DECRYPT_FAILED")))

            assertArrayEquals(
                byteArrayOf(9, 1, 2),
                repository.encrypt(5, ConferenceChannel.ScreenSharing, byteArrayOf(1, 2), 1),
            )
            val sent = client.sentOf<TdApi.EncryptGroupCallData>().single()
            assertInstanceOf(TdApi.GroupCallDataChannelScreenSharing::class.java, sent.dataChannel)
            assertEquals(1, sent.unencryptedPrefixSize)
            assertNull(repository.decrypt(5, 42, byteArrayOf(1)))
            assertEquals(
                42,
                (client.sentOf<TdApi.DecryptGroupCallData>().single().participantId as TdApi.MessageSenderUser).userId,
            )

            client.on(TdApi.EncryptGroupCallData::class) { awaitCancellation() }
            assertNull(repository.encrypt(5, ConferenceChannel.Main, byteArrayOf(1), 0))
        }
}
