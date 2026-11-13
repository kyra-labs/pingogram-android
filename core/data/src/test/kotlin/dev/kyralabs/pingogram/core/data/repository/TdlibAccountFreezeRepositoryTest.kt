package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.AccountFreeze
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibAccountFreezeRepositoryTest {
    private val client = FakeTdlibClient()

    @Test
    fun `holds the freeze TDLib announced, and forgets it once lifted`() =
        runTest {
            val repository = TdlibAccountFreezeRepository(client, backgroundScope)
            runCurrent()
            assertNull(repository.freeze.value)

            client.emit(TdApi.UpdateFreezeState(true, 1_700_000_000, 1_702_592_000, "https://t.me/spambot"))
            runCurrent()
            assertEquals(AccountFreeze(1_700_000_000, 1_702_592_000, "https://t.me/spambot"), repository.freeze.value)

            client.emit(TdApi.UpdateFreezeState(false, 0, 0, ""))
            runCurrent()
            assertNull(repository.freeze.value)
        }

    @Test
    fun `forgets the freeze when TDLib closes`() =
        runTest {
            val repository = TdlibAccountFreezeRepository(client, backgroundScope)
            runCurrent()
            client.emit(TdApi.UpdateFreezeState(true, 1_700_000_000, 1_702_592_000, "https://t.me/spambot"))
            runCurrent()

            client.emitAuthorizationState(TdApi.AuthorizationStateClosed())
            runCurrent()

            assertNull(repository.freeze.value)
        }
}
