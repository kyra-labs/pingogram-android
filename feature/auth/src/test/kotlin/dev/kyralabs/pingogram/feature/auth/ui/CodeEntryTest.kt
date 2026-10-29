package dev.kyralabs.pingogram.feature.auth.ui

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.kyralabs.pingogram.core.data.repository.TdlibAuthRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibConnectionRepository
import dev.kyralabs.pingogram.core.data.repository.TdlibCountryRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.testing.FakeDeviceAccountsRepository
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.feature.auth.R
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.UK_INTERNATIONAL
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.answerCountryRequests
import dev.kyralabs.pingogram.feature.auth.ui.AuthTestFixtures.ok
import kotlinx.coroutines.Dispatchers
import org.drinkless.tdlib.TdApi
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import dev.kyralabs.pingogram.core.ui.R as CoreUiR

/**
 * Code entry (FR-A3) through the screen and the real [AuthViewModel] on [FakeTdlibClient] (`docs/TESTING.md`):
 * typed or pasted, the code is sent once it is complete; a new code can be requested once the countdown, which
 * follows the device clock, reaches zero.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = COMPACT)
class CodeEntryTest {
    @get:Rule(order = 0)
    val componentActivity = ComponentActivityRegistration()

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val client =
        FakeTdlibClient(
            authState =
                AuthState.from(
                    TdApiFixtures.waitCode(
                        TdApiFixtures.authenticationCodeInfo(
                            phoneNumber = UK_INTERNATIONAL,
                            type = TdApi.AuthenticationCodeTypeSms(CODE_LENGTH),
                            nextType = TdApi.AuthenticationCodeTypeCall(CODE_LENGTH),
                            timeoutSeconds = RESEND_TIMEOUT_SECONDS,
                        ),
                    ),
                )!!,
            connectionState = ConnectionState.Ready,
        ).apply {
            answerCountryRequests()
            on(TdApi.CheckAuthenticationCode::class, TdResult.Failure(TdError(400, "PHONE_CODE_INVALID")))
            on(TdApi.ResendAuthenticationCode::class, ok())
        }

    @Test
    fun `a typed code is sent once, when its last digit is typed`() {
        showCodeScreen()

        "1234".forEach { codeField().performTextInput(it.toString()) }
        val beforeComplete = sentCodes()
        codeField().performTextInput("5")
        codeField().performTextInput("6")

        assertEquals(emptyList<String>(), beforeComplete)
        assertEquals(listOf("12345"), sentCodes())
        codeField().assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("12345")))
    }

    @Test
    fun `a pasted message with the code sends the code once`() {
        showCodeScreen()

        codeField().performTextReplacement("Login code: 54321. Do not give this code to anyone.")
        codeField().performTextReplacement("Login code: 54321. Do not give this code to anyone.")

        assertEquals(listOf("54321"), sentCodes())
    }

    @Test
    fun `a wrong code is reported as the field's error`() {
        showCodeScreen()

        codeField().performTextReplacement("12345")

        codeField().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, string(CoreUiR.string.error_code_invalid)),
        )
    }

    @Test
    fun `a new code can be requested only once the countdown reaches zero`() {
        showCodeScreen()
        val resend = string(CoreUiR.string.login_code_resend_call)

        advanceClockBySeconds(RESEND_TIMEOUT_SECONDS - 1L)
        val resendBeforeZero = composeRule.onAllNodesWithText(resend).fetchSemanticsNodes().size
        advanceClockBySeconds(1)
        composeRule
            .onNodeWithText(resend)
            .assertIsEnabled()
            .assertHasClickAction()
            .performClick()

        assertEquals(0, resendBeforeZero)
        assertEquals(1, client.sentOf<TdApi.ResendAuthenticationCode>().size)
    }

    private fun showCodeScreen() {
        val viewModel =
            AuthViewModel(
                savedStateHandle = SavedStateHandle(),
                authRepository = TdlibAuthRepository(client, Dispatchers.Main.immediate),
                countryRepository = TdlibCountryRepository(client),
                connectionRepository = TdlibConnectionRepository(client),
                deviceAccounts = FakeDeviceAccountsRepository(),
                timeSource = ElapsedRealtimeTimeSource,
            )
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AuthScreen(state = state, onEvent = viewModel::onEvent)
            }
        }
        composeRule.waitForIdle()
    }

    private fun codeField(): SemanticsNodeInteraction = composeRule.onNode(hasSetTextAction())

    private fun sentCodes(): List<String> = client.sentOf<TdApi.CheckAuthenticationCode>().map { it.code }

    /** Runs the main looper's clock forward, which the ViewModel's countdown follows (`SystemClock`). */
    private fun advanceClockBySeconds(seconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(seconds))
        composeRule.waitForIdle()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val CODE_LENGTH = 5
        const val RESEND_TIMEOUT_SECONDS = 3
    }
}
