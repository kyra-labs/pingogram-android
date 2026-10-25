package dev.kyralabs.pingogram.feature.chat.ui

import android.os.Looper
import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.testing.FakeChatLockRepository
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.PRIVATE_CHAT
import dev.kyralabs.pingogram.feature.chat.ui.ChatFixtures.message
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardResults
import dev.kyralabs.pingogram.feature.chat.ui.forward.ForwardViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * [ForwardViewModel] takes its route, the chat and the messages to forward, from its `SavedStateHandle`, as Navigation
 * hands it to the destination and gives it back after process death: the sheet opens again for the same messages.
 * Robolectric, since Navigation reads the arguments through a `Bundle`.
 */
@RunWith(RobolectricTestRunner::class)
class ForwardViewModelSavedStateTest {
    @Test
    fun `the route comes from the saved state, as after process death`() {
        val messages = FakeMessageRepository().apply { onMessage = { _, id -> Outcome.Success(message(id)) } }
        val actions = FakeMessageActionRepository()
        val scope = MainScope()
        // What Navigation keeps of ForwardRoute(PRIVATE_CHAT, listOf(5, 6)) in the destination's saved state.
        val restored = SavedStateHandle(mapOf("fromChatId" to PRIVATE_CHAT, "messageIds" to longArrayOf(5, 6)))

        ForwardViewModel(
            savedStateHandle = restored,
            chats = FakeChatRepository(),
            messages = messages,
            search = FakeSearchRepository(),
            users = FakeUserRepository(),
            groups = FakeGroupRepository(),
            actions = actions,
            dispatcher = Dispatchers.Main,
            applicationScope = scope,
            results = ForwardResults(),
            chatLock = FakeChatLockRepository(),
        )
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf(PRIVATE_CHAT to 5L, PRIVATE_CHAT to 6L), messages.messageRequests)
        assertEquals(listOf(5L, 6L), actions.propertyRequests)
        scope.cancel()
    }
}
