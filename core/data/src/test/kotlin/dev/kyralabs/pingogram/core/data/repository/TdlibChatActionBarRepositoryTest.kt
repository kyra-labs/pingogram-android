package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.common.result.Outcome
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Dismissing a chat's action bar and sharing the phone number with a contact, one request each. */
class TdlibChatActionBarRepositoryTest {
    private val client = FakeTdlibClient()
    private val repository = TdlibChatActionBarRepository(client)

    @Test
    fun `dismissing removes the chat's bar`() =
        runTest {
            client.on(TdApi.RemoveChatActionBar::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.dismiss(CHAT))
            assertEquals(CHAT, client.sentOf<TdApi.RemoveChatActionBar>().single().chatId)
        }

    @Test
    fun `sharing the phone number goes to the user`() =
        runTest {
            client.on(TdApi.SharePhoneNumber::class, TdResult.Success(TdApi.Ok()))

            assertEquals(Outcome.Success(Unit), repository.sharePhoneNumber(USER))
            assertEquals(USER, client.sentOf<TdApi.SharePhoneNumber>().single().userId)
        }

    @Test
    fun `a refusal comes back as its error`() =
        runTest {
            client.on(TdApi.SharePhoneNumber::class, TdResult.Failure(TdError(400, "USER_NOT_MUTUAL_CONTACT")))

            assertEquals(Outcome.Failure(DomainError.NotMutualContact), repository.sharePhoneNumber(USER))
        }

    private companion object {
        const val CHAT = 42L
        const val USER = 7L
    }
}
