package dev.kyralabs.pingogram.feature.calls

import app.cash.turbine.test
import dev.kyralabs.pingogram.core.domain.model.ChatVideoChat
import dev.kyralabs.pingogram.core.ui.component.VoiceChatSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** [voiceChatSummary]: what a chat's voice chat bar and info page show of its voice chat. */
class VoiceChatSummaryTest {
    private val repository = FakeGroupCallRepository()
    private val current = MutableStateFlow<ActiveGroupCall?>(null)

    @Test
    fun `a chat's voice chat shows its count, then that the user is in it, then nothing once it ends`() =
        runTest {
            voiceChatSummary(CHAT_ID, repository, current).test {
                assertEquals(VoiceChatSummary(3, isLiveStream = false, isJoined = false), awaitItem())

                current.value = ActiveGroupCall(CHAT_ID, CALL_ID, GroupCallPhase.Connected)
                assertEquals(VoiceChatSummary(3, isLiveStream = false, isJoined = true), awaitItem())

                repository.calls.value = mapOf(CALL_ID to groupCall().copy(isRtmpStream = true, participantCount = 40))
                assertEquals(VoiceChatSummary(40, isLiveStream = true, isJoined = true), awaitItem())

                repository.videoChats.value = emptyMap()
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a chat without a voice chat shows none`() =
        runTest {
            repository.videoChats.value = mapOf(1L to ChatVideoChat(CALL_ID, true, null))
            voiceChatSummary(CHAT_ID, repository, current).test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
