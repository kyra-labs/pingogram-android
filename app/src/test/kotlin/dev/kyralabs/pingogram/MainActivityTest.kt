package dev.kyralabs.pingogram

import androidx.annotation.StringRes
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import dagger.hilt.android.testing.BindValue
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import dagger.hilt.android.testing.UninstallModules
import dev.kyralabs.pingogram.core.tdlib.AuthState
import dev.kyralabs.pingogram.core.tdlib.ConnectionState
import dev.kyralabs.pingogram.core.tdlib.TdlibClient
import dev.kyralabs.pingogram.core.tdlib.di.TdlibModule
import dev.kyralabs.pingogram.core.testing.FakeTdlibClient
import dev.kyralabs.pingogram.core.testing.TdApiFixtures
import kotlinx.coroutines.runBlocking
import org.drinkless.tdlib.TdApi
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.feature.auth.R as AuthR
import dev.kyralabs.pingogram.feature.chats.R as ChatsR

/**
 * [MainActivity] end to end on a [FakeTdlibClient]: TDLib's login steps, through the real repositories and
 * [MainActivityViewModel], decide between the login flow and the signed-in app (FR-A8).
 */
@HiltAndroidTest
@UninstallModules(TdlibModule::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp-xxhdpi")
class MainActivityTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val tdlib = FakeTdlibClient(authState = AuthState.WaitPhoneNumber, connectionState = ConnectionState.Ready)

    @BindValue
    @JvmField
    val tdlibClient: TdlibClient = tdlib

    @Test
    fun `logs in and out as TDLib reports it`() {
        awaitText(AuthR.string.auth_start_messaging)
        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)

        tdlib(TdApiFixtures.ready())
        awaitText(ChatsR.string.chats_placeholder_title)
        tab(R.string.nav_chats).assertIsSelected()

        // Logged out on another device, for example.
        tdlib(TdApiFixtures.loggingOut())
        awaitText(AuthR.string.auth_logging_out)
        composeRule.onAllNodes(isSelectable()).assertCountEquals(0)

        // TDLib closes, and a new instance starts over.
        tdlib(TdApiFixtures.closing())
        tdlib(TdApiFixtures.closed())
        tdlib(TdApiFixtures.waitTdlibParameters())
        tdlib(TdApiFixtures.waitPhoneNumber())
        awaitText(AuthR.string.auth_start_messaging)
    }

    /** Reports [state] like TDLib; returns once the repositories took it, on their background dispatcher. */
    private fun tdlib(state: TdApi.AuthorizationState) = runBlocking { tdlib.emitAuthorizationState(state) }

    private fun awaitText(
        @StringRes id: Int,
    ) {
        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodes(hasText(string(id))).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun tab(
        @StringRes label: Int,
    ): SemanticsNodeInteraction = composeRule.onNode(hasText(string(label)) and isSelectable())

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
