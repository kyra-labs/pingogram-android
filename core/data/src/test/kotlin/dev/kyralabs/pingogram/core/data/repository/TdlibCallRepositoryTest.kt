package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallProblem
import dev.kyralabs.pingogram.core.domain.model.CallProtocol
import dev.kyralabs.pingogram.core.domain.model.CallServer
import dev.kyralabs.pingogram.core.domain.model.CallServerType
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/** Calls: the cache of `updateCall`, the engine's signaling data, and starting, answering and ending a call. */
class TdlibCallRepositoryTest {
    private val client = FakeTdlibClient()

    private fun TestScope.repository() = TdlibCallRepository(client, backgroundScope)

    private val protocol = CallProtocol(true, true, 65, 92, persistentListOf("13.0.0", "12.0.0"))

    @Test
    fun `knows no call before TDLib announces one`() =
        runTest {
            assertNull(repository().call(3).first())
        }

    @Test
    fun `maps an incoming call that rings`() =
        runTest {
            val repository = repository()

            client.emit(updateCall(call(state = TdApi.CallStatePending(true, true))))

            assertEquals(
                Call(
                    id = 3,
                    userId = 42,
                    isOutgoing = false,
                    isVideo = false,
                    state = CallState.Pending(isCreated = true, isReceived = true),
                ),
                repository.call(3).first(),
            )
        }

    @Test
    fun `maps a ready call with what the engine needs`() =
        runTest {
            val repository = repository()
            val key = ByteArray(256) { it.toByte() }
            val reflector =
                TdApi.CallServer(
                    1,
                    "91.108.13.2",
                    "",
                    598,
                    TdApi.CallServerTypeTelegramReflector(byteArrayOf(9), false),
                )
            val turn = TdApi.CallServer(2, "1.2.3.4", "::1", 3478, TdApi.CallServerTypeWebrtc("u", "p", true, true))
            val ready =
                TdApi.CallStateReady(
                    protocol.toTd(),
                    arrayOf(reflector, turn),
                    "{}",
                    key,
                    arrayOf("😀", "🐶", "🍎", "🚗"),
                    true,
                    false,
                    "{\"a\":1}",
                )

            client.emit(updateCall(call(state = ready)))

            val connection = (repository.call(3).first()?.state as CallState.Ready).connection
            assertEquals(
                CallConnection(
                    protocol = protocol,
                    servers =
                        persistentListOf(
                            CallServer(
                                1,
                                "91.108.13.2",
                                "",
                                598,
                                CallServerType.TelegramReflector(byteArrayOf(9), false),
                            ),
                            CallServer(2, "1.2.3.4", "::1", 3478, CallServerType.Webrtc("u", "p", true, true)),
                        ),
                    encryptionKey = key,
                    emojis = persistentListOf("😀", "🐶", "🍎", "🚗"),
                    allowP2p = true,
                    customParameters = "{\"a\":1}",
                ),
                connection,
            )
            assertArrayEquals(key, connection.encryptionKey())
        }

    @Test
    fun `maps the ends of a call`() =
        runTest {
            val repository = repository()

            client.emit(
                updateCall(
                    call(
                        id = 1,
                        state = TdApi.CallStateDiscarded(TdApi.CallDiscardReasonDeclined(), true, false, false),
                    ),
                ),
            )
            client.emit(
                updateCall(call(id = 2, state = TdApi.CallStateError(TdApi.Error(400, "USER_PRIVACY_RESTRICTED")))),
            )
            client.emit(updateCall(call(id = 4, state = TdApi.CallStateHangingUp())))

            val calls = repository.calls.first()
            assertEquals(CallState.Discarded(CallDiscardReason.Declined, needsRating = true), calls.getValue(1).state)
            assertEquals(CallState.Failed(DomainError.CallPrivacyRestricted), calls.getValue(2).state)
            assertEquals(CallState.HangingUp, calls.getValue(4).state)
        }

    @Test
    fun `an unchanged call keeps the map`() =
        runTest {
            val repository = repository()
            client.emit(updateCall(call()))
            val before = repository.calls.first()

            client.emit(updateCall(call()))

            assertSame(before, repository.calls.first())
        }

    @Test
    fun `logging out forgets every call`() =
        runTest {
            val repository = repository()
            client.emit(updateCall(call()))

            client.emit(TdApiFixtures.authorizationStateUpdate(TdApiFixtures.closed()))

            assertNull(repository.call(3).first())
        }

