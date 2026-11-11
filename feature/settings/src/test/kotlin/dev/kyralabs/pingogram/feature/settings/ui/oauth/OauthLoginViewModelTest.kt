package dev.kyralabs.pingogram.feature.settings.ui.oauth

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibOauthLoginRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OauthLoginViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info(matchCodeFirst = false)))
        client.on(TdApi.CheckOauthRequestMatchCode::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.AcceptOauthRequest::class, TdResult.Success(TdApi.HttpUrl(DONE)))
        client.on(TdApi.DeclineOauthRequest::class, TdResult.Success(TdApi.Ok()))
        client.on(
            TdApi.GetUser::class,
        ) { TdResult.Success(TdApiFixtures.user(id = it.userId, usernames = listOf("bot"))) }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun info(matchCodeFirst: Boolean) =
        TdApi.OauthLinkInfo(
            ME,
            URL,
            "example.org",
            false,
            "Example",
            BOT,
            true,
            true,
            false,
            "",
            "",
            "",
            "Firefox 145",
            "Linux",
            "203.0.113.7",
            "Berlin, Germany",
            matchCodeFirst,
            arrayOf("17", "42"),
        )

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): OauthLoginViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        runCurrent()
        backgroundScope.launch {
            client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = ME, firstName = "Ada", lastName = "Lovelace")))
            client.emit(TdApiFixtures.myIdOption(ME))
        }
        runCurrent()
        return OauthLoginViewModel(URL, handle, TdlibOauthLoginRepository(client), users, backgroundScope).also {
            runCurrent()
        }
    }

    private fun TestScope.effectsOf(viewModel: OauthLoginViewModel): List<OauthLoginEffect> {
        val effects = mutableListOf<OauthLoginEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    @Test
    fun `shows what the request asks for, sharing nothing at first`() =
        runTest {
            val state = viewModel().uiState.value

            assertEquals(OauthLoginStep.Confirm, state.step)
            assertEquals("example.org", state.request?.domain)
            assertEquals("Ada Lovelace", state.accountName)
            assertEquals("@bot", state.botName)
            assertFalse(state.allowMessages)
            assertFalse(state.sharePhoneNumber)
            assertTrue(client.sentOf<TdApi.AcceptOauthRequest>().isEmpty())
        }

    @Test
    fun `logging in shares only what the user turned on, then opens the website`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(OauthLoginEvent.SetSharePhoneNumber(true))
            viewModel.onEvent(OauthLoginEvent.LogIn)
            runCurrent()

            val accept = client.sentOf<TdApi.AcceptOauthRequest>().single()
            assertTrue(accept.allowPhoneNumberAccess)
            assertFalse(accept.allowWriteAccess)
            assertEquals(OauthLoginStep.Done, viewModel.uiState.value.step)
            assertEquals(listOf(OauthLoginEffect.OpenUrl(DONE)), effects)
        }

    @Test
    fun `a code asked first is checked before the login shows`() =
        runTest {
            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info(matchCodeFirst = true)))
            val viewModel = viewModel()
            assertEquals(OauthLoginStep.MatchCode, viewModel.uiState.value.step)

            viewModel.onEvent(OauthLoginEvent.PickCode("42"))
            runCurrent()
            viewModel.onEvent(OauthLoginEvent.LogIn)
            runCurrent()

            assertEquals("42", client.sentOf<TdApi.CheckOauthRequestMatchCode>().single().matchCode)
            assertEquals("42", client.sentOf<TdApi.AcceptOauthRequest>().single().matchCode)
        }

    @Test
    fun `a wrong code ends the request without declining it again`() =
        runTest {
            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info(matchCodeFirst = true)))
            client.on(TdApi.CheckOauthRequestMatchCode::class, TdResult.Failure(TdError(400, "MATCH_CODE_INVALID")))
            val viewModel = viewModel()

            viewModel.onEvent(OauthLoginEvent.PickCode("17"))
            runCurrent()
            viewModel.onDismissed()
            runCurrent()

            assertEquals(OauthLoginStep.Failed, viewModel.uiState.value.step)
            assertTrue(client.sentOf<TdApi.DeclineOauthRequest>().isEmpty())
        }

    @Test
    fun `declining, or closing the sheet unanswered, declines the request once`() =
        runTest {
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(OauthLoginEvent.Decline)
            viewModel.onDismissed()
            runCurrent()

            assertEquals(URL, client.sentOf<TdApi.DeclineOauthRequest>().single().url)
            assertEquals(listOf(OauthLoginEffect.Close), effects)
        }

    @Test
    fun `a request that cannot be read says why and tries again`() =
        runTest {
            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Failure(TdError(400, "URL_INVALID")))
            val viewModel = viewModel()
            assertEquals(OauthLoginStep.Failed, viewModel.uiState.value.step)
            assertTrue(viewModel.uiState.value.error is DomainError)

            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info(matchCodeFirst = false)))
            viewModel.onEvent(OauthLoginEvent.Retry)
            runCurrent()

            assertEquals(OauthLoginStep.Confirm, viewModel.uiState.value.step)
            // Nothing was read, so closing declines nothing.
            assertTrue(client.sentOf<TdApi.DeclineOauthRequest>().isEmpty())
        }

    @Test
    fun `the code picked and the switches survive process death`() =
        runTest {
            client.on(TdApi.GetOauthLinkInfo::class, TdResult.Success(info(matchCodeFirst = true)))
            val handle = SavedStateHandle()
            val first = viewModel(handle)
            first.onEvent(OauthLoginEvent.PickCode("42"))
            first.onEvent(OauthLoginEvent.SetAllowMessages(true))
            runCurrent()

            val restored = viewModel(handle).uiState.value

            assertEquals(OauthLoginStep.Confirm, restored.step)
            assertTrue(restored.allowMessages)
        }

    private companion object {
        const val URL = "https://oauth.telegram.org/auth?bot_id=7"
        const val DONE = "https://example.org/done"
        const val ME = 42L
        const val BOT = 7L
    }
}
