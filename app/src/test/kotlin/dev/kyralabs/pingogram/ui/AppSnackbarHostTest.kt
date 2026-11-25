package dev.kyralabs.pingogram.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.navigation.compose.rememberNavController
import dev.kyralabs.pingogram.MainActivityUiState
import dev.kyralabs.pingogram.core.ui.theme.PingogramTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's snackbar host while no snackbar shows (`docs/PERFORMANCE.md`, "Overdraw"): it takes no room, where it
 * was a column as tall as the window's insets down the middle of every page.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
class AppSnackbarHostTest {
    @get:Rule(order = 0)
    val activityRegistration = ActivityRegistration(ComponentActivity::class.java)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `without a snackbar, the host takes no room`() {
        composeRule.setContent {
            PingogramTheme(dynamicColor = false) {
                Box(Modifier.fillMaxSize()) {
                    LinkHandling(
                        links = null,
                        uiState = MainActivityUiState.SignedIn,
                        navController = rememberNavController(),
                        onOpenSearch = {},
                    )
                }
            }
        }

        val host = composeRule.onNodeWithTag(APP_SNACKBAR_HOST_TEST_TAG, useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(IntSize.Zero, host.size)
    }
}
