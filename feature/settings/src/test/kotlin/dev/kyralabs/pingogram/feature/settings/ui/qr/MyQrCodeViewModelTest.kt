package dev.kyralabs.pingogram.feature.settings.ui.qr

import androidx.lifecycle.viewModelScope
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibProfileRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.ProfileLink
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The user's own QR code: TDLib's link to the profile, with the user's name. */
@OptIn(ExperimentalCoroutinesApi::class)
class MyQrCodeViewModelTest {
    private val client = FakeTdlibClient()
    private val created = mutableListOf<MyQrCodeViewModel>()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        created.forEach { it.viewModelScope.cancel() }
        Dispatchers.resetMain()
    }

    // The real TDLib repositories on FakeTdlibClient, subscribed before the test emits, as at app start.
    private suspend fun TestScope.viewModel(link: TdResult<TdApi.UserLink>): MyQrCodeViewModel {
        client.on(TdApi.GetUserLink::class, link)
        val users = TdlibUserRepository(client, backgroundScope)
        val profile = TdlibProfileRepository(client, backgroundScope)
        runCurrent()
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ME, firstName = "Ada", lastName = "Lovelace")))
        client.emit(TdApiFixtures.myIdOption(ME))
        runCurrent()
        val viewModel = MyQrCodeViewModel(profile, users).also { created += it }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `shows the code of the profile link once TDLib gave it`() =
        runTest {
            val viewModel = viewModel(TdResult.Success(TdApi.UserLink("https://t.me/ada", 0)))
            assertEquals(MyQrCodeUiState.Loading, viewModel.uiState.value)

            runCurrent()

            val content = viewModel.uiState.value as MyQrCodeUiState.Content
            assertEquals(ME, content.userId)
            assertEquals(ProfileLink("https://t.me/ada", 0), content.link)
        }

    @Test
    fun `without a username the link is temporary`() =
        runTest {
            val viewModel = viewModel(TdResult.Success(TdApi.UserLink("https://t.me/contact/abc", 3_600)))
            runCurrent()

            assertEquals(true, (viewModel.uiState.value as MyQrCodeUiState.Content).link.isTemporary)
        }

    @Test
    fun `a failure shows, and asks again on retry`() =
        runTest {
            val viewModel = viewModel(TdResult.Failure(TdError(500, "Network is unreachable")))
            runCurrent()
            assertEquals(MyQrCodeUiState.Error(DomainError.Network), viewModel.uiState.value)

            client.on(TdApi.GetUserLink::class, TdResult.Success(TdApi.UserLink("https://t.me/ada", 0)))
            viewModel.onEvent(MyQrCodeEvent.Retry)
            runCurrent()

            assertEquals(ProfileLink("https://t.me/ada", 0), (viewModel.uiState.value as MyQrCodeUiState.Content).link)
        }

    private companion object {
        const val ME = 1L
    }
}
