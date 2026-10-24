package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibPrivacyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySettingViewModelTest {
    private val client = FakeTdlibClient()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them. The user repository subscribes to
    // TDLib's updates before the test emits any, as it does at app start.
    private fun TestScope.viewModel(
        setting: PrivacySetting = PrivacySetting.LastSeen,
        users: TdlibUserRepository = TdlibUserRepository(client, backgroundScope),
    ): PrivacySettingViewModel =
        PrivacySettingViewModel(setting, TdlibPrivacyRepository(client), users).also { runCurrent() }

    private fun TestScope.effectsOf(viewModel: PrivacySettingViewModel): List<PrivacySettingEffect> {
        val effects = mutableListOf<PrivacySettingEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.select(
        viewModel: PrivacySettingViewModel,
        choice: PrivacyChoice,
    ) {
        viewModel.onEvent(PrivacySettingEvent.Select(choice))
        runCurrent()
    }

    private fun rules(vararg rules: TdApi.UserPrivacySettingRule) = TdApi.UserPrivacySettingRules(arrayOf(*rules))

    private fun answerRules(vararg rules: TdApi.UserPrivacySettingRule) =
        client.on(TdApi.GetUserPrivacySettingRules::class, TdResult.Success(rules(*rules)))

    private fun answerSet(result: TdResult<TdApi.Ok> = TdResult.Success(TdApi.Ok())) =
        client.on(TdApi.SetUserPrivacySettingRules::class, result)

    private suspend fun TestScope.announceMe(isPremium: Boolean) {
        client.emit(TdApiFixtures.updateUser(TdApiFixtures.user(id = 42, isPremium = isPremium)))
        client.emit(TdApiFixtures.myIdOption(42))
        runCurrent()
    }

    private val PrivacySettingViewModel.state get() = uiState.value

    private val contactsWithException =
        PrivacyRules(PrivacyChoice.Contacts, PrivacyExceptions(restrictedUserCount = 1))

    @Test
    fun `is loading until TDLib reports the rules`() =
        runTest {
            client.on(TdApi.GetUserPrivacySettingRules::class) {
                CompletableDeferred<TdResult<TdApi.UserPrivacySettingRules>>().await()
            }

            assertEquals(PrivacySettingUiState(PrivacySetting.LastSeen), viewModel().state)
        }

    @Test
    fun `shows the setting's rules`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(5)),
                TdApi.UserPrivacySettingRuleAllowContacts(),
            )

            assertEquals(
                PrivacySettingUiState(PrivacySetting.LastSeen, PrivacyRulesState.Loaded(contactsWithException)),
                viewModel().state,
            )
        }

    @Test
    fun `a failure shows why, and retry loads again`() =
        runTest {
            client.on(
                TdApi.GetUserPrivacySettingRules::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 9")),
            )
            val viewModel = viewModel()

            assertEquals(PrivacyRulesState.Failed(DomainError.FloodWait(9)), viewModel.state.rules)

            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            viewModel.onEvent(PrivacySettingEvent.Retry)
            runCurrent()

            assertEquals(PrivacyRulesState.Loaded(PrivacyRules(PrivacyChoice.Everybody)), viewModel.state.rules)
        }

    @Test
    fun `retry does nothing while the rules show`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            val viewModel = viewModel()

            viewModel.onEvent(PrivacySettingEvent.Retry)
            runCurrent()

            assertEquals(1, client.sentOf<TdApi.GetUserPrivacySettingRules>().size)
        }

    @Test
    fun `a selected choice shows at once while it is saved`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(5)),
                TdApi.UserPrivacySettingRuleAllowContacts(),
            )
            client.on(TdApi.SetUserPrivacySettingRules::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            select(viewModel, PrivacyChoice.Nobody)

            assertEquals(PrivacyChoice.Nobody, viewModel.state.savingChoice)
            assertEquals(PrivacyChoice.Nobody, viewModel.state.shownRules?.choice)
        }

    @Test
    fun `a saved choice keeps the exceptions`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(5)),
                TdApi.UserPrivacySettingRuleAllowContacts(),
            )
            answerSet()
            val viewModel = viewModel()

            select(viewModel, PrivacyChoice.Everybody)

            val sent =
                client
                    .sentOf<TdApi.SetUserPrivacySettingRules>()
                    .single()
                    .rules.rules
            assertEquals(
                listOf(TdApi.UserPrivacySettingRuleRestrictUsers::class, TdApi.UserPrivacySettingRuleAllowAll::class),
                sent.map { it::class },
            )
            assertEquals(
                PrivacyRulesState.Loaded(contactsWithException.copy(choice = PrivacyChoice.Everybody)),
                viewModel.state.rules,
            )
            assertNull(viewModel.state.savingChoice)
        }

    @Test
    fun `selecting the current choice saves nothing`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowContacts())
            answerSet()
            val viewModel = viewModel()

            select(viewModel, PrivacyChoice.Contacts)

            assertTrue(client.sentOf<TdApi.SetUserPrivacySettingRules>().isEmpty())
        }

    @Test
    fun `another choice while saving saves nothing more`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowContacts())
            client.on(TdApi.SetUserPrivacySettingRules::class) { CompletableDeferred<TdResult<TdApi.Ok>>().await() }
            val viewModel = viewModel()

            select(viewModel, PrivacyChoice.Nobody)
            select(viewModel, PrivacyChoice.Everybody)

            assertEquals(1, client.sentOf<TdApi.SetUserPrivacySettingRules>().size)
            assertEquals(PrivacyChoice.Nobody, viewModel.state.savingChoice)
        }

    @Test
    fun `a failed save shows the choice that was there and the error`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowContacts())
            answerSet(TdResult.Failure(TdError(500, "Network is unreachable")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            select(viewModel, PrivacyChoice.Nobody)

            assertEquals(listOf(PrivacySettingEffect.ShowError(DomainError.Network)), effects)
            assertNull(viewModel.state.savingChoice)
            assertEquals(PrivacyChoice.Contacts, viewModel.state.shownRules?.choice)
        }

    @Test
    fun `a silent failure shows no error`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowContacts())
            answerSet(TdResult.Failure(TdError(406, "Silent")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            select(viewModel, PrivacyChoice.Nobody)

            assertTrue(effects.isEmpty())
            assertNull(viewModel.state.savingChoice)
        }

    @Test
    fun `TDLib's changes of the setting apply, others do not`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            val viewModel = viewModel()

            client.emit(TdApi.UpdateUserPrivacySettingRules(TdApi.UserPrivacySettingShowStatus(), rules()))
            client.emit(
                TdApi.UpdateUserPrivacySettingRules(
                    TdApi.UserPrivacySettingAllowCalls(),
                    rules(TdApi.UserPrivacySettingRuleAllowContacts()),
                ),
            )
            runCurrent()

            assertEquals(PrivacyRulesState.Loaded(PrivacyRules(PrivacyChoice.Nobody)), viewModel.state.rules)
        }

    @Test
    fun `a change while loading is not lost to an older answer`() =
        runTest {
            val answer = CompletableDeferred<TdResult<TdApi.UserPrivacySettingRules>>()
            client.on(TdApi.GetUserPrivacySettingRules::class) { answer.await() }
            val viewModel = viewModel()

            client.emit(TdApi.UpdateUserPrivacySettingRules(TdApi.UserPrivacySettingShowStatus(), rules()))
            answer.complete(TdResult.Success(rules(TdApi.UserPrivacySettingRuleAllowAll())))
            runCurrent()

            assertEquals(PrivacyRulesState.Loaded(PrivacyRules(PrivacyChoice.Nobody)), viewModel.state.rules)
        }

    // Telegram Premium

    @Test
    fun `without premium, voice messages can only be allowed for everybody`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            answerSet()
            val users = TdlibUserRepository(client, backgroundScope)
            val viewModel = viewModel(PrivacySetting.VoiceMessages, users)

            announceMe(isPremium = false)
            select(viewModel, PrivacyChoice.Contacts)

            assertTrue(viewModel.state.premiumRequired)
            assertTrue(client.sentOf<TdApi.SetUserPrivacySettingRules>().isEmpty())
        }

    @Test
    fun `without premium, a restricted choice can still be opened up to everybody`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowContacts())
            answerSet()
            val users = TdlibUserRepository(client, backgroundScope)
            val viewModel = viewModel(PrivacySetting.VoiceMessages, users)

            announceMe(isPremium = false)
            select(viewModel, PrivacyChoice.Everybody)

            assertEquals(1, client.sentOf<TdApi.SetUserPrivacySettingRules>().size)
        }

    @Test
    fun `with premium, voice messages can be limited`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            answerSet()
            val users = TdlibUserRepository(client, backgroundScope)
            val viewModel = viewModel(PrivacySetting.VoiceMessages, users)

            announceMe(isPremium = true)
            select(viewModel, PrivacyChoice.Contacts)

            assertFalse(viewModel.state.premiumRequired)
            assertEquals(1, client.sentOf<TdApi.SetUserPrivacySettingRules>().size)
        }

    @Test
    fun `other settings never require premium`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            val users = TdlibUserRepository(client, backgroundScope)
            val viewModel = viewModel(PrivacySetting.Calls, users)

            announceMe(isPremium = false)

            assertFalse(viewModel.state.premiumRequired)
        }

    @Test
    fun `when Telegram still requires premium, the page says so`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            answerSet(TdResult.Failure(TdError(403, "PREMIUM_ACCOUNT_REQUIRED")))
            val viewModel = viewModel(PrivacySetting.VoiceMessages)
            val effects = effectsOf(viewModel)

            select(viewModel, PrivacyChoice.Nobody)

            assertEquals(listOf(PrivacySettingEffect.ShowError(DomainError.PremiumRequired)), effects)
            assertEquals(PrivacyChoice.Everybody, viewModel.state.shownRules?.choice)
        }

    // Read time

    @Test
    fun `last seen shows whether the read time shows, other settings do not`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            client.on(TdApi.GetReadDatePrivacySettings::class, TdResult.Success(TdApi.ReadDatePrivacySettings(true)))

            assertEquals(true, viewModel().uiState.value.showsReadTime)
            assertNull(viewModel(PrivacySetting.Calls).uiState.value.showsReadTime)
        }

    @Test
    fun `a switched read time shows at once and is saved`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            client.on(TdApi.GetReadDatePrivacySettings::class, TdResult.Success(TdApi.ReadDatePrivacySettings(true)))
            client.on(TdApi.SetReadDatePrivacySettings::class, TdResult.Success(TdApi.Ok()))
            val viewModel = viewModel()

            viewModel.onEvent(PrivacySettingEvent.ShowReadTime(false))
            assertEquals(false, viewModel.uiState.value.showsReadTime)
            runCurrent()

            assertEquals(false, viewModel.uiState.value.showsReadTime)
            assertFalse(
                client
                    .sentOf<TdApi.SetReadDatePrivacySettings>()
                    .single()
                    .settings.showReadDate,
            )
        }

    @Test
    fun `a refused read time switches back and says why`() =
        runTest {
            answerRules(TdApi.UserPrivacySettingRuleAllowAll())
            client.on(TdApi.GetReadDatePrivacySettings::class, TdResult.Success(TdApi.ReadDatePrivacySettings(true)))
            client.on(TdApi.SetReadDatePrivacySettings::class, TdResult.Failure(TdError(400, "SOMETHING_WRONG")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            viewModel.onEvent(PrivacySettingEvent.ShowReadTime(false))
            runCurrent()

            assertEquals(true, viewModel.uiState.value.showsReadTime)
            assertEquals(1, effects.size)
        }
}
