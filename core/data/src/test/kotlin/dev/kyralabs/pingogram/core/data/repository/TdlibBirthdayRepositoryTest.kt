package dev.kyralabs.pingogram.core.data.repository

import dev.kyralabs.pingogram.core.domain.model.Birthdate
import dev.kyralabs.pingogram.core.domain.model.CloseBirthday
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TdlibBirthdayRepositoryTest {
    private val client = FakeTdlibClient()

    @Test
    fun `holds the contacts TDLib announced last, none before`() =
        runTest {
            val repository = TdlibBirthdayRepository(client, backgroundScope)
            runCurrent()
            assertEquals(emptyList<CloseBirthday>(), repository.closeBirthdays.value)

            client.emit(update(TdApi.CloseBirthdayUser(7, TdApi.Birthdate(24, 11, 1990))))
            runCurrent()
            assertEquals(listOf(CloseBirthday(7, Birthdate(24, 11, 1990))), repository.closeBirthdays.value)

            client.emit(update(TdApi.CloseBirthdayUser(8, TdApi.Birthdate(25, 11, 0))))
            runCurrent()
            assertEquals(listOf(CloseBirthday(8, Birthdate(25, 11, null))), repository.closeBirthdays.value)
        }

    @Test
    fun `logging out forgets them`() =
        runTest {
            val repository = TdlibBirthdayRepository(client, backgroundScope)
            runCurrent()
            client.emit(update(TdApi.CloseBirthdayUser(7, TdApi.Birthdate(24, 11, 1990))))
            client.emit(TdApi.UpdateAuthorizationState(TdApi.AuthorizationStateClosed()))
            runCurrent()

            assertEquals(emptyList<CloseBirthday>(), repository.closeBirthdays.value)
        }

    private fun update(vararg users: TdApi.CloseBirthdayUser) = TdApi.UpdateContactCloseBirthdays(arrayOf(*users))
}
