package dev.kyralabs.pingogram.feature.settings.ui.privacy.messages

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibPrivacyRepository
import dev.kyralabs.pingogram.core.domain.model.NewChatPrivacy
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NewChatPrivacyViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answer(allows = true, stars = 0)
        answerCanRestrict(true)
        client.on(TdApi.SetNewChatPrivacySettings::class, TdResult.Success(TdApi.Ok()))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun answer(
        allows: Boolean,
        stars: Long,
    ) = client.on(TdApi.GetNewChatPrivacySettings::class, TdResult.Success(TdApi.NewChatPrivacySettings(allows, stars)))

    private fun answerCanRestrict(can: Boolean) =
        client.on(TdApi.GetOption::class, TdResult.Success(TdApi.OptionValueBoolean(can)))

    // Through the real TDLib repository on FakeTdlibClient, as the app wires it.
    private fun TestScope.viewModel() = NewChatPrivacyViewModel(TdlibPrivacyRepository(client)).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: NewChatPrivacyViewModel): List<NewChatPrivacyEffect> {
        val effects = mutableListOf<NewChatPrivacyEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private val NewChatPrivacyViewModel.content get() = uiState.value as NewChatPrivacyUiState.Content

    @Test
    fun `shows who may start a chat`() =
        runTest {
            assertEquals(NewChatPrivacy(allowsNonContacts = true, canRestrict = true), viewModel().content.privacy)
        }

    @Test
    fun `keeping non-contacts out shows at once and is saved`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(NewChatPrivacyEvent.Select(allows = false))
            assertTrue(viewModel.content.isSaving)
            runCurrent()

            assertEquals(false, viewModel.content.privacy.allowsNonContacts)
            assertEquals(false, viewModel.content.isSaving)
            assertEquals(
                false,
                client
                    .sentOf<TdApi.SetNewChatPrivacySettings>()
                    .single()
                    .settings.allowNewChatsFromUnknownUsers,
            )
        }

    @Test
    fun `without Telegram's leave non-contacts cannot be kept out`() =
        runTest {
            answerCanRestrict(false)
            val viewModel = viewModel()

            viewModel.onEvent(NewChatPrivacyEvent.Select(allows = false))
            runCurrent()

            assertEquals(true, viewModel.content.privacy.allowsNonContacts)
            assertTrue(client.sentOf<TdApi.SetNewChatPrivacySettings>().isEmpty())
        }

    @Test
    fun `a refusal brings the choice back and says why`() =
        runTest {
            client.on(
                TdApi.SetNewChatPrivacySettings::class,
                TdResult.Failure(TdError(403, "PREMIUM_ACCOUNT_REQUIRED")),
            )
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(NewChatPrivacyEvent.Select(allows = false))
            runCurrent()

            assertEquals(true, viewModel.content.privacy.allowsNonContacts)
            assertEquals(listOf(NewChatPrivacyEffect.ShowError(DomainError.PremiumRequired)), effects)
        }

    @Test
    fun `a failed load shows its error and retry loads again`() =
        runTest {
            client.on(TdApi.GetNewChatPrivacySettings::class, TdResult.Failure(TdError(500, "Network error")))
            val viewModel = viewModel()
            assertTrue(viewModel.uiState.value is NewChatPrivacyUiState.Error)

            answer(allows = false, stars = 0)
            viewModel.onEvent(NewChatPrivacyEvent.Retry)
            runCurrent()

            assertEquals(false, viewModel.content.privacy.allowsNonContacts)
        }
}
