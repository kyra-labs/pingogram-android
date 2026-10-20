package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * [LockableApp]: the app leaves the composition while locked, so nothing of it shows or runs, and comes back with its
 * saved state, the back stack included, when unlocked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class LockableAppTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var locked by mutableStateOf(false)

    private fun setApp(content: @Composable () -> Unit) {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                LockableApp(locked = locked, lockScreen = { Text(LOCK_SCREEN) }, content = content)
            }
        }
    }

    @Test
    fun `shows the lock screen instead of the app while locked`() {
        var disposals = 0
        setApp {
            Text(APP)
            DisposableEffect(Unit) { onDispose { disposals++ } }
        }
        composeRule.onNodeWithText(APP).assertExists()
        composeRule.onNodeWithText(LOCK_SCREEN).assertDoesNotExist()

        locked = true
        composeRule.waitForIdle()

        composeRule.onNodeWithText(LOCK_SCREEN).assertExists()
        composeRule.onNodeWithText(APP).assertDoesNotExist()
        assertEquals(1, disposals)
    }

    @Test
    fun `the app keeps its saved state while locked`() {
        setApp {
            var taps by rememberSaveable { mutableIntStateOf(0) }
            Button(onClick = { taps++ }) { Text("Taps: $taps") }
        }
        composeRule.onNodeWithText("Taps: 0").performClick()
        composeRule.onNodeWithText("Taps: 1").performClick()

        locked = true
        composeRule.waitForIdle()
        locked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Taps: 2").assertExists()
    }

    @Test
    fun `the back stack comes back after unlocking`() {
        setApp {
            val navController = rememberNavController()
            NavHost(navController, startDestination = "list") {
                composable("list") { Button(onClick = { navController.navigate("chat") }) { Text("List") } }
                composable("chat") { Text("Chat") }
            }
        }
        composeRule.onNodeWithText("List").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chat").assertExists()

        locked = true
        composeRule.waitForIdle()
        locked = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Chat").assertExists()
    }

    private companion object {
        const val APP = "The app"
        const val LOCK_SCREEN = "Locked"
    }
}
