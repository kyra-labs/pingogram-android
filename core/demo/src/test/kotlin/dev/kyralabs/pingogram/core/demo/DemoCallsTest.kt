package dev.kyralabs.pingogram.core.demo

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class DemoCallsTest {
    @TempDir
    lateinit var dir: File

    private lateinit var harness: DemoTestHarness

    @BeforeEach
    fun setUp() {
        harness = DemoTestHarness(dir)
    }

    private fun TestScope.client() = with(harness) { start() }

    private val protocol = TdApi.CallProtocol(true, true, 65, 92, arrayOf("demo"))

    private fun List<TdApi.Update>.states(callId: Int) =
        filterIsInstance<TdApi.UpdateCall>().filter { it.call.id == callId }.map { it.call.state }

    @Test
    fun `a call to a sample person rings, is answered with four emojis, and ends in the chat`() =
        runTest {
            val (client, updates) = client()

            val id = client.ask(TdApi.CreateCall(DemoData.ADA, protocol, false)).id
            runCurrent()
            advanceTimeBy(5_000)
            runCurrent()
            client.ask(TdApi.DiscardCall(id, false, "", 42, false, 0))
            runCurrent()

            val states = updates.states(id)
            assertEquals(listOf(false, true), states.filterIsInstance<TdApi.CallStatePending>().map { it.isReceived })
            val ready = states.filterIsInstance<TdApi.CallStateReady>().single()
            assertEquals(4, ready.emojis.size)
            assertInstanceOf(TdApi.CallStateDiscarded::class.java, states.last())
            val last = client.ask(TdApi.GetChatHistory(DemoData.ADA_CHAT, 0, 0, 1, false)).messages.single()
            val recorded = last.content as TdApi.MessageCall
            assertEquals(42, recorded.duration)
            assertInstanceOf(TdApi.CallDiscardReasonHungUp::class.java, recorded.discardReason)
        }

    @Test
    fun `a sample person calls when asked, and a declined call is kept in the chat after a restart`() =
        runTest {
            val (client, updates) = client()

            client.ask(DemoIncomingCall(DemoData.BEN))
            runCurrent()
            val before = updates.filterIsInstance<TdApi.UpdateCall>().size
            advanceTimeBy(2_500)
            runCurrent()
            val ringing = updates.filterIsInstance<TdApi.UpdateCall>().single()
            client.ask(TdApi.DiscardCall(ringing.call.id, false, "", 0, false, 0))
            val (restarted, _) = client()

            assertEquals(0, before)
            assertTrue(!ringing.call.isOutgoing)
            val found = restarted.ask(TdApi.SearchCallMessages("", 20, true)).messages.single()
            assertInstanceOf(
                TdApi.CallDiscardReasonDeclined::class.java,
                (found.content as TdApi.MessageCall).discardReason,
            )
        }

    @Test
    fun `an answered incoming call connects`() =
        runTest {
            val (client, updates) = client()
            client.ask(DemoIncomingCall(DemoData.CHLOE))
            advanceTimeBy(2_500)
            runCurrent()
            val id =
                updates
                    .filterIsInstance<TdApi.UpdateCall>()
                    .single()
                    .call.id

            client.ask(TdApi.AcceptCall(id, protocol))
            advanceTimeBy(1_000)
            runCurrent()

            assertInstanceOf(TdApi.CallStateReady::class.java, updates.states(id).last())
        }

    @Test
    fun `the book club's voice chat has sample people, who speak once the user joins`() =
        runTest {
            val (client, updates) = client()

            val call = client.ask(TdApi.GetGroupCall(DemoData.SAMPLE_VOICE_CHAT))
            val joinParameters = TdApi.GroupCallJoinParameters(1, "{}", true, false)
            client.ask(TdApi.JoinVideoChat(DemoData.SAMPLE_VOICE_CHAT, null, joinParameters, ""))
            runCurrent()
            advanceTimeBy(2_000)
            runCurrent()

            assertTrue(call.isActive && !call.isJoined)
            val participants = updates.filterIsInstance<TdApi.UpdateGroupCallParticipant>()
            assertEquals(
                setOf(DemoData.ME, DemoData.ADA, DemoData.BEN, DemoData.CHLOE),
                participants.map { (it.participant.participantId as TdApi.MessageSenderUser).userId }.toSet(),
            )
            assertTrue(participants.last().participant.isSpeaking)
        }

    @Test
    fun `the user ends the voice chat for everyone, which stays ended`() =
        runTest {
            val (client, _) = client()

            client.ask(TdApi.EndGroupCall(DemoData.SAMPLE_VOICE_CHAT))
            val (restarted, updates) = client()

            assertTrue(!restarted.ask(TdApi.GetGroupCall(DemoData.SAMPLE_VOICE_CHAT)).isActive)
            val forum = updates.filterIsInstance<TdApi.UpdateNewChat>().first { it.chat.id == DemoData.FORUM_CHAT }
            assertEquals(0, forum.chat.videoChat.groupCallId)
        }
}
