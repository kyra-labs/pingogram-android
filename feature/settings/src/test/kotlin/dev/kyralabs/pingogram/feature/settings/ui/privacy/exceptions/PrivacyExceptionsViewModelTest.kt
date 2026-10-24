package dev.kyralabs.pingogram.feature.settings.ui.privacy.exceptions

import androidx.lifecycle.SavedStateHandle
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.OpenChats
import dev.kyralabs.pingogram.core.data.repository.TdlibChatRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPrivacyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibUserRepository
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptionSide
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.text.UiText
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
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyExceptionsViewModelTest {
    private val client = FakeTdlibClient()
    private val savedState = SavedStateHandle()

    private val ada = TdApiFixtures.user(id = 5, firstName = "Ada")
    private val grace = TdApiFixtures.user(id = 6, firstName = "Grace")
    private val team = TdApiFixtures.chat(id = -100, type = TdApi.ChatTypeBasicGroup(1), title = "Team")
    private val news = TdApiFixtures.chat(id = -200, type = TdApi.ChatTypeSupergroup(2, true), title = "News")
    private val adaChat = TdApiFixtures.chat(id = 5, type = TdApi.ChatTypePrivate(5), title = "Ada")
    private val graceChat = TdApiFixtures.chat(id = 6, type = TdApi.ChatTypePrivate(6), title = "Grace")
    private val saved = TdApiFixtures.chat(id = 42, type = TdApi.ChatTypePrivate(42), title = "Saved Messages")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answerRules(TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(5)), TdApi.UserPrivacySettingRuleAllowAll())
        client.on(TdApi.SetUserPrivacySettingRules::class, TdResult.Success(TdApi.Ok()))
        client.on(TdApi.GetChat::class) { request ->
            listOf(team, news, adaChat, graceChat)
                .firstOrNull { it.id == request.chatId }
                ?.let { TdResult.Success(it) }
                ?: TdResult.Failure(TdError(400, "Chat not found"))
        }
        client.on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun answerRules(vararg rules: TdApi.UserPrivacySettingRule) =
        client.on(
            TdApi.GetUserPrivacySettingRules::class,
            TdResult.Success(TdApi.UserPrivacySettingRules(arrayOf(*rules))),
        )

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them, TDLib having announced the users
    // and chats.
    private suspend fun TestScope.viewModel(
        side: PrivacyExceptionSide = PrivacyExceptionSide.Never,
        handle: SavedStateHandle = savedState,
    ): PrivacyExceptionsViewModel {
        val users = TdlibUserRepository(client, backgroundScope)
        val chats = TdlibChatRepository(client, backgroundScope, StandardTestDispatcher(testScheduler), OpenChats())
        runCurrent()
        client.emit(TdApiFixtures.myIdOption(42))
        listOf(ada, grace).forEach { client.emit(TdApiFixtures.updateUser(it)) }
        listOf(adaChat, graceChat, team, news, saved).forEach { client.emit(TdApiFixtures.updateNewChat(it)) }
        runCurrent()
        return PrivacyExceptionsViewModel(
            setting = PrivacySetting.LastSeen,
            side = side,
            savedStateHandle = handle,
            privacyRepository = TdlibPrivacyRepository(client),
            chatRepository = chats,
            userRepository = users,
        ).also {
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.pickerState.collect {} }
            runCurrent()
        }
    }

    private fun TestScope.effectsOf(viewModel: PrivacyExceptionsViewModel): List<PrivacyExceptionsEffect> {
        val effects = mutableListOf<PrivacyExceptionsEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.effects.toList(effects) }
        return effects
    }

    private fun TestScope.send(
        viewModel: PrivacyExceptionsViewModel,
        vararg events: PrivacyExceptionsEvent,
    ) {
        events.forEach {
            viewModel.onEvent(it)
            runCurrent()
        }
    }

    private val PrivacyExceptionsViewModel.content get() = uiState.value as PrivacyExceptionsUiState.Content

    private fun sentRules() =
        client
            .sentOf<TdApi.SetUserPrivacySettingRules>()
            .last()
            .rules.rules

    @Test
    fun `shows the side's users and groups with their names`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(5)),
                TdApi.UserPrivacySettingRuleRestrictChatMembers(longArrayOf(-100)),
                TdApi.UserPrivacySettingRuleAllowUsers(longArrayOf(6)),
                TdApi.UserPrivacySettingRuleAllowAll(),
            )

            val entries = viewModel().content.entries

            assertEquals(listOf(ExceptionKey(5, false), ExceptionKey(-100, true)), entries.map { it.key })
            assertEquals(listOf(UiText.Plain("Ada"), UiText.Plain("Team")), entries.map { it.title })
        }

    @Test
    fun `removing takes it out at once and saves it`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, PrivacyExceptionsEvent.Remove(ExceptionKey(5, false)))

            assertTrue(viewModel.content.entries.isEmpty())
            assertArrayEquals(
                arrayOf<Any>(TdApi.UserPrivacySettingRuleAllowAll::class.java),
                sentRules().map { it.javaClass }.toTypedArray(),
            )
        }

    @Test
    fun `a refused change brings the exceptions back and says why`() =
        runTest {
            client.on(TdApi.SetUserPrivacySettingRules::class, TdResult.Failure(TdError(400, "SOMETHING_WRONG")))
            val viewModel = viewModel()
            val effects = effectsOf(viewModel)

            send(viewModel, PrivacyExceptionsEvent.Remove(ExceptionKey(5, false)))

            assertEquals(listOf(ExceptionKey(5, false)), viewModel.content.entries.map { it.key })
            assertEquals(1, effects.size)
        }

    @Test
    fun `the picker offers people and groups of the chat list, not Saved Messages or channels`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, PrivacyExceptionsEvent.OpenPicker)

            val candidates = viewModel.pickerState.value!!.candidates
            assertEquals(
                setOf(ExceptionKey(5, false), ExceptionKey(6, false), ExceptionKey(-100, true)),
                candidates.map { it.entry.key }.toSet(),
            )
            assertEquals(listOf(ExceptionKey(5, false)), candidates.filter { it.selected }.map { it.entry.key })
        }

    @Test
    fun `the picker filters by its query`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, PrivacyExceptionsEvent.OpenPicker, PrivacyExceptionsEvent.PickerQueryChanged("te"))

            assertEquals(
                listOf(ExceptionKey(-100, true)),
                viewModel.pickerState.value!!
                    .candidates
                    .map { it.entry.key },
            )
        }

    @Test
    fun `closing the picker saves what was picked, taking it off the other side`() =
        runTest {
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(5)),
                TdApi.UserPrivacySettingRuleAllowUsers(longArrayOf(6)),
                TdApi.UserPrivacySettingRuleAllowContacts(),
            )
            val viewModel = viewModel()

            send(
                viewModel,
                PrivacyExceptionsEvent.OpenPicker,
                PrivacyExceptionsEvent.TogglePicked(ExceptionKey(6, false)),
                PrivacyExceptionsEvent.TogglePicked(ExceptionKey(-100, true)),
                PrivacyExceptionsEvent.ClosePicker,
            )

            assertNull(viewModel.pickerState.value)
            val rules = sentRules()
            assertArrayEquals(longArrayOf(5, 6), (rules[0] as TdApi.UserPrivacySettingRuleRestrictUsers).userIds)
            assertArrayEquals(longArrayOf(-100), (rules[1] as TdApi.UserPrivacySettingRuleRestrictChatMembers).chatIds)
            assertEquals(TdApi.UserPrivacySettingRuleAllowContacts::class.java, rules.last().javaClass)
            assertEquals(3, rules.size)
        }

    @Test
    fun `closing the picker without changes saves nothing`() =
        runTest {
            val viewModel = viewModel()

            send(viewModel, PrivacyExceptionsEvent.OpenPicker, PrivacyExceptionsEvent.ClosePicker)

            assertTrue(client.sentOf<TdApi.SetUserPrivacySettingRules>().isEmpty())
        }

    @Test
    fun `an open picker and its picks survive process death`() =
        runTest {
            val first = viewModel()
            send(
                first,
                PrivacyExceptionsEvent.OpenPicker,
                PrivacyExceptionsEvent.PickerQueryChanged("a"),
                PrivacyExceptionsEvent.TogglePicked(ExceptionKey(6, false)),
            )

            val restored = SavedStateHandle(savedState.keys().associateWith { savedState.get<Any?>(it) })
            val picker = viewModel(handle = restored).pickerState.value!!

            assertEquals("a", picker.query)
            assertEquals(
                setOf(ExceptionKey(5, false), ExceptionKey(6, false)),
                picker.candidates
                    .filter { it.selected }
                    .map { it.entry.key }
                    .toSet(),
            )
        }

    @Test
    fun `a failed load shows its error, and retry loads again`() =
        runTest {
            client.on(TdApi.GetUserPrivacySettingRules::class, TdResult.Failure(TdError(500, "Network error")))
            val viewModel = viewModel()
            assertEquals(PrivacyExceptionsUiState.Error(DomainError.Network), viewModel.uiState.value)

            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(6)),
                TdApi.UserPrivacySettingRuleAllowAll(),
            )
            send(viewModel, PrivacyExceptionsEvent.Retry)

            assertEquals(listOf(ExceptionKey(6, false)), viewModel.content.entries.map { it.key })
        }

    @Test
    fun `a change of the setting elsewhere reads the exceptions again`() =
        runTest {
            val viewModel = viewModel()
            answerRules(
                TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(6)),
                TdApi.UserPrivacySettingRuleAllowAll(),
            )

            client.emit(
                TdApi.UpdateUserPrivacySettingRules(
                    TdApi.UserPrivacySettingShowStatus(),
                    TdApi.UserPrivacySettingRules(arrayOf(TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(6)))),
                ),
            )
            runCurrent()

            assertEquals(listOf(ExceptionKey(6, false)), viewModel.content.entries.map { it.key })
        }
}
