package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.R
import dev.kyralabs.pingogram.core.common.result.DomainError
import dev.kyralabs.pingogram.core.domain.model.LinkSubject
import dev.kyralabs.pingogram.core.ui.component.LoadingIndicatorDelay
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import dev.kyralabs.pingogram.link.LinkDispatcher
import dev.kyralabs.pingogram.link.LinkEffect
import dev.kyralabs.pingogram.link.LinkNotice
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import dev.kyralabs.pingogram.core.ui.R as UiR

/** What the shell says about links ([LinkEffects]): why one does not open, and that one is being looked up. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppLinksTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val links = FakeLinkDispatcher()

    private fun setContent() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                val snackbars = remember { SnackbarHostState() }
                LinkEffects(links, rememberNavController(), snackbars, onOpenSearch = {})
                SnackbarHost(snackbars)
            }
        }
    }

    @Test
    fun `a web link Pingogram does not open offers the browser`() {
        setContent()

        links.send(LinkEffect.Notice(LinkNotice.Unsupported("https://t.me/addstickers/pack")))

        composeRule.onNodeWithText(string(R.string.link_not_supported)).assertExists()
        composeRule.onNodeWithText(string(R.string.link_open_in_browser)).assertExists()
    }

    @Test
    fun `an app link Pingogram does not open offers nothing`() {
        setContent()

        links.send(LinkEffect.Notice(LinkNotice.Unsupported(browserUrl = null)))

        composeRule.onNodeWithText(string(R.string.link_not_supported)).assertExists()
        composeRule.onNodeWithText(string(R.string.link_open_in_browser)).assertDoesNotExist()
    }

    @Test
    fun `says who could not be found`() {
        setContent()

        links.send(LinkEffect.Notice(LinkNotice.NotFound(LinkSubject.Username)))

        composeRule.onNodeWithText(string(R.string.link_not_found_username)).assertExists()
    }

    @Test
    fun `warns about login codes`() {
        setContent()

        links.send(LinkEffect.Notice(LinkNotice.LoginCode))

        composeRule.onNodeWithText(string(R.string.link_login_code)).assertExists()
    }

    @Test
    fun `says what failed`() {
        setContent()

        links.send(LinkEffect.Notice(LinkNotice.Failed(DomainError.Network)))

        composeRule.onNodeWithText(string(UiR.string.error_network)).assertExists()
    }

    @Test
    fun `a slow lookup says so after a moment, and can be cancelled`() {
        composeRule.mainClock.autoAdvance = false
        setContent()

        links.isResolving.value = true
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds / 2)
        composeRule.onNodeWithText(string(R.string.link_opening)).assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds)
        composeRule.onNodeWithText(string(R.string.link_opening)).assertExists()

        composeRule.onNodeWithText(string(R.string.link_opening_cancel)).performClick()
        composeRule.mainClock.advanceTimeBy(FRAME_MILLIS)

        assertEquals(1, links.cancelled)
    }

    @Test
    fun `the lookup's snackbar goes once the link resolved`() {
        composeRule.mainClock.autoAdvance = false
        setContent()
        links.isResolving.value = true
        composeRule.mainClock.advanceTimeBy(LoadingIndicatorDelay.inWholeMilliseconds * 2)
        composeRule.onNodeWithText(string(R.string.link_opening)).assertExists()

        links.isResolving.value = false
        composeRule.mainClock.advanceTimeBy(DISMISS_MILLIS)

        composeRule.onNodeWithText(string(R.string.link_opening)).assertDoesNotExist()
    }

    private fun string(
        @StringRes id: Int,
    ): String = RuntimeEnvironment.getApplication().getString(id)

    private class FakeLinkDispatcher : LinkDispatcher {
        private val channel = Channel<LinkEffect>(Channel.UNLIMITED)
        override val isResolving = MutableStateFlow(false)
        override val effects: Flow<LinkEffect> = channel.receiveAsFlow()
        var cancelled = 0

        fun send(effect: LinkEffect) {
            channel.trySend(effect)
        }

        override fun open(
            link: String,
            fromOutside: Boolean,
        ) = Unit

        override fun cancel() {
            cancelled++
            isResolving.value = false
        }
    }

    private companion object {
        const val FRAME_MILLIS = 16L
        const val DISMISS_MILLIS = 1_000L
    }
}
