package dev.kyralabs.pingogram

import androidx.annotation.StringRes
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.domain.model.AutoLock
import dev.kyralabs.pingogram.core.domain.model.PasscodeType
import dev.kyralabs.pingogram.core.domain.repository.PasscodeRepository
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdError
import dev.kyralabs.pingogram.core.tdlib.TdResult
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.inject.Inject
import dev.kyralabs.pingogram.feature.chats.R as ChatsR
import dev.kyralabs.pingogram.feature.settings.R as SettingsR

/**
 * The passcode lock end to end in [MainActivity], on the real settings file and lock: with an immediate auto-lock,
 * leaving the app locks it, the lock screen replaces the chats, and the right PIN on the keypad brings them back.
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class PasscodeLockTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient =
        FakeTdlibClient(authState = AuthState.Ready, connectionState = ConnectionState.Ready).apply {
            // TDLib's answer once a chat list is loaded completely: the chat list is empty.
            on(TdApi.LoadChats::class, TdResult.Failure(TdError(404, "Not Found")))
        }

    @Inject
    lateinit var passcodes: PasscodeRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun `leaving the app locks it, and the right PIN unlocks it`() {
        awaitText(ChatsR.string.chat_list_empty_title)
        runBlocking {
            passcodes.setPasscode("2580", PasscodeType.Pin)
            passcodes.setAutoLock(AutoLock.Immediately)
        }
        // Setting the passcode leaves the app open.
        composeRule.onNodeWithText(string(ChatsR.string.chat_list_empty_title)).assertExists()

        composeRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        composeRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        awaitText(SettingsR.string.lock_title)
        composeRule.onNodeWithText(string(ChatsR.string.chat_list_empty_title)).assertDoesNotExist()

        "2580".forEach { composeRule.onNode(hasText(it.toString())).performClick() }
        composeRule.onNodeWithContentDescription(string(SettingsR.string.lock_unlock)).performClick()

        awaitText(ChatsR.string.chat_list_empty_title)
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule
                .onAllNodes(
                    hasContentDescription(string(SettingsR.string.lock_unlock)),
                ).fetchSemanticsNodes()
                .isEmpty()
        }
    }

    private fun awaitText(
        @StringRes id: Int,
    ) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(string(id))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
    }
}
