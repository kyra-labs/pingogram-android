package dev.kyralabs.pingogram.feature.settings.ui.websites

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectedWebsiteRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.feature.settings.ui.TEST_NOW
import dev.kyralabs.pingogram.feature.settings.ui.plainSpaces
import dev.kyralabs.pingogram.feature.settings.ui.testTimeFormats
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The Websites page through the real TDLib repositories on [FakeTdlibClient], as the app wires them. */
@OptIn(ExperimentalCoroutinesApi::class)
class ConnectedWebsitesViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(handle: SavedStateHandle = savedState): ConnectedWebsitesViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        runCurrent()
        return ConnectedWebsitesViewModel(handle, TdlibConnectedWebsiteRepository(client), users, testTimeFormats())
            .also { runCurrent() }
    }

    private fun TestScope.effectsOf(viewModel: ConnectedWebsitesViewModel): List<ConnectedWebsitesEffect> {
        val effects = mutableListOf<ConnectedWebsitesEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun website(
        id: Long,
        domain: String,
        location: String = "Berlin, Germany",
    ) = TdApi.ConnectedWebsite(
        id,
        domain,
        BOT,
        "Firefox",
        "Linux",
        (TEST_NOW.epochSecond - DAY).toInt(),
        (TEST_NOW.epochSecond - HOUR).toInt(),
        "203.0.113.1",
        location,
    )

    private fun answerWebsites(vararg websites: TdApi.ConnectedWebsite) =
        client.on(TdApi.GetConnectedWebsites::class, TdResult.Success(TdApi.ConnectedWebsites(arrayOf(*websites))))

    private val ConnectedWebsitesViewModel.websites
        get() = (uiState.value.websites as WebsitesState.Loaded).websites

    @Test
    fun `lists the websites with the bot, browser, system and last activity`() =
        runTest {
            client.on(TdApi.GetUser::class, TdResult.Success(TdApiFixtures.user(id = BOT, firstName = "Example Bot")))
            answerWebsites(website(1, "example.org"))

            val viewModel = viewModel()

            val website = viewModel.websites.single()
            assertEquals("example.org", website.domain)
            assertEquals(listOf("Example Bot", "Firefox, Linux"), website.details.plainParts())
            assertEquals(listOf("203.0.113.1", "Berlin, Germany", "8:41 AM"), website.activity.plainParts())
        }

    @Test
    fun `a failure shows, and trying again loads them`() =
        runTest {
            client.on(TdApi.GetConnectedWebsites::class, TdResult.Failure(TdError(500, "Internal")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value.websites is WebsitesState.Failed)

            answerWebsites(website(1, "example.org"))
            viewModel.onEvent(ConnectedWebsitesEvent.Retry)
            runCurrent()

            assertEquals(listOf(1L), viewModel.websites.map { it.id })
        }

    @Test
    fun `logging a website out asks first, then removes it`() =
        runTest {
            answerWebsites(website(1, "example.org"), website(2, "shop.example"))
            client.on(TdApi.DisconnectWebsite::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ConnectedWebsitesEvent.Disconnect(viewModel.websites.first()))
            assertEquals(DisconnectDialog.Website(1, "example.org"), viewModel.uiState.value.dialog)
            assertTrue(client.sentOf<TdApi.DisconnectWebsite>().isEmpty())

            viewModel.onEvent(ConnectedWebsitesEvent.Confirm)
            runCurrent()

            assertEquals(1L, client.sentOf<TdApi.DisconnectWebsite>().single().websiteId)
            assertEquals(listOf(2L), viewModel.websites.map { it.id })
            assertNull(viewModel.uiState.value.dialog)
            assertEquals(listOf(ConnectedWebsitesEffect.Disconnected("example.org")), effects)
        }

    @Test
    fun `logging all out empties the list`() =
        runTest {
            answerWebsites(website(1, "example.org"), website(2, "shop.example"))
            client.on(TdApi.DisconnectAllWebsites::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ConnectedWebsitesEvent.DisconnectAll)
            viewModel.onEvent(ConnectedWebsitesEvent.Confirm)
            runCurrent()

            assertTrue(viewModel.websites.isEmpty())
            assertEquals(listOf(ConnectedWebsitesEffect.AllDisconnected), effects)
        }

    @Test
    fun `a refused log-out keeps the website and says why`() =
        runTest {
            answerWebsites(website(1, "example.org"))
            client.on(
                TdApi.DisconnectWebsite::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 30")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(ConnectedWebsitesEvent.Disconnect(viewModel.websites.first()))
            viewModel.onEvent(ConnectedWebsitesEvent.Confirm)
            runCurrent()

            assertEquals(listOf(1L), viewModel.websites.map { it.id })
            assertTrue((effects.single() as ConnectedWebsitesEffect.ShowError).error is DomainError.FloodWait)
        }

    @Test
    fun `dismissing the question sends nothing`() =
        runTest {
            answerWebsites(website(1, "example.org"))
            val viewModel = viewModel()

            viewModel.onEvent(ConnectedWebsitesEvent.DisconnectAll)
            viewModel.onEvent(ConnectedWebsitesEvent.Dismiss)
            runCurrent()

            assertNull(viewModel.uiState.value.dialog)
            assertTrue(client.sentOf<TdApi.DisconnectAllWebsites>().isEmpty())
        }

    @Test
    fun `the question survives process death`() =
        runTest {
            answerWebsites(website(1, "example.org"))
            val viewModel = viewModel()
            viewModel.onEvent(ConnectedWebsitesEvent.Disconnect(viewModel.websites.first()))

            val restored = viewModel(SavedStateHandle(savedState.keys().associateWith { savedState.get<Any>(it) }))

            assertEquals(DisconnectDialog.Website(1, "example.org"), restored.uiState.value.dialog)
        }

    private companion object {
        const val BOT = 77L
        const val HOUR = 3_600L
        const val DAY = 86_400L
    }
}

/** The plain texts that [this] joins, in order, without their isolates and with plain spaces. */
private fun UiText.plainParts(): List<String> =
    when (this) {
        is UiText.Plain -> listOf(plainSpaces(text.trim('⁨', '⁩')))
        is UiText.Resource -> args.flatMap { (it as UiText).plainParts() }
        else -> error("Unexpected $this")
    }
