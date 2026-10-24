package dev.kyralabs.pingogram.feature.settings.ui

import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.data.repository.TdlibAccountRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibBlockListRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibPrivacyRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibSessionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibTwoStepVerificationRepository
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.model.PrivacyChoice
import dev.kyralabs.pingogram.core.domain.model.PrivacyExceptions
import dev.kyralabs.pingogram.core.domain.model.PrivacyRules
import dev.kyralabs.pingogram.core.domain.model.PrivacySetting
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakePasscodeRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.ui.text.UiText
import dev.kyralabs.pingogram.core.ui.text.bidiIsolated
import dev.kyralabs.pingogram.feature.settings.R
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.drinkless.tdlib.TdApi
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacySecurityViewModelTest {
    private val client = FakeTdlibClient()
    private val passcodes = FakePasscodeRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        answerRules { TdApi.UserPrivacySettingRules(arrayOf(TdApi.UserPrivacySettingRuleAllowAll())) }
        answerBlocked(totalCount = 3)
        answerSessions(count = 4)
        answerPassword(passwordState())
        client.on(TdApi.GetAccountTtl::class, TdResult.Success(TdApi.AccountTtl(365)))
        client.on(TdApi.GetDefaultMessageAutoDeleteTime::class, TdResult.Success(TdApi.MessageAutoDeleteTime(0)))
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Through the real TDLib repositories on FakeTdlibClient, as the app wires them.
    private fun TestScope.viewModel(): PrivacySecurityViewModel =
        PrivacySecurityViewModel(
            TdlibPrivacyRepository(client),
            TdlibBlockListRepository(client),
            TdlibSessionRepository(client),
            TdlibTwoStepVerificationRepository(client),
            passcodes,
            TdlibAccountRepository(client),
            testTimeFormats(),
        ).also { runCurrent() }

    private fun answerRules(rules: (TdApi.UserPrivacySetting) -> TdApi.UserPrivacySettingRules) =
        client.on(TdApi.GetUserPrivacySettingRules::class) { TdResult.Success(rules(it.setting)) }

    private fun answerBlocked(totalCount: Int) =
        client.on(
            TdApi.GetBlockedMessageSenders::class,
            TdResult.Success(TdApi.MessageSenders(totalCount, arrayOf(TdApi.MessageSenderUser(7)))),
        )

    private fun answerSessions(count: Int) =
        client.on(
            TdApi.GetActiveSessions::class,
            TdResult.Success(TdApi.Sessions(Array(count) { session(it.toLong(), isCurrent = it == 0) }, 180)),
        )

    private fun answerPassword(state: TdApi.PasswordState) =
        client.on(TdApi.GetPasswordState::class, TdResult.Success(state))

    private fun passwordState(
        hasPassword: Boolean = true,
        hasRecoveryEmail: Boolean = true,
        pendingEmail: String? = null,
        pendingResetDate: Int = 0,
        loginEmail: String = "",
    ) = TdApi.PasswordState(
        hasPassword,
        "",
        hasRecoveryEmail,
        false,
        pendingEmail?.let { TdApi.EmailAddressAuthenticationCodeInfo(it, 6) },
        loginEmail,
        pendingResetDate,
    )

    private fun rules(vararg rules: TdApi.UserPrivacySettingRule) = TdApi.UserPrivacySettingRules(arrayOf(*rules))

    private val PrivacySecurityViewModel.state get() = uiState.value

    private val PrivacySecurityViewModel.content get() = state as PrivacySecurityUiState.Content

    private val everybody = PrivacyRules(PrivacyChoice.Everybody)

    @Test
    fun `shows how long the account may stay unused`() =
        runTest {
            assertEquals(365, viewModel().content.accountTtlDays)
        }

    @Test
    fun `shows the login email where there is one and the timer of new chats`() =
        runTest {
            assertEquals(null, viewModel().content.loginEmailPattern)

            answerPassword(passwordState(loginEmail = "a****@e******.org"))
            client.on(
                TdApi.GetDefaultMessageAutoDeleteTime::class,
                TdResult.Success(TdApi.MessageAutoDeleteTime(604_800)),
            )
            val content = viewModel().content

            assertEquals("a****@e******.org", content.loginEmailPattern)
            assertEquals(604_800, content.defaultAutoDeleteSeconds)
        }

    @Test
    fun `is loading until everything is loaded`() =
        runTest {
            client.on(TdApi.GetPasswordState::class) { CompletableDeferred<TdResult<TdApi.PasswordState>>().await() }

            assertEquals(PrivacySecurityUiState.Loading, viewModel().state)
        }

    @Test
    fun `shows every setting's choice, the counts and two-step verification`() =
        runTest {
            answerRules { setting ->
                when (setting) {
                    is TdApi.UserPrivacySettingShowPhoneNumber -> {
                        rules(TdApi.UserPrivacySettingRuleAllowContacts())
                    }

                    is TdApi.UserPrivacySettingShowStatus -> {
                        rules(
                            TdApi.UserPrivacySettingRuleRestrictUsers(longArrayOf(1, 2)),
                            TdApi.UserPrivacySettingRuleAllowAll(),
                        )
                    }

                    is TdApi.UserPrivacySettingAllowCalls -> {
                        rules()
                    }

                    else -> {
                        rules(TdApi.UserPrivacySettingRuleAllowAll())
                    }
                }
            }

            val content = viewModel().content

            assertEquals(
                mapOf(
                    PrivacySetting.PhoneNumber to PrivacyRules(PrivacyChoice.Contacts),
                    PrivacySetting.LastSeen to
                        PrivacyRules(PrivacyChoice.Everybody, PrivacyExceptions(restrictedUserCount = 2)),
                    PrivacySetting.ProfilePhoto to everybody,
                    PrivacySetting.ForwardedMessages to everybody,
                    PrivacySetting.Calls to PrivacyRules(PrivacyChoice.Nobody),
                    PrivacySetting.ChatInvites to everybody,
                    PrivacySetting.VoiceMessages to everybody,
                ),
                content.rules,
            )
            assertEquals(3, content.blockedCount)
            assertEquals(4, content.sessionCount)
            assertEquals(
                TwoStepVerificationSummary(UiText.resource(R.string.settings_two_step_verification_on_recovery_email)),
                content.twoStepVerification,
            )
        }

    @Test
    fun `asks for one blocked sender only, for the total count`() =
        runTest {
            viewModel()

            val request = client.sentOf<TdApi.GetBlockedMessageSenders>().single()
            assertInstanceOf(TdApi.BlockListMain::class.java, request.blockList)
            assertEquals(0, request.offset)
            assertEquals(1, request.limit)
        }

    @Test
    fun `shows whether the passcode lock is on, as it changes`() =
        runTest {
            val viewModel = viewModel()
            assertEquals(false, viewModel.content.passcodeLock)

            passcodes.setPasscode("1234", PasscodeType.Pin)
            runCurrent()

            assertEquals(true, viewModel.content.passcodeLock)
        }

    @Test
    fun `two-step verification off`() =
        runTest {
            answerPassword(passwordState(hasPassword = false, hasRecoveryEmail = false))

            assertEquals(
                TwoStepVerificationSummary(UiText.resource(R.string.settings_two_step_verification_off)),
                viewModel().content.twoStepVerification,
            )
        }

    @Test
    fun `two-step verification without a recovery email`() =
        runTest {
            answerPassword(passwordState(hasRecoveryEmail = false))

            assertEquals(
                TwoStepVerificationSummary(
                    UiText.resource(R.string.settings_two_step_verification_on_no_recovery_email),
                ),
                viewModel().content.twoStepVerification,
            )
        }

    @Test
    fun `two-step verification with a recovery email waiting for confirmation and a requested reset`() =
        runTest {
            val resetDate = Instant.parse("2026-10-25T09:41:00Z").epochSecond.toInt()
            answerPassword(
                passwordState(hasRecoveryEmail = false, pendingEmail = "a***@g***.com", pendingResetDate = resetDate),
            )

            val summary = viewModel().content.twoStepVerification

            assertEquals(
                UiText.resource(R.string.settings_two_step_verification_pending_email, "a***@g***.com".bidiIsolated()),
                summary.status,
            )
            assertEquals("Oct 25, 2026, 9:41 AM", summary.pendingResetDate?.let(::plainSpaces))
        }

    @Test
    fun `a failure shows why, and retry loads everything again`() =
        runTest {
            client.on(
                TdApi.GetActiveSessions::class,
                TdResult.Failure(TdError(429, "Too Many Requests: retry after 5")),
            )
            val viewModel = viewModel()

            assertEquals(PrivacySecurityUiState.Error(DomainError.FloodWait(5)), viewModel.state)

            answerSessions(count = 2)
            viewModel.onEvent(PrivacySecurityEvent.Retry)
            runCurrent()

            assertEquals(2, viewModel.content.sessionCount)
            assertEquals(2, client.sentOf<TdApi.GetPasswordState>().size)
        }

    @Test
    fun `retry does nothing while the page shows`() =
        runTest {
            val viewModel = viewModel()

            viewModel.onEvent(PrivacySecurityEvent.Retry)
            runCurrent()

            assertEquals(1, client.sentOf<TdApi.GetPasswordState>().size)
        }

    @Test
    fun `no answer in time is a network error`() =
        runTest {
            client.on(TdApi.GetBlockedMessageSenders::class) {
                CompletableDeferred<TdResult<TdApi.MessageSenders>>().await()
            }
            val viewModel = viewModel()

            advanceTimeBy(REQUEST_TIMEOUT)
            runCurrent()

            assertEquals(PrivacySecurityUiState.Error(DomainError.Network), viewModel.state)
        }

    @Test
    fun `a change of a privacy setting shows at once`() =
        runTest {
            val viewModel = viewModel()

            client.emit(
                TdApi.UpdateUserPrivacySettingRules(
                    TdApi.UserPrivacySettingShowProfilePhoto(),
                    rules(TdApi.UserPrivacySettingRuleAllowContacts()),
                ),
            )
            runCurrent()

            assertEquals(PrivacyRules(PrivacyChoice.Contacts), viewModel.content.rules[PrivacySetting.ProfilePhoto])
        }

    @Test
    fun `a change while loading is not lost to an older answer`() =
        runTest {
            val password = CompletableDeferred<TdResult<TdApi.PasswordState>>()
            client.on(TdApi.GetPasswordState::class) { password.await() }
            val viewModel = viewModel()

            client.emit(TdApi.UpdateUserPrivacySettingRules(TdApi.UserPrivacySettingAllowCalls(), rules()))
            password.complete(TdResult.Success(passwordState()))
            runCurrent()

            assertEquals(PrivacyRules(PrivacyChoice.Nobody), viewModel.content.rules[PrivacySetting.Calls])
        }

    @Test
    fun `showing again loads the counts again, keeping what shows meanwhile`() =
        runTest {
            val viewModel = viewModel()
            val blocked = CompletableDeferred<TdResult<TdApi.MessageSenders>>()
            client.on(TdApi.GetBlockedMessageSenders::class) { blocked.await() }

            viewModel.onEvent(PrivacySecurityEvent.Refresh)
            runCurrent()

            assertEquals(3, viewModel.content.blockedCount)

            blocked.complete(TdResult.Success(TdApi.MessageSenders(1, arrayOf(TdApi.MessageSenderUser(7)))))
            runCurrent()

            assertEquals(1, viewModel.content.blockedCount)
        }

    @Test
    fun `what shows stays when loading it again fails`() =
        runTest {
            val viewModel = viewModel()
            client.on(TdApi.GetActiveSessions::class, TdResult.Failure(TdError(500, "Network is unreachable")))

            viewModel.onEvent(PrivacySecurityEvent.Refresh)
            runCurrent()

            assertEquals(4, viewModel.content.sessionCount)
        }

    @Test
    fun `showing again while loading loads once`() =
        runTest {
            client.on(TdApi.GetPasswordState::class) { CompletableDeferred<TdResult<TdApi.PasswordState>>().await() }
            val viewModel = viewModel()

            viewModel.onEvent(PrivacySecurityEvent.Refresh)
            runCurrent()

            assertEquals(1, client.sentOf<TdApi.GetPasswordState>().size)
        }

    private companion object {
        /** `REQUEST_TIMEOUT` of `core:data`. */
        val REQUEST_TIMEOUT = 30.seconds

        fun session(
            id: Long,
            isCurrent: Boolean,
        ) = TdApi.Session(
            id,
            isCurrent,
            false,
            false,
            true,
            true,
            TdApi.SessionDeviceTypeAndroid(),
            1,
            "App",
            "1.0",
            false,
            "Phone",
            "Android",
            "15",
            0,
            0,
            "",
            "",
        )
    }
}