    @Test
    fun `passes the other side's signaling data on as it comes`() =
        runTest {
            val repository = repository()
            val received = async(start = CoroutineStart.UNDISPATCHED) { repository.signalingData.take(2).toList() }

            client.emit(TdApi.UpdateNewCallSignalingData(3, byteArrayOf(1, 2)))
            client.emit(TdApi.UpdateNewCallSignalingData(4, byteArrayOf(3)))

            val data = received.await()
            assertEquals(listOf(3, 4), data.map { it.callId })
            assertArrayEquals(byteArrayOf(1, 2), data.first().data())
        }

    @Test
    fun `starts a call with the engine's protocol and returns its id`() =
        runTest {
            client.on(TdApi.CreateCall::class, TdResult.Success(TdApi.CallId(3)))

            assertEquals(Outcome.Success(3), repository().startCall(42, isVideo = false, protocol = protocol))
            val sent = client.sentOf<TdApi.CreateCall>().single()
            assertEquals(42L, sent.userId)
            assertEquals(listOf("13.0.0", "12.0.0"), sent.protocol.libraryVersions.toList())
            assertEquals(92, sent.protocol.maxLayer)
        }

    @Test
    fun `a call the other person's privacy refuses is a domain error`() =
        runTest {
            client.on(TdApi.CreateCall::class, TdResult.Failure(TdError(403, "USER_PRIVACY_RESTRICTED")))

            assertEquals(
                Outcome.Failure(DomainError.CallPrivacyRestricted),
                repository().startCall(42, isVideo = false, protocol = protocol),
            )
        }

    @Test
    fun `a call to an outdated app is a domain error`() =
        runTest {
            client.on(TdApi.CreateCall::class, TdResult.Failure(TdError(400, "PARTICIPANT_VERSION_OUTDATED")))

            assertEquals(
                Outcome.Failure(DomainError.CallPeerOutdated),
                repository().startCall(42, isVideo = false, protocol = protocol),
            )
        }

    @Test
    fun `answers a call with the engine's protocol`() =
        runTest {
            client.on(TdApi.AcceptCall::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository().acceptCall(3, protocol))
            assertEquals(3, client.sentOf<TdApi.AcceptCall>().single().callId)
        }

    @Test
    fun `ends a call with how long it lasted`() =
        runTest {
            client.on(TdApi.DiscardCall::class, TdResult.Success(TdApi.Ok()))

            val result =
                repository().discardCall(
                    3,
                    isDisconnected = true,
                    duration = 61.seconds,
                    isVideo = false,
                    connectionId = 7,
                )

            assertEquals(Outcome.Success(Unit), result)
            val sent = client.sentOf<TdApi.DiscardCall>().single()
            assertEquals(
                listOf<Any>(3, true, 61, 7L, ""),
                listOf(sent.callId, sent.isDisconnected, sent.duration, sent.connectionId, sent.inviteLink),
            )
        }

    @Test
    fun `rates an ended call with its stars and problems`() =
        runTest {
            client.on(TdApi.SendCallRating::class, TdResult.Success(TdApi.Ok()))

            val result = repository().rateCall(3, 2, setOf(CallProblem.Echo, CallProblem.PixelatedVideo))

            assertEquals(Outcome.Success(Unit), result)
            val sent = client.sentOf<TdApi.SendCallRating>().single()
            assertEquals(3, (sent.callId as TdApi.InputCallDiscarded).callId)
            assertEquals(2, sent.rating)
            assertEquals("", sent.comment)
            assertEquals(
                setOf(TdApi.CallProblemEcho.CONSTRUCTOR, TdApi.CallProblemPixelatedVideo.CONSTRUCTOR),
                sent.problems.map { it.constructor }.toSet(),
            )
        }

    @Test
    fun `sends the engine's signaling data`() =
        runTest {
            client.on(TdApi.SendCallSignalingData::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository().sendSignalingData(3, byteArrayOf(5)))
            assertArrayEquals(byteArrayOf(5), client.sentOf<TdApi.SendCallSignalingData>().single().data)
        }
}

/** Incoming voice call 3 from user 42, ringing. */
private fun call(
    id: Int = 3,
    state: TdApi.CallState = TdApi.CallStatePending(true, false),
) = TdApi.Call(id, 1000L + id, 42, false, false, state)

private fun updateCall(call: TdApi.Call) = TdApi.UpdateCall(call)

private fun CallProtocol.toTd() =
    TdApi.CallProtocol(udpP2p, udpReflector, minLayer, maxLayer, libraryVersions.toTypedArray())
