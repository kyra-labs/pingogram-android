package dev.kyralabs.pingogram.feature.calls

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.domain.model.Call
import dev.kyralabs.pingogram.core.domain.model.CallConnection
import dev.kyralabs.pingogram.core.domain.model.CallDiscardReason
import dev.kyralabs.pingogram.core.domain.model.CallState
import dev.kyralabs.pingogram.core.domain.model.ConferenceTarget
import dev.kyralabs.pingogram.feature.calls.engine.CallMediaState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A one-to-one call that becomes a conference call (`docs/adr/0025-conference-calls.md`), from either side. */
@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerUpgradeTest {
    private val repository = FakeCallRepository()
    private val groupRepository = FakeGroupCallRepository()
    private val conferences = FakeConferenceCallRepository()
    private val engine = FakeCallEngine()
    private val line = CallLine()
    private lateinit var groups: GroupCallManager

    private fun TestScope.managers(): CallManager {
        groups =
            GroupCallManager(
                groupRepository,
                conferences,
                engine,
                FakeCallSystem(),
                FakeCallForeground(),
                FakeCallClock(),
                { "" },
                line,
                { FakeNetworkRepository() },
                backgroundScope,
            )
        return CallManager(
            repository,
            engine,
            FakeCallSystem(),
            FakeCallForeground(),
            FakeCallTones(),
            FakeCallClock(),
            { _, _ -> "Ada" },
            { FakeNetworkRepository() },
            line,
            { groups },
            backgroundScope,
        ).also {
            it.start()
            runCurrent()
        }
    }

    private fun connection(supported: Boolean = true) =
        CallConnection(
            protocol = ENGINE_PROTOCOL,
            servers = persistentListOf(),
            encryptionKey = ByteArray(256) { 1 },
            emojis = persistentListOf("😀", "🐶", "🍎", "🚗"),
            allowP2p = true,
            customParameters = "",
            isGroupCallSupported = supported,
        )

    private fun call(state: CallState) =
        Call(FakeCallRepository.STARTED_CALL_ID, userId = 42, isOutgoing = true, isVideo = false, state = state)

    /** A call to user 42, connected, that both sides can turn into a conference call where [supported]. */
    private fun TestScope.connected(
        manager: CallManager,
        supported: Boolean = true,
    ) {
        backgroundScope.launchNow { manager.startCall(42) }
        repository.update(call(CallState.Pending(isCreated = true, isReceived = true)))
        repository.update(call(CallState.Ready(connection(supported))))
        runCurrent()
        engine.sessions
            .single()
            .state.value = CallMediaState.Connected
        runCurrent()
    }

    @Test
    fun `adding people starts a conference call on the same line, discards the call with its link and invites them`() =
        runTest {
            val manager = managers()
            connected(manager)
            assertTrue(manager.call.value!!.canUpgrade)
            groupRepository.calls.value += CONFERENCE_CALL_ID to groupCall().copy(inviteLink = CONFERENCE_LINK)

            backgroundScope.launchNow { manager.upgradeToConference(listOf(7L, 8L)) }
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.UpgradedToGroupCall), manager.call.value?.phase)
            assertTrue(engine.sessions.single().closed)
            assertFalse(conferences.creates.single().isMuted)
            assertEquals(listOf(CONFERENCE_LINK), repository.discardedLinks)
            assertEquals(listOf(7L, 8L), conferences.invites.map { it.second })
            assertEquals(Outcome.Success(ConferenceInvites(invited = 2)), groups.upgradeInvites.value)
            val conference = groups.call.value!!
            assertTrue(conference.isConference)
            assertEquals(CONFERENCE_CALL_ID, conference.groupCallId)
            assertFalse(conference.isMuted)
            assertTrue(line.isBusy)
        }

    @Test
    fun `a conference call that cannot be made ends the call with why`() =
        runTest {
            conferences.joinResult = Outcome.Failure(DomainError.Network)
            val manager = managers()
            connected(manager)

            backgroundScope.launchNow { manager.upgradeToConference(listOf(7L)) }
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.Failed(DomainError.Network)), manager.call.value?.phase)
            assertEquals(listOf(""), repository.discardedLinks)
            assertTrue(conferences.invites.isEmpty())
            assertFalse(line.isBusy)
        }

    @Test
    fun `nothing is upgraded where the other side cannot take part in a conference call`() =
        runTest {
            val manager = managers()
            connected(manager, supported = false)
            assertFalse(manager.call.value!!.canUpgrade)

            manager.upgradeToConference(listOf(7L))
            runCurrent()

            assertEquals(CallPhase.Connected, manager.call.value?.phase)
            assertTrue(conferences.creates.isEmpty())
        }

    @Test
    fun `the other side's upgrade joins its conference call by the link, unmuted as the call was`() =
        runTest {
            val manager = managers()
            connected(manager)

            repository.update(
                call(
                    CallState.Discarded(CallDiscardReason.UpgradedToGroupCall, false, upgradeLink = CONFERENCE_LINK),
                ),
            )
            runCurrent()

            assertEquals(CallPhase.Ended(CallEndReason.UpgradedToGroupCall), manager.call.value?.phase)
            val (target, join) = conferences.joins.single()
            assertEquals(ConferenceTarget.Link(CONFERENCE_LINK), target)
            assertFalse(join.isMuted)
            assertTrue(groups.call.value!!.isConference)
            assertTrue(line.isBusy)
        }
}
