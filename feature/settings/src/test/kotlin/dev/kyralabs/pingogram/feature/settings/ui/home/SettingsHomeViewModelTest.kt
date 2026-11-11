package dev.kyralabs.pingogram.feature.settings.ui.home

import dev.kyralabs.pingogram.core.data.repository.TdlibAccountFreezeRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibStorageRepository
import dev.kyralabs.pingogram.core.domain.model.AccountFreeze
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeAppLanguageRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsHomeViewModelTest {
    private val client = FakeTdlibClient()
    private val language = FakeAppLanguageRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them.
    private fun TestScope.viewModel(): SettingsHomeViewModel =
        SettingsHomeViewModel(
            TdlibSessionRepository(client),
            TdlibStorageRepository(client),
            language,
            TdlibAccountFreezeRepository(client, backgroundScope),
        ).also { runCurrent() }

    private fun session(id: Long) =
        TdApi.Session(
            id,
            id == 1L,
            false,
            false,
            true,
            true,
            TdApi.SessionDeviceTypeAndroid(),
            1,
            "Pingogram",
            "Pingogram 1.1.0",
            false,
            "Example Phone 8",
            "Android",
            "15",
            1_700_000_000,
            1_700_000_000,
            "203.0.113.7",
            "Berlin, Germany",
        )

    @Test
    fun `the home counts the sessions, adds up the storage and follows the language`() =
        runTest {
            client.on(
                TdApi.GetActiveSessions::class,
                TdResult.Success(TdApi.Sessions(arrayOf(session(1), session(2), session(3)), 180)),
            )
            client.on(
                TdApi.GetStorageStatisticsFast::class,
                TdResult.Success(TdApi.StorageStatisticsFast(1_000L, 4, 200L, 0, 0)),
            )
            val viewModel = viewModel()

            assertEquals(SettingsHomeSummaries(sessionCount = 3, storageBytes = 1_200L), viewModel.summaries.value)

            language.language.value = "de"
            runCurrent()

            assertEquals("de", viewModel.summaries.value.languageTag)
        }

    @Test
    fun `what TDLib cannot tell stays unknown`() =
        runTest {
            client.on(TdApi.GetActiveSessions::class, TdResult.Failure(TdError(500, "INTERNAL")))
            client.on(TdApi.GetStorageStatisticsFast::class, TdResult.Failure(TdError(500, "INTERNAL")))

            val summaries = viewModel().summaries.value

            assertNull(summaries.sessionCount)
            assertNull(summaries.storageBytes)
        }

    @Test
    fun `the home shows the account's freeze while Telegram keeps it frozen`() =
        runTest {
            val viewModel = viewModel()
            assertNull(viewModel.summaries.value.freeze)

            client.emit(TdApi.UpdateFreezeState(true, 1_700_000_000, 1_702_592_000, "https://t.me/spambot"))
            runCurrent()
            assertEquals(
                AccountFreeze(1_700_000_000, 1_702_592_000, "https://t.me/spambot"),
                viewModel.summaries.value.freeze,
            )

            client.emit(TdApi.UpdateFreezeState(false, 0, 0, ""))
            runCurrent()
            assertNull(viewModel.summaries.value.freeze)
        }
}
