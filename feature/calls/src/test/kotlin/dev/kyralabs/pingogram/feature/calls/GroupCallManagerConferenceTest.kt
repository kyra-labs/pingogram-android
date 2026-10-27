package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.ConferenceChannel
import dev.kyralabs.pingogram.core.domain.model.ConferenceInviteResult
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.core.domain.model.GroupCallParticipant
import dev.kyralabs.pingogram.core.domain.model.MessageSender
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** [GroupCallManager] with conference calls (`docs/adr/0025-conference-calls.md`). */
class GroupCallManagerConferenceTest {
    private val repository = FakeGroupCallRepository().apply { calls.value += CONFERENCE_CALL_ID to groupCall() }
    private val conferences = FakeConferenceCallRepository()
    private val engine = FakeCallEngine()
    private val system = FakeCallSystem()
    private val foreground = FakeCallForeground()
    private val line = CallLine()
    private val link = ConferenceTarget.Link(CONFERENCE_LINK)

    private fun TestScope.manager() =
        GroupCallManager(
            repository,
            conferences,
            engine,
            system,
            foreground,
            FakeCallClock(),
            { "" },
            line,
            { FakeNetworkRepository() },
            backgroundScope,
        )

    private fun TestScope.joined(manager: GroupCallManager): FakeGroupCallSession {
        launchNow { manager.joinConference(link) }
        runCurrent()
        return engine.groupSessions.single()
    }

    @Test
    fun `a link joins muted through TDLib, connects with its answer and follows the call TDLib named`() =
        runTest {
            val manager = manager()

            val session = joined(manager)

            val (target, join) = conferences.joins.single()
            assertEquals(link, target)
            assertEquals(session.joinPayload, join.payload)
            assertEquals(FakeGroupCallSession.AUDIO_SOURCE, join.audioSourceId)
            assertTrue(join.isMuted)
            assertTrue(repository.joins.isEmpty())
            assertEquals(CONFERENCE_PARAMS, session.params)
            assertEquals(listOf(CONFERENCE_CALL_ID), repository.loaded)
            val call = manager.call.value!!
            assertTrue(call.isConference)
            assertEquals(0L, call.chatId)
            assertEquals(CONFERENCE_CALL_ID, call.groupCallId)
            assertTrue(line.isBusy)
        }

    @Test
    fun `frames are encrypted by TDLib once the call is joined, and dropped before`() =
        runTest {
            val manager = manager()
            val answer = CompletableDeferred<Unit>()
            conferences.holdJoin = answer
            launchNow { manager.joinConference(link) }
            runCurrent()
            val encryption = engine.encryptions.single()

            assertNull(encryption.encrypt(ConferenceChannel.Main, byteArrayOf(1), 0))
            assertTrue(conferences.frames.isEmpty())

            answer.complete(Unit)
            runCurrent()
            encryption.encrypt(ConferenceChannel.Main, byteArrayOf(1), 0)
            encryption.decrypt(7, byteArrayOf(2))

            assertEquals(listOf("encrypt $CONFERENCE_CALL_ID", "decrypt $CONFERENCE_CALL_ID 7"), conferences.frames)
        }

    @Test
    fun `the engine learns whose audio sources are from the participants TDLib announced`() =
        runTest {
            val manager = manager()
            val session = joined(manager)
            repository.participant(participant(7, ssrc = 70))
            repository.participant(participant(8, ssrc = 80))
            runCurrent()

            session.unknown.trySend(listOf(70, 90))
            runCurrent()

            assertEquals(listOf(mapOf(70 to 7L)), session.mappedSources)
        }

    @Test
    fun `a link refused by TDLib ends the call with the reason, and nothing joins while the user is in a call`() =
        runTest {
            conferences.joinResult = Outcome.Failure(DomainError.AdminRightsRequired)
            val manager = manager()
            launchNow { manager.joinConference(link) }
            runCurrent()

            assertEquals(
                GroupCallPhase.Ended(GroupCallEndReason.Failed(DomainError.AdminRightsRequired)),
                manager.call.value?.phase,
            )

            val busy = CallLine().also { it.take(Any()) }
            val other =
                GroupCallManager(
                    repository,
                    conferences,
                    engine,
                    system,
                    foreground,
                    FakeCallClock(),
                    { "" },
                    busy,
                    { FakeNetworkRepository() },
                    backgroundScope,
                )
            assertEquals(JoinResult.Busy, other.joinConference(link))
        }

    @Test
    fun `inviting counts who was invited or is in the call already, and names who could not be`() =
        runTest {
            val manager = manager()
            joined(manager)
            conferences.inviteResults =
                mapOf(
                    8L to Outcome.Success(ConferenceInviteResult.AlreadyParticipant),
                    9L to Outcome.Success(ConferenceInviteResult.PrivacyRestricted),
                    10L to Outcome.Failure(DomainError.Network),
                )

            val result = manager.invite(listOf(7L, 8L, 9L, 10L))

            assertEquals(Outcome.Success(ConferenceInvites(invited = 2, refused = persistentListOf(9L, 10L))), result)
            assertEquals(
                listOf(7L, 8L, 9L, 10L).map { Triple(CONFERENCE_CALL_ID, it, false) },
                conferences.invites,
            )
        }

    @Test
    fun `inviting fails with the first error when nobody could be invited, and does nothing outside a conference`() =
        runTest {
            val manager = manager()
            assertEquals(Outcome.Failure(DomainError.NotFound), manager.invite(listOf(7L)))

            joined(manager)
            conferences.inviteResults = mapOf(7L to Outcome.Failure(DomainError.Network))

            assertEquals(Outcome.Failure(DomainError.Network), manager.invite(listOf(7L)))
        }

    private fun participant(
        userId: Long,
        ssrc: Int,
    ) = GroupCallParticipant(
        participant = MessageSender.User(userId),
        audioSourceId = ssrc,
        bio = "",
        isCurrentUser = false,
        isSpeaking = false,
        isHandRaised = false,
        isMutedForAllUsers = false,
        isMutedForCurrentUser = false,
        canUnmuteSelf = false,
        canBeMutedForAllUsers = false,
        canBeUnmutedForAllUsers = false,
        order = "$userId",
    )
}
