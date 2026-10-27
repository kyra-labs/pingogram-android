package dev.kyralabs.pingogram.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.kyralabs.pingogram.core.ui.navigation.OverlayHost
import dev.kyralabs.pingogram.core.ui.navigation.overlay
import dev.kyralabs.pingogram.core.ui.navigation.rememberOverlayNavigator
import dev.kyralabs.pingogram.ui.ActivityRegistration
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import dev.kyralabs.pingogram.core.navigation.ChatsRoute as Screen
import dev.kyralabs.pingogram.core.navigation.MediaViewerRoute as Picture

/**
 * `core:ui`'s overlay destinations (the media viewer's, `OverlayNavigator`): the screen below an overlay stays
 * composed and started while it shows, so coming back composes nothing anew, and is hidden from screen readers
 * meanwhile; the overlay keeps its route's arguments and leaves the back stack with back.
 */
@RunWith(RobolectricTestRunner::class)
class OverlayNavigatorTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController

    /** How often the screen below was composed from nothing. */
    private var screensCreated = 0

    private fun setContent() {
        composeRule.setContent {
            val controller = rememberNavController().also { navController = it }
            val overlays = rememberOverlayNavigator(controller)
            OverlayHost(overlays) {
                NavHost(controller, startDestination = Screen) {
                    composable<Screen> {
                        remember { screensCreated++ }
                        Text(SCREEN)
                    }
                    overlay<Picture> { entry ->
                        Box(Modifier.fillMaxSize()) { Text("$PICTURE ${entry.toRoute<Picture>().messageId}") }
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun `the screen below an overlay stays composed and started, and is not composed again on the way back`() {
        setContent()

        composeRule.runOnUiThread { navController.navigate(Picture(chatId = 1, messageId = 7)) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("$PICTURE 7").assertIsDisplayed()
        val below = navController.getBackStackEntry<Screen>()
        assertEquals(Lifecycle.State.STARTED, below.lifecycle.currentState)
        // Below the overlay, as below a dialog: nothing of it reaches screen readers.
        composeRule.onNodeWithText(SCREEN).assertDoesNotExist()

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(SCREEN).assertIsDisplayed()
        composeRule.onNodeWithText("$PICTURE 7").assertDoesNotExist()
        assertEquals(Lifecycle.State.RESUMED, below.lifecycle.currentState)
        assertEquals(1, screensCreated)
    }

    @Test
    fun `an overlay is resumed once it came in, and destroyed once it went out`() {
        setContent()

        composeRule.runOnUiThread { navController.navigate(Picture(chatId = 1, messageId = 3)) }
        composeRule.waitForIdle()
        val entry = navController.currentBackStackEntry!!
        assertEquals(Lifecycle.State.RESUMED, entry.lifecycle.currentState)

        composeRule.runOnUiThread { navController.popBackStack() }
        composeRule.waitForIdle()

        assertEquals(Lifecycle.State.DESTROYED, entry.lifecycle.currentState)
    }

    private companion object {
        const val SCREEN = "Conversation"
        const val PICTURE = "Picture"
    }
}
